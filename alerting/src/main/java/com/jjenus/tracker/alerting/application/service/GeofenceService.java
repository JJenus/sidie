package com.jjenus.tracker.alerting.application.service;


import com.jjenus.tracker.alerting.api.dto.GeofencePointDto;
import com.jjenus.tracker.alerting.api.dto.GeofenceResponse;
import com.jjenus.tracker.alerting.api.dto.PagedResponse;
import com.jjenus.tracker.alerting.api.dto.SearchRequest;
import com.jjenus.tracker.alerting.domain.entity.Geofence;
import com.jjenus.tracker.alerting.domain.entity.GeofencePoint;
import com.jjenus.tracker.alerting.infrastructure.cache.GeofenceCacheService;
import com.jjenus.tracker.alerting.infrastructure.cache.RedisKeyGenerator;
import com.jjenus.tracker.alerting.infrastructure.repository.GeofenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GeofenceService {

    private static final Logger logger = LoggerFactory.getLogger(GeofenceService.class);

    private final GeofenceRepository geofenceRepository;
    private final GeofenceCacheService geofenceCacheService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisKeyGenerator keyGenerator;

    public GeofenceService(
            GeofenceRepository geofenceRepository,
            GeofenceCacheService geofenceCacheService,
            RedisTemplate<String, Object> redisTemplate,
            RedisKeyGenerator keyGenerator) {
        this.geofenceRepository = geofenceRepository;
        this.geofenceCacheService = geofenceCacheService;
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
    }

    @Transactional
    public Geofence createGeofence(Geofence geofence) {
        Geofence saved = geofenceRepository.save(geofence);

        // Cache the geofence
        geofenceCacheService.cacheGeofence(saved);

        // Invalidate pagination cache
        invalidatePaginationCache();

        logger.info("Created geofence: {} for {} vehicles",
                saved.getName(), saved.getVehicleIds().size());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Geofence> getVehicleGeofences(String vehicleId) {
        // Try cache first
        List<Geofence> cached = geofenceCacheService.getVehicleGeofences(vehicleId);
        if (cached != null) {
            logger.debug("Cache hit for vehicle geofences: {}", vehicleId);
            return cached;
        }

        // Cache miss - load from DB
        List<Geofence> geofences = geofenceRepository.findByVehicleId(vehicleId);

        // Cache the result
        geofenceCacheService.cacheVehicleGeofences(vehicleId, geofences);

        return geofences;
    }

    @Transactional(readOnly = true)
    public PagedResponse<GeofenceResponse> getVehicleGeofencesPaged(String vehicleId, SearchRequest searchRequest) {
        String cacheKey = keyGenerator.getPaginatedGeofencesKey(
                searchRequest.getPage(),
                searchRequest.getSize(),
                searchRequest.getSortBy(),
                searchRequest.getSortDirection().name(),
                searchRequest.getSearch(),
                vehicleId,
                null // Don't filter by active status
        );

        try {
            // Try cache first
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && cached instanceof PagedResponse) {
                logger.debug("Cache hit for paginated vehicle geofences");
                return (PagedResponse<GeofenceResponse>) cached;
            }
        } catch (Exception e) {
            logger.warn("Failed to get paginated vehicle geofences from cache", e);
        }

        // Cache miss - query database
        Pageable pageable = createPageable(searchRequest);
        Page<Geofence> page = geofenceRepository.findByVehicleId(vehicleId, pageable);

        PagedResponse<GeofenceResponse> response = new PagedResponse<>(page.map(this::toResponse));

        // Cache the result
        try {
            redisTemplate.opsForValue().set(cacheKey, response,
                    RedisKeyGenerator.PAGINATION_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Failed to cache paginated vehicle geofences", e);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<Geofence> getActiveGeofences(String vehicleId) {
        return geofenceRepository.findByVehicleIdAndIsActive(vehicleId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<GeofenceResponse> getActiveGeofencesPaged(String vehicleId, SearchRequest searchRequest) {
        String cacheKey = keyGenerator.getPaginatedGeofencesKey(
                searchRequest.getPage(),
                searchRequest.getSize(),
                searchRequest.getSortBy(),
                searchRequest.getSortDirection().name(),
                searchRequest.getSearch(),
                vehicleId,
                true // Filter by active=true
        );

        try {
            // Try cache first
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && cached instanceof PagedResponse) {
                logger.debug("Cache hit for paginated active vehicle geofences");
                return (PagedResponse<GeofenceResponse>) cached;
            }
        } catch (Exception e) {
            logger.warn("Failed to get paginated active vehicle geofences from cache", e);
        }

        // Cache miss - query database
        Pageable pageable = createPageable(searchRequest);
        Page<Geofence> page = geofenceRepository.findByVehicleIdAndIsActive(vehicleId, pageable);

        PagedResponse<GeofenceResponse> response = new PagedResponse<>(page.map(this::toResponse));

        // Cache the result
        try {
            redisTemplate.opsForValue().set(cacheKey, response,
                    RedisKeyGenerator.PAGINATION_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Failed to cache paginated active vehicle geofences", e);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public PagedResponse<GeofenceResponse> searchGeofences(SearchRequest searchRequest) {
        String cacheKey = keyGenerator.getPaginatedGeofencesKey(
                searchRequest.getPage(),
                searchRequest.getSize(),
                searchRequest.getSortBy(),
                searchRequest.getSortDirection().name(),
                searchRequest.getSearch(),
                searchRequest.getVehicleId(),
                searchRequest.getActive()
        );

        try {
            // Try cache first
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && cached instanceof PagedResponse) {
                logger.debug("Cache hit for search geofences");
                return (PagedResponse<GeofenceResponse>) cached;
            }
        } catch (Exception e) {
            logger.warn("Failed to get search geofences from cache", e);
        }

        // Cache miss - query database
        Pageable pageable = createPageable(searchRequest);
        Page<Geofence> page = geofenceRepository.searchGeofences(
                searchRequest.getVehicleId(),
                searchRequest.getSearch(),
                searchRequest.getActive(),
                pageable);

        PagedResponse<GeofenceResponse> response = new PagedResponse<>(page.map(this::toResponse));

        // Cache the result
        try {
            redisTemplate.opsForValue().set(cacheKey, response,
                    RedisKeyGenerator.PAGINATION_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Failed to cache search geofences", e);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public Geofence getGeofenceById(Long geofenceId) {
        // Try cache first
        return geofenceCacheService.getGeofenceById(geofenceId)
                .orElseGet(() -> {
                    // Cache miss - load from DB
                    Geofence geofence = geofenceRepository.findById(geofenceId)
                            .orElseThrow(() -> new IllegalArgumentException("Geofence not found"));

                    // Cache for future requests
                    geofenceCacheService.cacheGeofence(geofence);

                    return geofence;
                });
    }

    @Transactional
    public Geofence updateGeofence(Long geofenceId, Geofence updates) {
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found"));

        // Track affected vehicles for cache invalidation
        Set<String> oldVehicleIds = geofence.getVehicleIds();
        Set<String> newVehicleIds = updates.getVehicleIds() != null ?
                updates.getVehicleIds() : oldVehicleIds;

        // Update fields
        if (updates.getName() != null) geofence.setName(updates.getName());
        if (updates.getDescription() != null) geofence.setDescription(updates.getDescription());
        if (updates.getIsActive() != null) geofence.setIsActive(updates.getIsActive());
        if (updates.getShapeType() != null) geofence.setShapeType(updates.getShapeType());

        // Update geometry if changed
        if (updates.getCenterLatitude() != null) {
            geofence.setCenterLatitude(updates.getCenterLatitude());
        }

        if (updates.getCenterLongitude() != null) {
            geofence.setCenterLongitude(updates.getCenterLongitude());
        }

        if (updates.getRadiusMeters() != null) {
            geofence.setRadiusMeters(updates.getRadiusMeters());
        }

        // Update vehicle associations
        if (updates.getVehicleIds() != null) {
            geofence.setVehicleIds(updates.getVehicleIds());
        }

        Geofence updated = geofenceRepository.save(geofence);

        // Update cache
        geofenceCacheService.cacheGeofence(updated);

        // Invalidate caches for all affected vehicles
        Set<String> allAffected = oldVehicleIds;
        allAffected.addAll(newVehicleIds);
        geofenceCacheService.invalidateVehicleGeofenceCaches(allAffected);

        // Invalidate pagination cache
        invalidatePaginationCache();

        logger.info("Updated geofence: {} affecting {} vehicles",
                geofenceId, allAffected.size());
        return updated;
    }

    @Transactional
    public void deleteGeofence(Long geofenceId) {
        Geofence geofence = geofenceRepository.findById(geofenceId)
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found"));

        Set<String> affectedVehicles = geofence.getVehicleIds();
        geofenceRepository.deleteById(geofenceId);

        // Invalidate caches
        geofenceCacheService.invalidateGeofence(geofenceId);
        geofenceCacheService.invalidateVehicleGeofenceCaches(affectedVehicles);

        // Invalidate pagination cache
        invalidatePaginationCache();

        logger.info("Deleted geofence: {} affecting {} vehicles",
                geofenceId, affectedVehicles.size());
    }

    @Transactional(readOnly = true)
    public List<Geofence> findNearbyGeofencesForVehicle(String vehicleId,
                                                        Double latitude,
                                                        Double longitude) {
        return geofenceRepository.findNearbyGeofencesForVehicle(vehicleId, latitude, longitude);
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

    @Transactional(readOnly = true)
    public boolean existsAndActive(Long geofenceId) {
        return geofenceRepository.findById(geofenceId)
                .map(g -> Boolean.TRUE.equals(g.getIsActive()))
                .orElse(false);
    }

    // ========== HELPER METHODS ==========

    private Pageable createPageable(SearchRequest searchRequest) {
        Sort sort = Sort.by(searchRequest.getSortDirection(), searchRequest.getSortBy());
        return PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), sort);
    }

    private void invalidatePaginationCache() {
        try {
            Set<String> keys = redisTemplate.keys(keyGenerator.getPaginatedGeofencesPattern());
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.debug("Invalidated pagination cache for geofences");
            }
        } catch (Exception e) {
            logger.warn("Failed to invalidate pagination cache", e);
        }
    }

    private GeofenceResponse toResponse(Geofence geofence) {
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

        // Convert points
        if (geofence.getPoints() != null && !geofence.getPoints().isEmpty()) {
            response.setPoints(geofence.getPoints().stream()
                    .map(point -> {
                        GeofencePointDto dto =
                                new GeofencePointDto();
                        dto.setLatitude(point.getLatitude());
                        dto.setLongitude(point.getLongitude());
                        return dto;
                    })
                    .collect(Collectors.toList()));
        }

        return response;
    }
}