package com.jjenus.tracker.alerting.application.service;

import com.jjenus.tracker.alerting.domain.entity.Geofence;
import com.jjenus.tracker.alerting.infrastructure.repository.GeofenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional
public class GeofenceCommandService {

    private static final Logger logger = LoggerFactory.getLogger(GeofenceCommandService.class);

    private final GeofenceRepository geofenceRepository;
    private final GeofenceQueryService geofenceQueryService;

    public GeofenceCommandService(GeofenceRepository geofenceRepository, GeofenceQueryService geofenceQueryService) {
        this.geofenceRepository = geofenceRepository;
        this.geofenceQueryService = geofenceQueryService;
    }

    @Caching(evict = {
        @CacheEvict(value = "geofences", key = "'vehicle_*'"),
        @CacheEvict(value = "geofencesPaged", allEntries = true),
        @CacheEvict(value = "geofenceStats", key = "'exists_*'")
    })
    public Geofence createGeofence(Geofence geofence) {
        Geofence saved = geofenceRepository.save(geofence);
        logger.info("Created geofence: {} for {} vehicles",
                saved.getName(), saved.getVehicleIds().size());
        return saved;
    }

    @Caching(
        put = @CachePut(value = "geofences", key = "#geofenceId"),
        evict = {
            @CacheEvict(value = "geofences", key = "'vehicle_*'"),
            @CacheEvict(value = "geofencesPaged", allEntries = true),
            @CacheEvict(value = "geofenceStats", key = "'exists_' + #geofenceId"),
            @CacheEvict(value = "geofences", key = "'nearby_*'", condition = "#oldVehicleIds != null")
        }
    )
    public Geofence updateGeofence(Long geofenceId, Geofence updates) {
        Geofence geofence = geofenceQueryService.getGeofenceById(geofenceId);

        // Track affected vehicles for cache invalidation
        Set<String> oldVehicleIds = geofence.getVehicleIds();
        Set<String> newVehicleIds = updates.getVehicleIds() != null ?
                updates.getVehicleIds() : oldVehicleIds;

        // Update fields
        if (updates.getName() != null) geofence.setName(updates.getName());
        if (updates.getDescription() != null) geofence.setDescription(updates.getDescription());
        if (updates.getIsActive() != null) geofence.setIsActive(updates.getIsActive());
        if (updates.getShapeType() != null) geofence.setShapeType(updates.getShapeType());

        if (updates.getCenterLatitude() != null) {
            geofence.setCenterLatitude(updates.getCenterLatitude());
        }

        if (updates.getCenterLongitude() != null) {
            geofence.setCenterLongitude(updates.getCenterLongitude());
        }

        if (updates.getRadiusMeters() != null) {
            geofence.setRadiusMeters(updates.getRadiusMeters());
        }

        if (updates.getVehicleIds() != null) {
            geofence.setVehicleIds(updates.getVehicleIds());
        }

        Geofence updated = geofenceRepository.save(geofence);

        logger.info("Updated geofence: {} affecting {} vehicles",
                geofenceId, oldVehicleIds.size() + newVehicleIds.size());
        return updated;
    }

    @Caching(
        evict = {
            @CacheEvict(value = "geofences", key = "#geofenceId"),
            @CacheEvict(value = "geofences", key = "'vehicle_*'"),
            @CacheEvict(value = "geofencesPaged", allEntries = true),
            @CacheEvict(value = "geofenceStats", key = "'exists_' + #geofenceId"),
            @CacheEvict(value = "geofences", key = "'nearby_*'")
        }
    )
    public void deleteGeofence(Long geofenceId) {
        Geofence geofence = geofenceQueryService.getGeofenceById(geofenceId);
        geofenceRepository.deleteById(geofenceId);

        logger.info("Deleted geofence: {} affecting {} vehicles",
                geofenceId, geofence.getVehicleIds().size());
    }

    @CacheEvict(value = "geofences", key = "'nearby_' + #vehicleId + '_*'")
    public void checkGeofenceViolations(String vehicleId, Double latitude, Double longitude) {
        geofenceQueryService.checkGeofenceViolations(vehicleId, latitude, longitude);
    }
}