package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.*;
import com.jjenus.tracker.core.domain.entity.Vehicle;
import com.jjenus.tracker.core.exception.VehicleException;
import com.jjenus.tracker.core.infrastructure.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VehicleQueryService {

    private final VehicleRepository vehicleRepository;
    private final ModelMapper modelMapper;

    @Cacheable(value = "vehicles", key = "#vehicleId", unless = "#result == null")
    public VehicleResponse getVehicle(String vehicleId) {
        log.debug("Cache miss - Fetching vehicle: {}", vehicleId);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> VehicleException.notFound(vehicleId));
        return toResponse(vehicle);
    }

    @Cacheable(value = "vehicles", key = "'byDevice_' + #deviceId", unless = "#result == null")
    public VehicleResponse getVehicleByDeviceId(String deviceId) {
        log.debug("Cache miss - Fetching vehicle by device: {}", deviceId);
        Vehicle vehicle = vehicleRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> VehicleException.notFound("Device: " + deviceId));
        return toResponse(vehicle);
    }

    @Cacheable(value = "vehicles", key = "'search_' + #request.hashCode()")
    public PagedResponse<VehicleResponse> searchVehicles(VehicleSearchRequest request) {
        log.debug("Cache miss - Searching vehicles: {}", request);

        Specification<Vehicle> spec = Specification.where(null);

        if (StringUtils.hasText(request.getSearch())) {
            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("vehicleId")), "%" + request.getSearch().toLowerCase() + "%"),
                            cb.like(cb.lower(root.get("licensePlate")), "%" + request.getSearch().toLowerCase() + "%"),
                            cb.like(cb.lower(root.get("model")), "%" + request.getSearch().toLowerCase() + "%"),
                            cb.like(cb.lower(root.get("vin")), "%" + request.getSearch().toLowerCase() + "%")
                    )
            );
        }

        if (StringUtils.hasText(request.getEngineState())) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("engineState"), request.getEngineState())
            );
        }

        if (request.getAccStatus() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("accStatus"), request.getAccStatus())
            );
        }

        if (request.getFuelCutActive() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("fuelCutActive"), request.getFuelCutActive())
            );
        }

        if (request.getLastTelemetryBefore() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThan(root.get("lastTelemetryTime"), request.getLastTelemetryBefore())
            );
        }

        if (request.getLastTelemetryAfter() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThan(root.get("lastTelemetryTime"), request.getLastTelemetryAfter())
            );
        }

        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                Sort.by(request.getSortDirection(), request.getSortBy())
        );

        Page<Vehicle> page = vehicleRepository.findAll(spec, pageable);
        return new PagedResponse<>(page.map(this::toResponse));
    }

    @Cacheable(value = "vehicleStats", key = "'activeTrips'")
    public List<VehicleResponse> getVehiclesWithActiveTrips() {
        log.debug("Cache miss - Fetching vehicles with active trips");
        return vehicleRepository.findVehiclesWithActiveTrips().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "vehicleStats", key = "'fuelCutActive'")
    public List<VehicleResponse> getVehiclesWithActiveFuelCut() {
        log.debug("Cache miss - Fetching vehicles with active fuel cut");
        return vehicleRepository.findVehiclesWithActiveFuelCut().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "vehicleStats", key = "'staleTelemetry_' + #cutoffTime")
    public List<VehicleResponse> getVehiclesWithStaleTelemetry(Instant cutoffTime) {
        log.debug("Cache miss - Fetching vehicles with stale telemetry");
        return vehicleRepository.findVehiclesWithStaleTelemetry(cutoffTime).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "vehicles", key = "'exists_' + #vehicleId")
    public boolean exists(String vehicleId) {
        return vehicleRepository.existsById(vehicleId);
    }

    @Cacheable(value = "vehicles", key = "'deviceExists_' + #deviceId")
    public boolean deviceExists(String deviceId) {
        return vehicleRepository.existsByDeviceId(deviceId);
    }

    @Cacheable(value = "vehicles", key = "'plateExists_' + #licensePlate")
    public boolean licensePlateExists(String licensePlate) {
        return vehicleRepository.existsByLicensePlate(licensePlate);
    }

    private VehicleResponse toResponse(Vehicle vehicle) {
        VehicleResponse response = modelMapper.map(vehicle, VehicleResponse.class);

        // Map current location if present
        if (vehicle.getCurrentLocation() != null) {
            response.setCurrentLocation(modelMapper.map(vehicle.getCurrentLocation(), LocationResponse.class));
        }

        return response;
    }
}