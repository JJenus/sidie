package com.jjenus.tracker.core.infrastructure;

import com.jjenus.tracker.core.domain.Vehicle;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryVehicleRepository implements IVehicleRepository {
    private final Map<String, Vehicle> vehicles = new ConcurrentHashMap<>();
    
    @Override
    public Optional<Vehicle> findById(String vehicleId) {
        return Optional.ofNullable(vehicles.get(vehicleId));
    }
    
    @Override
    public List<Vehicle> findAll() {
        return List.copyOf(vehicles.values());
    }
    
    @Override
    public void save(Vehicle vehicle) {
        if (vehicle != null) {
            vehicles.put(vehicle.getVehicleId(), vehicle);
        }
    }
    
    @Override
    public void delete(String vehicleId) {
        vehicles.remove(vehicleId);
    }
    
    @Override
    public boolean exists(String vehicleId) {
        return vehicles.containsKey(vehicleId);
    }
}
