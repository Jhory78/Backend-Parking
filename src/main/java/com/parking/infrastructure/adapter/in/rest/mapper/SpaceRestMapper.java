package com.parking.infrastructure.adapter.in.rest.mapper;

import com.parking.domain.entity.Occupancy;
import com.parking.domain.entity.OccupancyReport;
import com.parking.domain.entity.OccupancyState;
import com.parking.domain.entity.OccupancyUpdate;
import com.parking.domain.entity.ParkingSpace;
import com.parking.domain.entity.VehicleType;
import com.parking.infrastructure.adapter.in.rest.dto.AvailabilityResponse;
import com.parking.infrastructure.adapter.in.rest.dto.OccupancyEventResponse;
import com.parking.infrastructure.adapter.in.rest.dto.OccupancyResponse;
import com.parking.infrastructure.adapter.in.rest.dto.SpaceResponse;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SpaceRestMapper {

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");

    public SpaceResponse toResponse(ParkingSpace space) {
        return new SpaceResponse(space.getId(), space.getCode(), space.getVehicleType(), space.getStatus());
    }

    public List<SpaceResponse> toResponses(List<ParkingSpace> spaces) {
        return spaces.stream().map(this::toResponse).toList();
    }

    public AvailabilityResponse toAvailability(VehicleType vehicleType, Occupancy occupancy) {
        return new AvailabilityResponse(
                vehicleType,
                occupancy.totalSpaces(),
                occupancy.availableSpaces(),
                occupancy.occupiedSpaces(),
                occupancy.percentage());
    }

    public List<AvailabilityResponse> toAvailabilities(Map<VehicleType, Occupancy> occupancyByType) {
        return occupancyByType.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toAvailability(entry.getKey(), entry.getValue()))
                .toList();
    }

    public OccupancyResponse toResponse(OccupancyState state) {
        OccupancyReport report = state.report();
        Occupancy overall = report.overall();

        return new OccupancyResponse(
                overall.totalSpaces(),
                overall.occupiedSpaces(),
                overall.availableSpaces(),
                overall.percentage(),
                overall.level(),
                state.entryBlocked(),
                alertMessageFor(overall),
                toAvailabilities(report.byVehicleType()));
    }

    public OccupancyEventResponse toEvent(OccupancyUpdate update) {
        SpaceResponse changedSpace = update.changedSpace() == null ? null : toResponse(update.changedSpace());
        return new OccupancyEventResponse(changedSpace, toResponse(update.state()));
    }

    /** Operator-facing alert for RF-20 (high occupancy) and RF-21 (full). */
    private String alertMessageFor(Occupancy occupancy) {
        return switch (occupancy.level()) {
            case NORMAL -> null;
            case HIGH -> "Ocupación alta: %s %% de la capacidad (%d de %d espacios ocupados)".formatted(
                    formatPercentage(occupancy.percentage()), occupancy.occupiedSpaces(), occupancy.totalSpaces());
            case FULL -> "Parqueadero lleno: no hay espacios disponibles de ningún tipo. Ingreso bloqueado.";
        };
    }

    private String formatPercentage(double percentage) {
        // DecimalFormat is not thread-safe: one instance per call
        return new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(SPANISH)).format(percentage);
    }
}
