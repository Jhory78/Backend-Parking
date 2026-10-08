package com.parking;

import com.parking.application.port.out.EntryBarrierPort;
import com.parking.application.usecase.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for Sprint 2 user stories (RF-16 to RF-22).
 * <p>
 * The V3 migration loads 30 spaces: 20 for cars (C-01..C-20) and 10 for
 * motorcycles (M-01..M-10). 24 occupied spaces are exactly 80 %.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SprintTwoAcceptanceTest {

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private EntryBarrierPort entryBarrier;

    private String adminBearer;

    @BeforeEach
    void resetParkingLot() throws Exception {
        jdbc.update("UPDATE parking_spaces SET status = 'AVAILABLE'");
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

    private Long spaceId(String code) {
        return jdbc.queryForObject("SELECT id FROM parking_spaces WHERE code = ?", Long.class, code);
    }

    private ResultActions changeStatus(String code, String newStatus) throws Exception {
        return mvc.perform(patch("/spaces/{spaceId}/status", spaceId(code))
                .header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s"}
                        """.formatted(newStatus)));
    }

    private void occupy(String code) throws Exception {
        changeStatus(code, "OCCUPIED").andExpect(status().isOk());
    }

    /** Occupies the first {@code count} spaces in code order (cars first, then motorcycles). */
    private void occupySpaces(int count) throws Exception {
        List<String> codes = jdbc.queryForList(
                "SELECT code FROM parking_spaces ORDER BY code", String.class);
        for (String code : codes.subList(0, count)) {
            occupy(code);
        }
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

    /** Server-Sent Events are always UTF-8, whatever the response charset says. */
    private String streamContent(MvcResult stream) {
        return new String(stream.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
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

    // ── RF-16 Real-time space status ────────────────────────────────

    @Nested
    class RF16_RealTimeSpaceStatus {

        @Test
        void statusChangeIsStored() throws Exception {
            changeStatus("C-01", "OCCUPIED")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("C-01"))
                    .andExpect(jsonPath("$.status").value("OCCUPIED"));

            mvc.perform(get("/spaces").param("status", "OCCUPIED").header("Authorization", adminBearer))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].code").value("C-01"));
        }

        @Test
        void streamSendsTheCurrentStateOnConnection() throws Exception {
            String content = streamContent(openStream());

            assertThat(content)
                    .contains("event:occupancy")
                    .contains("\"changedSpace\":null")
                    .contains("\"totalSpaces\":30");
        }

        @Test
        void streamPushesEveryChange() throws Exception {
            MvcResult stream = openStream();

            occupy("C-02");
            changeStatus("C-02", "AVAILABLE").andExpect(status().isOk());

            String content = streamContent(stream);
            assertThat(content)
                    .contains("{\"id\":%d,\"code\":\"C-02\",\"vehicleType\":\"CAR\",\"status\":\"OCCUPIED\"}"
                            .formatted(spaceId("C-02")))
                    .contains("{\"id\":%d,\"code\":\"C-02\",\"vehicleType\":\"CAR\",\"status\":\"AVAILABLE\"}"
                            .formatted(spaceId("C-02")));
        }

        @Test
        void rejectedChangeIsNotPushed() throws Exception {
            occupy("C-03");
            MvcResult stream = openStream();
            int contentAfterSnapshot = streamContent(stream).length();

            changeStatus("C-03", "OCCUPIED").andExpect(status().isConflict());

            assertThat(streamContent(stream)).hasSize(contentAfterSnapshot);
        }

        @Test
        void occupiedSpaceCannotBeOccupiedAgain() throws Exception {
            occupy("C-04");

            changeStatus("C-04", "OCCUPIED")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("El espacio C-04 ya está ocupado"));
        }

        @Test
        void availableSpaceCannotBeReleased() throws Exception {
            changeStatus("C-05", "AVAILABLE")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("El espacio C-05 ya está disponible"));
        }

        @Test
        void unknownSpaceReturns404() throws Exception {
            mvc.perform(patch("/spaces/{spaceId}/status", 999_999)
                            .header("Authorization", adminBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"OCCUPIED\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Espacio no encontrado"));
        }

        @Test
        void invalidStatusIsRejected() throws Exception {
            changeStatus("C-06", "RESERVED").andExpect(status().isBadRequest());
            mvc.perform(patch("/spaces/{spaceId}/status", spaceId("C-06"))
                            .header("Authorization", adminBearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.status").value("El estado es obligatorio"));
        }

        @Test
        void operatorCanChangeStatus() throws Exception {
            mvc.perform(patch("/spaces/{spaceId}/status", spaceId("C-07"))
                            .header("Authorization", createOperatorBearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"OCCUPIED\"}"))
                    .andExpect(status().isOk());
        }
    }

    // ── RF-17 Available spaces by vehicle type ──────────────────────

    @Nested
    class RF17_AvailabilityByVehicleType {

        @Test
        void returnsAvailabilityOfEveryType() throws Exception {
            occupy("M-01");

            mvc.perform(get("/spaces/availability").header("Authorization", adminBearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].vehicleType").value("CAR"))
                    .andExpect(jsonPath("$[0].totalSpaces").value(20))
                    .andExpect(jsonPath("$[0].availableSpaces").value(20))
                    .andExpect(jsonPath("$[1].vehicleType").value("MOTORCYCLE"))
                    .andExpect(jsonPath("$[1].totalSpaces").value(10))
                    .andExpect(jsonPath("$[1].availableSpaces").value(9))
                    .andExpect(jsonPath("$[1].occupiedSpaces").value(1))
                    .andExpect(jsonPath("$[1].occupancyPercentage").value(10.0));
        }

        @Test
        void filtersByVehicleType() throws Exception {
            mvc.perform(get("/spaces/availability").param("vehicleType", "CAR").header("Authorization", adminBearer))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].vehicleType").value("CAR"))
                    .andExpect(jsonPath("$[0].availableSpaces").value(20));
        }

        @Test
        void invalidVehicleTypeIsRejected() throws Exception {
            mvc.perform(get("/spaces/availability").param("vehicleType", "TRUCK").header("Authorization", adminBearer))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Valor inválido para el parámetro 'vehicleType'"));
        }
    }

    // ── RF-18 Space map ─────────────────────────────────────────────

    @Nested
    class RF18_SpaceMap {

        @Test
        void listsEverySpaceOrderedByCode() throws Exception {
            occupy("C-10");

            mvc.perform(get("/spaces").header("Authorization", adminBearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(30)))
                    .andExpect(jsonPath("$[0].code").value("C-01"))
                    .andExpect(jsonPath("$[0].vehicleType").value("CAR"))
                    .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                    .andExpect(jsonPath("$[9].code").value("C-10"))
                    .andExpect(jsonPath("$[9].status").value("OCCUPIED"))
                    .andExpect(jsonPath("$[29].code").value("M-10"));
        }

        @Test
        void filtersByVehicleTypeAndStatus() throws Exception {
            occupy("M-02");

            mvc.perform(get("/spaces").param("vehicleType", "MOTORCYCLE").header("Authorization", adminBearer))
                    .andExpect(jsonPath("$", hasSize(10)));
            mvc.perform(get("/spaces")
                            .param("vehicleType", "MOTORCYCLE")
                            .param("status", "OCCUPIED")
                            .header("Authorization", adminBearer))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].code").value("M-02"));
        }

        @Test
        void operatorCanSeeTheMap() throws Exception {
            mvc.perform(get("/spaces").header("Authorization", createOperatorBearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(30)));
        }

        @Test
        void requiresAuthentication() throws Exception {
            mvc.perform(get("/spaces")).andExpect(status().isUnauthorized());
            mvc.perform(get("/spaces/stream")).andExpect(status().isUnauthorized());
        }
    }

    // ── RF-19 Detect high occupancy ─────────────────────────────────

    @Nested
    class RF19_DetectHighOccupancy {

        @Test
        void below80PercentIsNormal() throws Exception {
            occupySpaces(23);

            getOccupancy()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.occupiedSpaces").value(23))
                    .andExpect(jsonPath("$.occupancyPercentage").value(76.7))
                    .andExpect(jsonPath("$.level").value("NORMAL"))
                    .andExpect(jsonPath("$.alertMessage").value(nullValue()));
        }

        @Test
        void exactly80PercentIsHigh() throws Exception {
            occupySpaces(24);

            getOccupancy()
                    .andExpect(jsonPath("$.occupancyPercentage").value(80.0))
                    .andExpect(jsonPath("$.level").value("HIGH"));
        }
    }

    // ── RF-20 Notify high occupancy ─────────────────────────────────

    @Nested
    class RF20_NotifyHighOccupancy {

        @Test
        void occupancyIncludesAnAlertForTheOperator() throws Exception {
            occupySpaces(24);

            getOccupancy().andExpect(jsonPath("$.alertMessage")
                    .value("Ocupación alta: 80 % de la capacidad (24 de 30 espacios ocupados)"));
        }

        @Test
        void alertIsPushedInRealTimeWhenThresholdIsReached() throws Exception {
            occupySpaces(23);
            MvcResult stream = openStream();

            occupy("M-05");

            assertThat(streamContent(stream))
                    .contains("\"level\":\"HIGH\"")
                    .contains("Ocupación alta: 80 % de la capacidad");
        }
    }

    // ── RF-21 Detect full capacity ──────────────────────────────────

    @Nested
    class RF21_DetectFullCapacity {

        @Test
        void allSpacesOccupiedIsFull() throws Exception {
            occupySpaces(30);

            getOccupancy()
                    .andExpect(jsonPath("$.availableSpaces").value(0))
                    .andExpect(jsonPath("$.occupancyPercentage").value(100.0))
                    .andExpect(jsonPath("$.level").value("FULL"))
                    .andExpect(jsonPath("$.alertMessage")
                            .value("Parqueadero lleno: no hay espacios disponibles de ningún tipo. Ingreso bloqueado."));
        }

        @Test
        void oneFullVehicleTypeIsNotAFullParkingLot() throws Exception {
            for (int i = 1; i <= 10; i++) {
                occupy("M-%02d".formatted(i));
            }

            getOccupancy()
                    .andExpect(jsonPath("$.level").value("NORMAL"))
                    .andExpect(jsonPath("$.byVehicleType[1].vehicleType").value("MOTORCYCLE"))
                    .andExpect(jsonPath("$.byVehicleType[1].availableSpaces").value(0));
        }
    }

    // ── RF-22 Block entry at full capacity ──────────────────────────

    @Nested
    class RF22_BlockEntryAtFullCapacity {

        @Test
        void barrierIsBlockedWhenFullAndUnblockedWhenASpaceIsReleased() throws Exception {
            occupySpaces(29);
            getOccupancy().andExpect(jsonPath("$.entryBlocked").value(false));

            occupy("M-10");
            getOccupancy().andExpect(jsonPath("$.entryBlocked").value(true));
            assertThat(entryBarrier.isBlocked()).isTrue();

            changeStatus("C-01", "AVAILABLE").andExpect(status().isOk());
            getOccupancy().andExpect(jsonPath("$.entryBlocked").value(false));
            assertThat(entryBarrier.isBlocked()).isFalse();
        }

        @Test
        void barrierStateIsPushedInRealTime() throws Exception {
            occupySpaces(29);
            MvcResult stream = openStream();

            occupy("M-10");

            assertThat(streamContent(stream))
                    .contains("\"level\":\"FULL\"")
                    .contains("\"entryBlocked\":true");
        }
    }
}
