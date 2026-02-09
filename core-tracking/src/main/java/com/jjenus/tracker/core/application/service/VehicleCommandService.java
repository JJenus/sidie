package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.api.dto.VehicleRequest;
import com.jjenus.tracker.core.api.dto.VehicleResponse;
import com.jjenus.tracker.core.api.dto.VehicleUpdateRequest;
import com.jjenus.tracker.core.domain.entity.Vehicle;
import com.jjenus.tracker.core.exception.VehicleException;
import com.jjenus.tracker.core.infrastructure.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class VehicleCommandService {

    private final VehicleRepository vehicleRepository;
    private final VehicleQueryService vehicleQueryService;
    private final ModelMapper modelMapper;

    @Caching(evict = {
            @CacheEvict(value = "vehicles", key = "#request.vehicleId"),
            @CacheEvict(value = "vehicles", key = "'byDevice_' + #request.deviceId", condition = "#request.deviceId != null"),
            @CacheEvict(value = "vehicles", key = "'search_*'"),
            @CacheEvict(value = "vehicleStats", allEntries = true)
    })
    public VehicleResponse createVehicle(VehicleRequest request) {
        log.info("Creating vehicle: {}", request.getVehicleId());

        validateVehicleCreation(request);

        Vehicle vehicle = modelMapper.map(request, Vehicle.class);
        Vehicle saved = vehicleRepository.save(vehicle);

        log.info("Vehicle created: {}", saved.getVehicleId());
        return vehicleQueryService.getVehicle(saved.getVehicleId());
    }

    @Caching(evict = {
            @CacheEvict(value = "vehicles", key = "#vehicleId"),
            @CacheEvict(value = "vehicles", key = "'byDevice_*'", condition = "#request.deviceId != null"),
            @CacheEvict(value = "vehicles", key = "'search_*'"),
            @CacheEvict(value = "vehicleStats", allEntries = true)
    })
    public VehicleResponse updateVehicle(String vehicleId, VehicleUpdateRequest request) {
        log.info("Updating vehicle: {}", vehicleId);

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> VehicleException.notFound(vehicleId));

        if (request.getModel() != null) {
            vehicle.setModel(request.getModel());
        }

        if (request.getLicensePlate() != null) {
            vehicle.setLicensePlate(request.getLicensePlate());
        }

        if (request.getVin() != null) {
            vehicle.setVin(request.getVin());
        }

        if (request.getFuelLevel() != null) {
            vehicle.setFuelLevel(request.getFuelLevel());
        }

        if (request.getOdometerKm() != null) {
            vehicle.setOdometerKm(request.getOdometerKm());
        }

        if (request.getFuelCutActive() != null) {
            vehicle.setFuelCutActive(request.getFuelCutActive());
        }

        Vehicle saved = vehicleRepository.save(vehicle);

        log.info("Vehicle updated: {}", saved.getVehicleId());
        return vehicleQueryService.getVehicle(saved.getVehicleId());
    }

    @Caching(evict = {
            @CacheEvict(value = "vehicles", key = "#vehicleId"),
            @CacheEvict(value = "vehicles", key = "'byDevice_*'"),
            @CacheEvict(value = "vehicles", key = "'search_*'"),
            @CacheEvict(value = "vehicleStats", allEntries = true)
    })
    public void deleteVehicle(String vehicleId) {
        log.info("Deleting vehicle: {}", vehicleId);

        if (!vehicleRepository.existsById(vehicleId)) {
            throw VehicleException.notFound(vehicleId);
        }

        vehicleRepository.deleteById(vehicleId);
        log.info("Vehicle deleted: {}", vehicleId);
    }

    @Caching(evict = {
            @CacheEvict(value = "vehicles", key = "#vehicleId"),
            @CacheEvict(value = "vehicleStats", allEntries = true)
    })
    public void updateVehicleStatus(String vehicleId, String deviceId, boolean online) {
        log.info("Updating vehicle {} status to online={}", vehicleId, online);

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> VehicleException.notFound(vehicleId));

        // Update logic here

        vehicleRepository.save(vehicle);
    }

    private void validateVehicleCreation(VehicleRequest request) {
        if (vehicleRepository.existsById(request.getVehicleId())) {
            throw new IllegalArgumentException("Vehicle with ID " + request.getVehicleId() + " already exists");
        }

        if (request.getDeviceId() != null && vehicleRepository.existsByDeviceId(request.getDeviceId())) {
            throw VehicleException.deviceAlreadyAssigned(request.getDeviceId());
        }

        if (request.getLicensePlate() != null && vehicleRepository.existsByLicensePlate(request.getLicensePlate())) {
            throw new IllegalArgumentException("Vehicle with license plate " + request.getLicensePlate() + " already exists");
        }
    }
}