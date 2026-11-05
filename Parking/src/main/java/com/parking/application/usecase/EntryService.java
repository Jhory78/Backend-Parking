package com.parking.application.usecase;

import com.parking.application.port.in.FindEntriesUseCase;
import com.parking.application.port.in.RecognizePlateUseCase;
import com.parking.application.port.in.RegisterEntryCommand;
import com.parking.application.port.in.RegisterEntryUseCase;
import com.parking.application.port.in.RegisterExitUseCase;
import com.parking.application.port.in.SpaceAllocationUseCase;
import com.parking.application.port.out.PlateRecognitionPort;
import com.parking.application.port.out.UserRepositoryPort;
import com.parking.application.port.out.VehicleEntryRepositoryPort;
import com.parking.domain.entity.EntryHistoryPage;
import com.parking.domain.entity.EntryHistoryQuery;
import com.parking.domain.entity.LicensePlate;
import com.parking.domain.entity.ParkingSpace;
import com.parking.domain.entity.PlateReading;
import com.parking.domain.entity.PlateRecognition;
import com.parking.domain.entity.User;
import com.parking.domain.entity.VehicleEntry;
import com.parking.domain.entity.VehicleType;
import com.parking.domain.exception.ActiveEntryNotFoundException;
import com.parking.domain.exception.FieldValidationException;
import com.parking.domain.exception.PlateNotRecognizedException;
import com.parking.domain.exception.UserNotFoundException;
import com.parking.domain.exception.VehicleAlreadyInsideException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Vehicle entries and exits by plate (RF-01..11).
 * <p>
 * The OCR only proposes the plate; the entry is registered when the operator
 * confirms it. Spaces are taken and freed through {@link SpaceAllocationUseCase}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntryService implements RecognizePlateUseCase, RegisterEntryUseCase, RegisterExitUseCase,
        FindEntriesUseCase {

    static final String IMAGE_FIELD = "image";
    static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    static final int MAX_HISTORY_PAGE_SIZE = 100;

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final PlateRecognitionPort plateRecognition;
    private final VehicleEntryRepositoryPort entryRepository;
    private final UserRepositoryPort userRepository;
    private final SpaceAllocationUseCase spaceAllocation;
    private final Clock clock;

    @Override
    public PlateRecognition recognize(byte[] image) {
        validateImage(image);

        PlateReading reading = plateRecognition.read(image).orElseThrow(PlateNotRecognizedException::new);
        LicensePlate plate = LicensePlate.fromOcr(reading.text()).orElseThrow(PlateNotRecognizedException::new);

        return new PlateRecognition(
                plate.value(),
                reading.confidence(),
                plate.vehicleType().orElse(null),
                entryRepository.existsActiveByPlate(plate.value()));
    }

    @Override
    @Transactional
    public VehicleEntry registerEntry(RegisterEntryCommand command) {
        LicensePlate plate = LicensePlate.of(command.plate());
        VehicleType vehicleType = Optional.ofNullable(command.vehicleType())
                .or(plate::vehicleType)
                .orElseThrow(() -> new FieldValidationException("vehicleType",
                        "No se reconoce el formato de la placa; indica el tipo de vehículo"));

        if (entryRepository.existsActiveByPlate(plate.value())) {
            throw new VehicleAlreadyInsideException(plate.value());
        }

        User operator = userRepository.findById(command.operatorId()).orElseThrow(UserNotFoundException::new);

        Instant entryTime = now();
        ParkingSpace space = spaceAllocation.assignFirstAvailable(vehicleType, plate, entryTime);
        return entryRepository.save(VehicleEntry.register(plate, vehicleType, space, entryTime, operator));
    }

    @Override
    @Transactional
    public VehicleEntry registerExit(String rawPlate) {
        LicensePlate plate = LicensePlate.of(rawPlate);
        VehicleEntry entry = entryRepository.findActiveByPlate(plate.value())
                .orElseThrow(() -> new ActiveEntryNotFoundException(plate.value()));

        entry.close(now());
        VehicleEntry closedEntry = entryRepository.save(entry);
        spaceAllocation.vacate(entry.getSpace().getId());
        return closedEntry;
    }

    @Override
    public List<VehicleEntry> findActiveEntries() {
        return entryRepository.findAllActive();
    }

    @Override
    public EntryHistoryPage findHistory(EntryHistoryQuery query) {
        if (query.page() < 0) {
            throw new FieldValidationException("page", "La página no puede ser negativa");
        }
        if (query.size() < 1 || query.size() > MAX_HISTORY_PAGE_SIZE) {
            throw new FieldValidationException("size",
                    "El tamaño de página debe estar entre 1 y %d".formatted(MAX_HISTORY_PAGE_SIZE));
        }
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new FieldValidationException("from", "La fecha inicial no puede ser posterior a la final");
        }

        String plate = LicensePlate.normalize(query.plate());
        return entryRepository.findHistory(new EntryHistoryQuery(
                plate.isEmpty() ? null : plate, query.from(), query.to(), query.page(), query.size()));
    }

    /** RF-01: JPEG or PNG, checked by content (the declared content type can lie). */
    private void validateImage(byte[] image) {
        if (image == null || image.length == 0) {
            throw new FieldValidationException(IMAGE_FIELD, "La imagen es obligatoria");
        }
        if (image.length > MAX_IMAGE_BYTES) {
            throw new FieldValidationException(IMAGE_FIELD, "La imagen no puede superar 5 MB");
        }
        if (!startsWith(image, JPEG_SIGNATURE) && !startsWith(image, PNG_SIGNATURE)) {
            throw new FieldValidationException(IMAGE_FIELD, "La imagen debe ser JPEG o PNG");
        }
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /** Whole seconds: the API returns times like {@code 2026-10-14T15:30:00Z}. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }
}
