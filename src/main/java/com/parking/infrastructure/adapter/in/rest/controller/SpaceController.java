package com.parking.infrastructure.adapter.in.rest.controller;

import com.parking.application.port.in.ChangeSpaceStatusUseCase;
import com.parking.application.port.in.FindSpacesUseCase;
import com.parking.domain.entity.OccupancyUpdate;
import com.parking.domain.entity.SpaceStatus;
import com.parking.domain.entity.VehicleType;
import com.parking.infrastructure.adapter.in.rest.dto.AvailabilityResponse;
import com.parking.infrastructure.adapter.in.rest.dto.OccupancyResponse;
import com.parking.infrastructure.adapter.in.rest.dto.SpaceResponse;
import com.parking.infrastructure.adapter.in.rest.dto.UpdateSpaceStatusRequest;
import com.parking.infrastructure.adapter.in.rest.mapper.SpaceRestMapper;
import com.parking.infrastructure.adapter.out.notification.SseOccupancyNotifierAdapter;
import com.parking.infrastructure.utils.constants.ApiRoutes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Parking spaces (Sprint 2). Available to operators and administrators.
 */
@RestController
@RequestMapping(ApiRoutes.SPACES)
@RequiredArgsConstructor
public class SpaceController {

    private final FindSpacesUseCase findSpacesUseCase;
    private final ChangeSpaceStatusUseCase changeSpaceStatusUseCase;
    private final SseOccupancyNotifierAdapter occupancyNotifier;
    private final SpaceRestMapper mapper;

    /** RF-18: status of every space, optionally filtered. */
    @GetMapping
    public List<SpaceResponse> findSpaces(@RequestParam(required = false) VehicleType vehicleType,
                                          @RequestParam(required = false) SpaceStatus status) {
        return mapper.toResponses(findSpacesUseCase.findSpaces(vehicleType, status));
    }

    /** RF-17: available spaces per vehicle type. */
    @GetMapping(ApiRoutes.SPACES_AVAILABILITY)
    public List<AvailabilityResponse> findAvailability(@RequestParam(required = false) VehicleType vehicleType) {
        return mapper.toAvailabilities(findSpacesUseCase.findAvailability(vehicleType));
    }

    /** RF-19, RF-21, RF-22: occupancy level, alert and barrier state. */
    @GetMapping(ApiRoutes.SPACES_OCCUPANCY)
    public OccupancyResponse getOccupancy() {
        return mapper.toResponse(findSpacesUseCase.getOccupancyState());
    }

    /** RF-16: marks a space as occupied or available and notifies every screen. */
    @PatchMapping(ApiRoutes.SPACE_STATUS)
    public SpaceResponse changeStatus(@PathVariable Long spaceId, @Valid @RequestBody UpdateSpaceStatusRequest request) {
        return mapper.toResponse(changeSpaceStatusUseCase.changeStatus(spaceId, request.status()));
    }

    /** RF-16, RF-20: real-time stream of occupancy events (Server-Sent Events). */
    @GetMapping(path = ApiRoutes.SPACES_STREAM, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOccupancy() {
        return occupancyNotifier.subscribe(OccupancyUpdate.snapshot(findSpacesUseCase.getOccupancyState()));
    }
}
