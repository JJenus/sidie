// ========== GeofenceQueryService.java ==========
package com.jjenus.tracker.alerting.application.service;

import com.jjenus.tracker.alerting.api.dto.GeofenceResponse;
import com.jjenus.tracker.alerting.api.dto.PagedResponse;
import com.jjenus.tracker.alerting.api.dto.SearchRequest;
import com.jjenus.tracker.alerting.domain.entity.Geofence;
import com.jjenus.tracker.alerting.infrastructure.repository.GeofenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GeofenceQueryService {

    private static final Logger logger = LoggerFactory.getLogger(GeofenceQueryService.class);

    private final GeofenceRepository geofenceRepository;

    public GeofenceQueryService(GeofenceRepository geofenceRepository) {
        this.geofenceRepository = geofenceRepository;
    }

    @Cacheable(value = "geofences", key = "#geofenceId")
    public Geofence getGeofenceById(Long geofenceId) {
        return geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found"));
    }

    @Cacheable(value = "geofences", key = "'vehicle_' + #vehicleId + '_all'")
    public List<Geofence> getVehicleGeofences(String vehicleId) {
        return geofenceRepository.findByVehicleId(vehicleId);
    }

    @Cacheable(value = "geofences", key = "'vehicle_' + #vehicleId + '_active'")
    public List<Geofence> getActiveGeofences(String vehicleId) {
        return geofenceRepository.findByVehicleIdAndIsActive(vehicleId);
    }

    @Cacheable(value = "geofencesPaged", key = "'vehiclePaged_' + #vehicleId + '_' + #searchRequest.hashCode()")
    public PagedResponse<GeofenceResponse> getVehicleGeofencesPaged(String vehicleId, SearchRequest searchRequest) {
        Pageable pageable = createPageable(searchRequest);
        Page<Geofence> page = geofenceRepository.findByVehicleId(vehicleId, pageable);
        return new PagedResponse<>(page.map(this::toResponse));
    }

    @Cacheable(value = "geofencesPaged", key = "'activeVehiclePaged_' + #vehicleId + '_' + #searchRequest.hashCode()")
    public PagedResponse<GeofenceResponse> getActiveGeofencesPaged(String vehicleId, SearchRequest searchRequest) {
        Pageable pageable = createPageable(searchRequest);
        Page<Geofence> page = geofenceRepository.findByVehicleIdAndIsActive(vehicleId, pageable);
        return new PagedResponse<>(page.map(this::toResponse));
    }

    @Cacheable(value = "geofencesPaged", key = "'search_' + #searchRequest.hashCode()")
    public PagedResponse<GeofenceResponse> searchGeofences(SearchRequest searchRequest) {
        Pageable pageable = createPageable(searchRequest);
        Page<Geofence> page = geofenceRepository.searchGeofences(
                searchRequest.getVehicleId(),
                searchRequest.getSearch(),
                searchRequest.getActive(),
                pageable);
        return new PagedResponse<>(page.map(this::toResponse));
    }

    @Cacheable(value = "geofences", key = "'nearby_' + #vehicleId + '_' + #latitude + '_' + #longitude")
    public List<Geofence> findNearbyGeofencesForVehicle(String vehicleId, Double latitude, Double longitude) {
        return geofenceRepository.findNearbyGeofencesForVehicle(vehicleId, latitude, longitude);
    }

    @Cacheable(value = "geofenceStats", key = "'exists_' + #geofenceId")
    public boolean existsAndActive(Long geofenceId) {
        return geofenceRepository.findById(geofenceId)
                .map(g -> Boolean.TRUE.equals(g.getIsActive()))
                .orElse(false);
    }

    public void checkGeofenceViolations(String vehicleId, Double latitude, Double longitude) {
        List<Geofence> nearbyGeofences = findNearbyGeofencesForVehicle(vehicleId, latitude, longitude);

        for (Geofence geofence : nearbyGeofences) {
            boolean isInside = geofence.isPointInside(latitude, longitude);
            logger.debug("Vehicle {} is {} geofence {} at [{}, {}]",
                    vehicleId,
                    isInside ? "inside" : "outside",
                    geofence.getName(),
                    latitude, longitude);
        }
    }

    private Pageable createPageable(SearchRequest searchRequest) {
        Sort sort = Sort.by(searchRequest.getSortDirection(), searchRequest.getSortBy());
        return PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), sort);
    }

    public GeofenceResponse toResponse(Geofence geofence) {
        GeofenceResponse response = new GeofenceResponse();
        response.setGeofenceId(geofence.getGeofenceId());
        response.setVehicleId(geofence.getVehicleIds().stream().findFirst().orElse(null));
        response.setName(geofence.getName());
        response.setDescription(geofence.getDescription());
        response.setShapeType(geofence.getShapeType());
        response.setCenterLatitude(geofence.getCenterLatitude());
        response.setCenterLongitude(geofence.getCenterLongitude());
        response.setRadiusMeters(geofence.getRadiusMeters());
        response.setActive(Boolean.TRUE.equals(geofence.getIsActive()));
        response.setCreatedAt(geofence.getCreatedAt());
        response.setUpdatedAt(geofence.getUpdatedAt());

        if (geofence.getPoints() != null && !geofence.getPoints().isEmpty()) {
            response.setPoints(geofence.getPoints().stream()
                    .map(point -> {
                        com.jjenus.tracker.alerting.api.dto.GeofencePointDto dto =
                                new com.jjenus.tracker.alerting.api.dto.GeofencePointDto();
                        dto.setLatitude(point.getLatitude());
                        dto.setLongitude(point.getLongitude());
                        return dto;
                    })
                    .collect(Collectors.toList()));
        }

        return response;
    }
}