package com.jjenus.tracker.core.infrastructure;

import com.jjenus.tracker.core.domain.Vehicle;
import java.util.List;
import java.util.Optional;

public interface IVehicleRepository {
    Optional<Vehicle> findById(String vehicleId);
    List<Vehicle> findAll();
    void save(Vehicle vehicle);
    void delete(String vehicleId);
    boolean exists(String vehicleId);
}
