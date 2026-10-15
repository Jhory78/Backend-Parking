package com.parking;

import com.jayway.jsonpath.JsonPath;
import com.parking.application.port.in.RegisterEntryCommand;
import com.parking.application.port.in.RegisterEntryUseCase;
import com.parking.application.port.out.EntryBarrierPort;
import com.parking.application.port.out.PlateRecognitionPort;
import com.parking.application.usecase.SpaceService;
import com.parking.domain.entity.PlateReading;
import com.parking.domain.entity.VehicleEntry;
import com.parking.domain.exception.NoSpaceAvailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for Sprint 3 user stories (RF-01 to RF-11): vehicle entry by
 * plate with automatic space assignment, plus the vehicle exit.
 * <p>
 * The OCR engine is mocked: no test calls the real service. Spaces come from the
 * V3 migration: C-01..C-20 for cars and M-01..M-10 for motorcycles.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SprintThreeAcceptanceTest {

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final String ISO_UTC_SECONDS = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z";

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    private static final byte[] GIF = "GIF89a....".getBytes(StandardCharsets.US_ASCII);

    @MockitoBean
    private PlateRecognitionPort plateRecognition;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private EntryBarrierPort entryBarrier;

    @Autowired
    private RegisterEntryUseCase registerEntryUseCase;

    private String adminBearer;

    @BeforeEach
    void resetParkingLot() throws Exception {
        jdbc.update("DELETE FROM vehicle_entries");
        jdbc.update("UPDATE parking_spaces SET status = 'AVAILABLE', current_plate = NULL, occupied_since = NULL");
        spaceService.synchronizeEntryBarrier();
        adminBearer = bearer("admin@parking.com", "Admin123*");
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private String bearer(String email, String password) throws Exception {
        String body = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
        Matcher matcher = ACCESS_TOKEN.matcher(body);
        assertThat(matcher.find()).as("login of %s", email).isTrue();
        return "Bearer " + matcher.group(1);
    }

    private String createOperatorBearer() throws Exception {
        String email = "operator-" + UUID.randomUUID() + "@parking.com";
        mvc.perform(post("/users")
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Operator", "email": "%s", "password": "Operator123"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return bearer(email, "Operator123");
    }

    private void ocrReads(String text, double confidence) {
        given(plateRecognition.read(any())).willReturn(Optional.of(new PlateReading(text, confidence)));
    }

    private ResultActions recognize(MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/entries/recognize").file(file).header("Authorization", adminBearer));
    }

    private ResultActions recognize(byte[] image) throws Exception {
        return recognize(new MockMultipartFile("image", "plate.jpg", "image/jpeg", image));
    }

    private ResultActions registerEntry(String json) throws Exception {
        return mvc.perform(post("/entries")
                .header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions registerEntry(String plate, String vehicleType) throws Exception {
        return registerEntry("""
                {"plate": "%s", "vehicleType": %s}
                """.formatted(plate, vehicleType == null ? "null" : "\"" + vehicleType + "\""));
    }

    private ResultActions enter(String plate) throws Exception {
        return registerEntry(plate, null).andExpect(status().isCreated());
    }

    private ResultActions registerExit(String plate) throws Exception {
        return mvc.perform(post("/entries/exit")
                .header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"plate": "%s"}
                        """.formatted(plate)));
    }

    private ResultActions changeStatus(String code, String newStatus) throws Exception {
        Long spaceId = jdbc.queryForObject("SELECT id FROM parking_spaces WHERE code = ?", Long.class, code);
        return mvc.perform(patch("/spaces/{spaceId}/status", spaceId)
                .header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s"}
                        """.formatted(newStatus)));
    }

    /** Fills spaces straight in the database, as if they had been occupied earlier. */
    private void occupyAllExcept(String... freeCodes) {
        jdbc.update("UPDATE parking_spaces SET status = 'OCCUPIED'");
        for (String code : freeCodes) {
            jdbc.update("UPDATE parking_spaces SET status = 'AVAILABLE' WHERE code = ?", code);
        }
        spaceService.synchronizeEntryBarrier();
    }

    private ResultActions getOccupancy() throws Exception {
        return mvc.perform(get("/spaces/occupancy").header("Authorization", adminBearer));
    }

    private MvcResult openStream() throws Exception {
        return mvc.perform(get("/spaces/stream")
                        .header("Authorization", adminBearer)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private String streamContent(MvcResult stream) {
        return new String(stream.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ── RF-01 Capture the plate image ───────────────────────────────

    @Nested
    class RF01_CapturePlateImage {

        @Test
        void acceptsJpegAndPng() throws Exception {
            ocrReads("ABC123", 0.93);

            recognize(JPEG).andExpect(status().isOk());
            recognize(new MockMultipartFile("image", "plate.png", "image/png", PNG)).andExpect(status().isOk());
        }

        @Test
        void rejectsOtherFormatsEvenIfTheyClaimToBeJpeg() throws Exception {
            recognize(GIF)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.image").value("La imagen debe ser JPEG o PNG"));

            then(plateRecognition).should(never()).read(any());
        }

        @Test
        void rejectsImagesAbove5Megabytes() throws Exception {
            byte[] image = Arrays.copyOf(JPEG, 5 * 1024 * 1024 + 1);

            recognize(image)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Error de validación en los datos enviados"))
                    .andExpect(jsonPath("$.fieldErrors.image").value("La imagen no puede superar 5 MB"));
        }

        @Test
        void rejectsEmptyOrMissingImage() throws Exception {
            recognize(new byte[0])
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.image").value("La imagen es obligatoria"));

            recognize(new MockMultipartFile("photo", "plate.jpg", "image/jpeg", JPEG))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.image").value("El archivo es obligatorio"));
        }

        @Test
        void requiresMultipartFormData() throws Exception {
            mvc.perform(post("/entries/recognize")
                            .header("Authorization", adminBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.message").value("Tipo de contenido no soportado"));
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(multipart("/entries/recognize").file(new MockMultipartFile("image", JPEG)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── RF-02 Recognize the plate text ──────────────────────────────

    @Nested
    class RF02_RecognizePlateText {

        @Test
        void returnsTheNormalizedPlate() throws Exception {
            ocrReads("abc-123", 0.93);

            recognize(JPEG)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plate").value("ABC123"))
                    .andExpect(jsonPath("$.confidence").value(0.93))
                    .andExpect(jsonPath("$.lowConfidence").value(false))
                    .andExpect(jsonPath("$.vehicleType").value("CAR"))
                    .andExpect(jsonPath("$.validFormat").value(true))
                    .andExpect(jsonPath("$.alreadyInside").value(false));
        }

        @Test
        void fixesTypicalOcrMistakes() throws Exception {
            ocrReads("A8C1O3", 0.85);

            recognize(JPEG).andExpect(jsonPath("$.plate").value("ABC103"));
        }

        @Test
        void flagsReadingsBelowTheConfidenceThreshold() throws Exception {
            ocrReads("ABC123", 0.79);
            recognize(JPEG).andExpect(jsonPath("$.lowConfidence").value(true));

            ocrReads("ABC123", 0.80);
            recognize(JPEG).andExpect(jsonPath("$.lowConfidence").value(false));
        }

        @Test
        void registersNothing() throws Exception {
            ocrReads("ABC123", 0.93);

            recognize(JPEG).andExpect(status().isOk());

            mvc.perform(get("/entries/active").header("Authorization", adminBearer))
                    .andExpect(jsonPath("$", hasSize(0)));
            getOccupancy().andExpect(jsonPath("$.occupiedSpaces").value(0));
        }

        @Test
        void operatorCanRecognize() throws Exception {
            ocrReads("ABC123", 0.93);

            mvc.perform(multipart("/entries/recognize")
                            .file(new MockMultipartFile("image", "plate.jpg", "image/jpeg", JPEG))
                            .header("Authorization", createOperatorBearer()))
                    .andExpect(status().isOk());
        }
    }

    // ── RF-03 Manual entry when recognition fails ───────────────────

    @Nested
    class RF03_ManualEntryWhenRecognitionFails {

        @Test
        void engineFailureReturns422() throws Exception {
            given(plateRecognition.read(any())).willReturn(Optional.empty());

            recognize(JPEG)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.message").value("No se pudo leer la placa, ingrésala manualmente"));
        }

        @Test
        void textThatIsNotAPlateReturns422() throws Exception {
            ocrReads("--", 0.40);

            recognize(JPEG).andExpect(status().isUnprocessableContent());
        }

        @Test
        void entryCanBeRegisteredWithoutRecognition() throws Exception {
            registerEntry("ABC-123", null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.plate").value("ABC123"));

            then(plateRecognition).should(never()).read(any());
        }
    }

    // ── RF-04 Identify the vehicle type ─────────────────────────────

    @Nested
    class RF04_IdentifyVehicleType {

        @Test
        void carPlateGetsACarSpace() throws Exception {
            enter("ABC123")
                    .andExpect(jsonPath("$.vehicleType").value("CAR"))
                    .andExpect(jsonPath("$.space.code").value("C-01"));
        }

        @Test
        void motorcyclePlateGetsAMotorcycleSpace() throws Exception {
            enter("abc12d")
                    .andExpect(jsonPath("$.plate").value("ABC12D"))
                    .andExpect(jsonPath("$.vehicleType").value("MOTORCYCLE"))
                    .andExpect(jsonPath("$.space.code").value("M-01"));
        }

        @Test
        void unknownFormatRequiresTheVehicleType() throws Exception {
            registerEntry("CD1234", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.vehicleType")
                            .value("No se reconoce el formato de la placa; indica el tipo de vehículo"));

            registerEntry("CD1234", "CAR")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.vehicleType").value("CAR"));
        }

        @Test
        void recognitionReportsUnknownFormats() throws Exception {
            ocrReads("R1234", 0.90);

            recognize(JPEG)
                    .andExpect(jsonPath("$.plate").value("R1234"))
                    .andExpect(jsonPath("$.vehicleType").value(nullValue()))
                    .andExpect(jsonPath("$.validFormat").value(false));
        }

        @Test
        void invalidPlatesAreRejected() throws Exception {
            registerEntry("{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.plate").value("La placa es obligatoria"));
            registerEntry("---", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.plate").value("La placa es obligatoria"));
            registerEntry("A-1", "CAR")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.plate").value("La placa debe tener entre 4 y 8 letras o números"));
            registerEntry("ABC123", "TRUCK").andExpect(status().isBadRequest());
        }
    }

    // ── RF-05 Entry date and time ───────────────────────────────────

    @Nested
    class RF05_EntryDateAndTime {

        @Test
        void entryTimeIsTheCurrentTimeInUtc() throws Exception {
            Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            String body = body(enter("ABC123")
                    .andExpect(jsonPath("$.entryTime").value(matchesPattern(ISO_UTC_SECONDS)))
                    .andExpect(jsonPath("$.exitTime").value(nullValue()))
                    .andExpect(jsonPath("$.durationMinutes").value(nullValue()))
                    .andExpect(jsonPath("$.status").value("ACTIVE")));
            Instant after = Instant.now();

            Instant entryTime = Instant.parse(JsonPath.read(body, "$.entryTime"));
            assertThat(entryTime).isBetween(before, after);
        }
    }

    // ── RF-06 Check availability before the entry ──────────────────

    @Nested
    class RF06_CheckAvailability {

        @Test
        void noCarSpaceLeftRejectsCarsButNotMotorcycles() throws Exception {
            jdbc.update("UPDATE parking_spaces SET status = 'OCCUPIED' WHERE vehicle_type = 'CAR'");

            registerEntry("ABC123", null).andExpect(status().isConflict());
            enter("ABC12D").andExpect(jsonPath("$.space.code").value("M-01"));
        }

        @Test
        void rejectedEntryIsNotStored() throws Exception {
            jdbc.update("UPDATE parking_spaces SET status = 'OCCUPIED' WHERE vehicle_type = 'MOTORCYCLE'");

            registerEntry("ABC12D", null).andExpect(status().isConflict());

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vehicle_entries", Integer.class)).isZero();
        }
    }

    // ── RF-07 Block the entry when there is no space ────────────────

    @Nested
    class RF07_BlockEntryWhenFull {

        @Test
        void blockedBarrierRejectsTheEntry() throws Exception {
            occupyAllExcept();
            assertThat(entryBarrier.isBlocked()).isTrue();

            registerEntry("ABC123", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("No hay espacios disponibles para carros"));
        }

        @Test
        void takingTheLastSpaceBlocksTheBarrier() throws Exception {
            occupyAllExcept("M-10");

            enter("ABC12D").andExpect(jsonPath("$.space.code").value("M-10"));

            getOccupancy()
                    .andExpect(jsonPath("$.level").value("FULL"))
                    .andExpect(jsonPath("$.entryBlocked").value(true));
        }
    }

    // ── RF-08 Tell the operator there is no space ───────────────────

    @Nested
    class RF08_NotifyNoSpace {

        @Test
        void messageNamesTheVehicleType() throws Exception {
            jdbc.update("UPDATE parking_spaces SET status = 'OCCUPIED'");

            registerEntry("ABC123", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"))
                    .andExpect(jsonPath("$.path").value("/entries"))
                    .andExpect(jsonPath("$.message").value("No hay espacios disponibles para carros"));
            registerEntry("ABC12D", null)
                    .andExpect(jsonPath("$.message").value("No hay espacios disponibles para motos"));
        }
    }

    // ── RF-09 Assign a space ────────────────────────────────────────

    @Nested
    class RF09_AssignSpace {

        @Test
        void assignsTheFirstAvailableSpaceInCodeOrder() throws Exception {
            changeStatus("C-01", "OCCUPIED").andExpect(status().isOk());
            changeStatus("C-03", "OCCUPIED").andExpect(status().isOk());

            enter("AAA111").andExpect(jsonPath("$.space.code").value("C-02"));
            enter("BBB222").andExpect(jsonPath("$.space.code").value("C-04"));
        }

        @Test
        void spaceMapShowsThePlateAndEntryTime() throws Exception {
            String entryTime = JsonPath.read(body(enter("ABC123")), "$.entryTime");

            mvc.perform(get("/spaces").param("status", "OCCUPIED").header("Authorization", adminBearer))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].code").value("C-01"))
                    .andExpect(jsonPath("$[0].currentPlate").value("ABC123"))
                    .andExpect(jsonPath("$[0].occupiedSince").value(entryTime));
        }

        @Test
        void assignmentIsPushedInRealTime() throws Exception {
            MvcResult stream = openStream();

            enter("ABC123");

            assertThat(streamContent(stream))
                    .contains("\"code\":\"C-01\",\"vehicleType\":\"CAR\",\"status\":\"OCCUPIED\",\"currentPlate\":\"ABC123\"")
                    .contains("\"occupiedSpaces\":1");
        }

        @Test
        void concurrentEntriesNeverShareASpace() throws Exception {
            int cars = 25;
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(cars);
            List<Future<VehicleEntry>> results = new ArrayList<>();
            try {
                for (int i = 0; i < cars; i++) {
                    String plate = "CAR%03d".formatted(i);
                    results.add(executor.submit(() -> {
                        start.await();
                        return registerEntryUseCase.registerEntry(new RegisterEntryCommand(plate, null));
                    }));
                }
                start.countDown();

                List<String> assignedSpaces = new ArrayList<>();
                int rejected = 0;
                for (Future<VehicleEntry> result : results) {
                    try {
                        assignedSpaces.add(result.get(30, TimeUnit.SECONDS).getSpace().getCode());
                    } catch (java.util.concurrent.ExecutionException ex) {
                        assertThat(ex.getCause()).isInstanceOf(NoSpaceAvailableException.class);
                        rejected++;
                    }
                }

                assertThat(assignedSpaces).hasSize(20).doesNotHaveDuplicates();
                assertThat(rejected).isEqualTo(5);
                assertThat(jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT space_id) FROM vehicle_entries WHERE status = 'ACTIVE'", Integer.class))
                        .isEqualTo(20);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    // ── RF-10 Store the entry ───────────────────────────────────────

    @Nested
    class RF10_StoreEntry {

        @Test
        void entryIsStored() throws Exception {
            String body = body(enter("ABC123")
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.space.id").isNumber()));
            Integer id = JsonPath.read(body, "$.id");

            assertThat(jdbc.queryForMap("""
                    SELECT e.plate, e.vehicle_type, e.status, s.code
                    FROM vehicle_entries e JOIN parking_spaces s ON s.id = e.space_id
                    WHERE e.id = ?
                    """, id))
                    .containsEntry("plate", "ABC123")
                    .containsEntry("vehicle_type", "CAR")
                    .containsEntry("status", "ACTIVE")
                    .containsEntry("code", "C-01");
        }

        @Test
        void activeEntriesAreListedMostRecentFirst() throws Exception {
            enter("AAA111");
            enter("BBB22C");
            enter("CCC333");
            registerExit("CCC333").andExpect(status().isOk());

            mvc.perform(get("/entries/active").header("Authorization", adminBearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].plate").value("BBB22C"))
                    .andExpect(jsonPath("$[0].space.code").value("M-01"))
                    .andExpect(jsonPath("$[1].plate").value("AAA111"));
        }

        @Test
        void operatorCanRegisterEntries() throws Exception {
            mvc.perform(post("/entries")
                            .header("Authorization", createOperatorBearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"plate\": \"ABC123\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get("/entries/active")).andExpect(status().isUnauthorized());
            mvc.perform(post("/entries").contentType(MediaType.APPLICATION_JSON).content("{\"plate\": \"ABC123\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── RF-11 Validate that the plate is not already inside ─────────

    @Nested
    class RF11_PlateNotAlreadyInside {

        @Test
        void samePlateCannotEnterTwice() throws Exception {
            enter("ABC123");

            registerEntry("abc-123", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("El vehículo ABC123 ya se encuentra dentro"));
            getOccupancy().andExpect(jsonPath("$.occupiedSpaces").value(1));
        }

        @Test
        void recognitionWarnsThatTheVehicleIsInside() throws Exception {
            enter("ABC123");
            ocrReads("ABC123", 0.95);

            recognize(JPEG).andExpect(jsonPath("$.alreadyInside").value(true));
        }

        @Test
        void plateCanEnterAgainAfterLeaving() throws Exception {
            enter("ABC123");
            registerExit("ABC123").andExpect(status().isOk());

            enter("ABC123").andExpect(jsonPath("$.status").value("ACTIVE"));
        }
    }

    // ── Vehicle exit ────────────────────────────────────────────────

    @Nested
    class VehicleExit {

        @Test
        void exitClosesTheEntryAndFreesTheSpace() throws Exception {
            enter("ABC123");

            registerExit("abc 123")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plate").value("ABC123"))
                    .andExpect(jsonPath("$.space.code").value("C-01"))
                    .andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.exitTime").value(matchesPattern(ISO_UTC_SECONDS)))
                    .andExpect(jsonPath("$.durationMinutes").value(0));

            mvc.perform(get("/spaces").param("vehicleType", "CAR").header("Authorization", adminBearer))
                    .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                    .andExpect(jsonPath("$[0].currentPlate").value(nullValue()))
                    .andExpect(jsonPath("$[0].occupiedSince").value(nullValue()));
            mvc.perform(get("/entries/active").header("Authorization", adminBearer))
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void exitIsPushedInRealTime() throws Exception {
            enter("ABC123");
            MvcResult stream = openStream();

            registerExit("ABC123").andExpect(status().isOk());

            assertThat(streamContent(stream))
                    .contains("\"code\":\"C-01\",\"vehicleType\":\"CAR\",\"status\":\"AVAILABLE\",\"currentPlate\":null");
        }

        @Test
        void exitUnblocksTheBarrier() throws Exception {
            occupyAllExcept("C-20");
            enter("ABC123");
            getOccupancy().andExpect(jsonPath("$.entryBlocked").value(true));

            registerExit("ABC123").andExpect(status().isOk());

            getOccupancy().andExpect(jsonPath("$.entryBlocked").value(false));
        }

        @Test
        void exitWithoutAnActiveEntryReturns404() throws Exception {
            registerExit("XYZ987")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No hay un ingreso activo para la placa XYZ987"));

            registerExit("").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.plate").value("La placa es obligatoria"));
        }

        @Test
        void spaceWithARegisteredVehicleCannotBeReleasedByHand() throws Exception {
            enter("ABC123");

            changeStatus("C-01", "AVAILABLE")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("La plaza tiene un vehículo registrado; registra su salida"));
        }

        @Test
        void manualOccupationStillWorksWithoutAPlate() throws Exception {
            changeStatus("C-05", "OCCUPIED")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentPlate").value(nullValue()))
                    .andExpect(jsonPath("$.occupiedSince").value(nullValue()));

            changeStatus("C-05", "AVAILABLE").andExpect(status().isOk());
        }
    }
}
