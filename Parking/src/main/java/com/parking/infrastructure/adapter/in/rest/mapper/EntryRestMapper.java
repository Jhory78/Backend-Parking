package com.parking.infrastructure.adapter.in.rest.mapper;

import com.parking.application.port.in.RegisterEntryCommand;
import com.parking.domain.entity.ParkingSpace;
import com.parking.domain.entity.PlateRecognition;
import com.parking.domain.entity.VehicleEntry;
import com.parking.infrastructure.adapter.in.rest.dto.EntryResponse;
import com.parking.infrastructure.adapter.in.rest.dto.PlateRecognitionResponse;
import com.parking.infrastructure.adapter.in.rest.dto.RegisterEntryRequest;
import com.parking.infrastructure.config.OcrProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EntryRestMapper {

    private final OcrProperties ocrProperties;

    public RegisterEntryCommand toCommand(RegisterEntryRequest request) {
        return new RegisterEntryCommand(request.plate(), request.vehicleType());
    }

    public EntryResponse toResponse(VehicleEntry entry) {
        ParkingSpace space = entry.getSpace();
        return new EntryResponse(
                entry.getId(),
                entry.getPlate(),
                entry.getVehicleType(),
                new EntryResponse.AssignedSpace(space.getId(), space.getCode()),
                entry.getEntryTime(),
                entry.getExitTime(),
                entry.durationMinutes(),
                entry.getStatus());
    }

    public List<EntryResponse> toResponses(List<VehicleEntry> entries) {
        return entries.stream().map(this::toResponse).toList();
    }

    public PlateRecognitionResponse toResponse(PlateRecognition recognition) {
        return new PlateRecognitionResponse(
                recognition.plate(),
                recognition.confidence(),
                recognition.confidence() < ocrProperties.confidenceThreshold(),
                recognition.vehicleType(),
                recognition.validFormat(),
                recognition.alreadyInside());
    }
}
