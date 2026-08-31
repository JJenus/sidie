package com.jjenus.tracker.core.application;

import com.jjenus.tracker.core.domain.entity.Vehicle;
import com.jjenus.tracker.core.infrastructure.repository.VehicleRepository;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.shared.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Transactional(readOnly = true)
public class VehicleQueryService {
    private final VehicleRepository vehicleRepository;

    public VehicleQueryService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public Optional<Vehicle> getVehicleById(String vehicleId) {
        Long orgId = TenantContext.getCurrentOrgId();
        return vehicleRepository.findById(vehicleId)
            .filter(v -> orgId.equals(v.getOrganizationId()));
    }

    public List<Vehicle> getAllVehicles() {
        Long orgId = TenantContext.getCurrentOrgId();
        List<Vehicle> result = new ArrayList<>();
        findAllBatched(orgId, PageRequest.of(0, 500), result::add);
        return result;
    }

    private void findAllBatched(Long orgId, Pageable pageable, Consumer<Vehicle> consumer) {
        Page<Vehicle> page;
        do {
            page = vehicleRepository.findByOrganizationId(orgId, pageable);
            page.forEach(consumer);
            pageable = PageRequest.of(pageable.getPageNumber() + 1, pageable.getPageSize());
        } while (page.hasNext());
    }

    public Optional<LocationPoint> getCurrentLocation(String vehicleId) {
        return getVehicleById(vehicleId)
            .map(Vehicle::getCurrentLocationPoint);
    }

    public boolean isVehicleMoving(String vehicleId) {
        return getVehicleById(vehicleId)
            .map(v -> {
                LocationPoint loc = v.getCurrentLocationPoint();
                return loc != null && loc.speedKmh() > 0;
            })
            .orElse(false);
    }

    public Optional<Float> getVehicleSpeed(String vehicleId) {
        return getVehicleById(vehicleId)
            .map(v -> {
                LocationPoint loc = v.getCurrentLocationPoint();
                return loc != null ? loc.speedKmh() : null;
            });
    }
}
