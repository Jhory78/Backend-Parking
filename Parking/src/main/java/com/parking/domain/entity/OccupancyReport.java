package com.parking.domain.entity;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Occupancy of the whole parking lot and of each vehicle type.
 */
public record OccupancyReport(Occupancy overall, Map<VehicleType, Occupancy> byVehicleType) {

    public OccupancyReport {
        byVehicleType = Map.copyOf(byVehicleType);
    }

    public static OccupancyReport of(Collection<ParkingSpace> spaces) {
        Map<VehicleType, Occupancy> byVehicleType = new EnumMap<>(VehicleType.class);
        for (VehicleType vehicleType : VehicleType.values()) {
            byVehicleType.put(vehicleType, occupancyOf(spaces.stream()
                    .filter(space -> space.getVehicleType() == vehicleType)
                    .toList()));
        }
        return new OccupancyReport(occupancyOf(spaces), byVehicleType);
    }

    public Occupancy forVehicleType(VehicleType vehicleType) {
        return byVehicleType.get(vehicleType);
    }

    private static Occupancy occupancyOf(Collection<ParkingSpace> spaces) {
        int occupied = (int) spaces.stream().filter(ParkingSpace::isOccupied).count();
        return new Occupancy(spaces.size(), occupied);
    }
}
