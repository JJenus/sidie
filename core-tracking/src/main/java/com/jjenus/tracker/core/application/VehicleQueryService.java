package com.jjenus.tracker.core.application;

import com.jjenus.tracker.core.domain.entity.Vehicle;
import com.jjenus.tracker.core.infrastructure.repository.VehicleRepository;
import com.jjenus.tracker.shared.domain.LocationPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class VehicleQueryService {
    private final VehicleRepository vehicleRepository;

    public VehicleQueryService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public Optional<Vehicle> getVehicleById(String vehicleId) {
        return vehicleRepository.findById(vehicleId);
    }

    public List<Vehicle> getAllVehicles() {
        List<Vehicle> result = new ArrayList<>();
        findAllBatched(PageRequest.of(0, 500), result::add);
        return result;
    }

    private void findAllBatched(Pageable pageable, Consumer<Vehicle> consumer) {
        Page<Vehicle> page;
        do {
            page = vehicleRepository.findAll(pageable);
            page.forEach(consumer);
            pageable = PageRequest.of(pageable.getPageNumber() + 1, pageable.getPageSize());
        } while (page.hasNext());
    }

    public Optional<LocationPoint> getCurrentLocation(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
            .map(Vehicle::getCurrentLocationPoint);
    }

    public boolean isVehicleMoving(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
            .map(v -> {
                LocationPoint loc = v.getCurrentLocationPoint();
                return loc != null && loc.speedKmh() > 0;
            })
            .orElse(false);
    }

    public Optional<Float> getVehicleSpeed(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
            .map(v -> {
                LocationPoint loc = v.getCurrentLocationPoint();
                return loc != null ? loc.speedKmh() : null;
            });
    }
}
