
package com.jjenus.tracker.alerting.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.api.dto.*;
        import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.domain.entity.Geofence;
import com.jjenus.tracker.alerting.domain.enums.AlertRuleType;
import com.jjenus.tracker.alerting.exception.AlertException;
import com.jjenus.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class AlertRuleCommandService {

    private static final Logger logger = LoggerFactory.getLogger(AlertRuleCommandService.class);

    private final AlertRuleRepository ruleRepository;
    private final AlertRuleQueryService ruleQueryService;
    private final GeofenceRuleValidator geofenceRuleValidator;
    private final ObjectMapper objectMapper;

    public AlertRuleCommandService(
            AlertRuleRepository ruleRepository,
            AlertRuleQueryService ruleQueryService,
            GeofenceRuleValidator geofenceRuleValidator,
            ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.ruleQueryService = ruleQueryService;
        this.geofenceRuleValidator = geofenceRuleValidator;
        this.objectMapper = objectMapper;
    }

    // ========== TEMPLATE METHODS ==========

    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public AlertRuleResponse createOverspeedRule(OverspeedRuleTemplateRequest request) {
        logger.info("Creating overspeed rule: {}", request.getRuleKey());

        validateRuleKeyUniqueness(request.getRuleKey());
        validateOverspeedRuleRequest(request);

        Map<String, Object> parameters = buildOverspeedParameters(request);
        String actionsJson = buildOverspeedActions();

        AlertRule rule = createBaseRule(request, AlertRuleType.SPEED, parameters);
        rule.setActions(actionsJson);
        rule.setVehicleIds(request.getVehicleIds());

        AlertRule saved = saveRule(rule);

        logger.info("Overspeed rule created successfully: {} for {} vehicles",
                saved.getRuleKey(), request.getVehicleIds().size());

        return ruleQueryService.toResponse(saved);
    }

    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public AlertRuleResponse createIdleTimeoutRule(IdleTimeoutRuleTemplateRequest request) {
        logger.info("Creating idle timeout rule: {}", request.getRuleKey());

        validateRuleKeyUniqueness(request.getRuleKey());
        validateIdleTimeoutRuleRequest(request);

        Map<String, Object> parameters = buildIdleTimeoutParameters(request);
        AlertRule rule = createBaseRule(request, AlertRuleType.TIME, parameters);
        rule.setVehicleIds(request.getVehicleIds());

        AlertRule saved = saveRule(rule);

        logger.info("Idle timeout rule created successfully: {} for {} vehicles",
                saved.getRuleKey(), request.getVehicleIds().size());

        return ruleQueryService.toResponse(saved);
    }

    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public AlertRuleResponse createGeofenceRule(GeofenceRuleTemplateRequest request) {
        logger.info("Creating geofence rule: {}", request.getRuleKey());

        validateRuleKeyUniqueness(request.getRuleKey());
        geofenceRuleValidator.validateGeofenceRuleRequest(request);

        Geofence geofence = geofenceRuleValidator.getValidatedGeofence(request.getGeofenceId());
        Map<String, Object> parameters = buildGeofenceParameters(request, geofence);
        validateVehicleGeofenceAssociation(geofence, request.getVehicleIds());

        AlertRule rule = createBaseRule(request, AlertRuleType.GEOFENCE, parameters);
        rule.setVehicleIds(request.getVehicleIds());

        AlertRule saved = saveRule(rule);

        logger.info("Geofence rule created successfully: {} for geofence {} and {} vehicles",
                saved.getRuleKey(), request.getGeofenceId(), request.getVehicleIds().size());

        return ruleQueryService.toResponse(saved);
    }

    // ========== CRUD METHODS ==========

    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public AlertRuleResponse createRule(CreateAlertRuleRequest request) {
        logger.info("Creating custom alert rule: {}", request.getRuleKey());

        validateRuleKeyUniqueness(request.getRuleKey());

        AlertRuleType ruleType = ruleQueryService.parseRuleType(request.getRuleType());
        Map<String, Object> parameters = parseAndValidateParameters(request.getParameters(), ruleType);
        Set<String> vehicleIds = extractVehicleIdsFromParameters(parameters);

        AlertRule rule = new AlertRule();
        rule.setRuleKey(request.getRuleKey());
        rule.setRuleName(request.getRuleName());
        rule.setRuleType(ruleType);
        rule.setParameters(parameters);
        rule.setPriority(request.getPriority());
        rule.setIsEnabled(request.isEnabled());
        rule.setVehicleIds(vehicleIds);
        rule.setCooldownMinutes(5);

        AlertRule saved = saveRule(rule);
        logger.info("Custom alert rule created successfully: {}", saved.getRuleKey());

        return ruleQueryService.toResponse(saved);
    }

    @Caching(
            put = @CachePut(value = "alertRules", key = "'rule_' + #ruleKey"),
            evict = {
                    @CacheEvict(value = "alertRules", key = "'all'"),
                    @CacheEvict(value = "alertRules", key = "'enabled'"),
                    @CacheEvict(value = "alertRules", key = "'vehicle_*'"),
                    @CacheEvict(value = "alertRulesPaged", allEntries = true),
                    @CacheEvict(value = "vehicleRules", allEntries = true)
            }
    )
    public AlertRuleResponse updateRule(String ruleKey, UpdateAlertRuleRequest request) {
        logger.info("Updating alert rule: {}", ruleKey);

        AlertRule rule = ruleQueryService.getRuleEntityByKey(ruleKey);

        boolean hasChanges = false;
        Set<String> oldVehicleIds = new HashSet<>(rule.getVehicleIds());

        if (StringUtils.hasText(request.getRuleName())) {
            rule.setRuleName(request.getRuleName());
            hasChanges = true;
        }

        if (StringUtils.hasText(request.getRuleKey()) && !request.getRuleKey().equals(ruleKey)) {
            validateRuleKeyUniqueness(request.getRuleKey());
            rule.setRuleKey(request.getRuleKey());
            hasChanges = true;
        }

        if (request.getParameters() != null && !request.getParameters().isEmpty()) {
            Map<String, Object> existingParams = rule.getParameters();
            existingParams.putAll(request.getParameters());
            validateRuleParameters(rule.getRuleType(), existingParams);
            rule.setParameters(existingParams);

            Set<String> newVehicleIds = extractVehicleIdsFromParameters(existingParams);
            rule.setVehicleIds(newVehicleIds);
            hasChanges = true;
        }

        if (request.getPriority() > 0) {
            rule.setPriority(request.getPriority());
            hasChanges = true;
        }

        if (hasChanges) {
            rule.setUpdatedAt(Instant.now());
            AlertRule updated = ruleRepository.save(rule);

            logger.info("Alert rule updated successfully: {}", ruleKey);
            return ruleQueryService.toResponse(updated);
        }

        logger.debug("No changes detected for rule: {}", ruleKey);
        return ruleQueryService.toResponse(rule);
    }

    @Caching(
            put = @CachePut(value = "alertRules", key = "'rule_' + #ruleKey"),
            evict = {
                    @CacheEvict(value = "alertRules", key = "'all'"),
                    @CacheEvict(value = "alertRules", key = "'enabled'"),
                    @CacheEvict(value = "alertRules", key = "'vehicle_*'"),
                    @CacheEvict(value = "alertRulesPaged", allEntries = true),
                    @CacheEvict(value = "vehicleRules", allEntries = true)
            }
    )
    public void enableRule(String ruleKey) {
        logger.info("Enabling alert rule: {}", ruleKey);

        AlertRule rule = ruleQueryService.getRuleEntityByKey(ruleKey);

        if (!Boolean.TRUE.equals(rule.isEnabled())) {
            rule.setIsEnabled(true);
            rule.setUpdatedAt(Instant.now());
            ruleRepository.save(rule);

            logger.info("Alert rule enabled: {}", ruleKey);
        } else {
            logger.debug("Rule already enabled: {}", ruleKey);
        }
    }

    @Caching(
            evict = {
                    @CacheEvict(value = "alertRules", key = "'rule_' + #ruleKey"),
                    @CacheEvict(value = "alertRules", key = "'all'"),
                    @CacheEvict(value = "alertRules", key = "'enabled'"),
                    @CacheEvict(value = "alertRules", key = "'vehicle_*'"),
                    @CacheEvict(value = "alertRulesPaged", allEntries = true),
                    @CacheEvict(value = "vehicleRules", allEntries = true)
            }
    )
    public void disableRule(String ruleKey) {
        logger.info("Disabling alert rule: {}", ruleKey);

        AlertRule rule = ruleQueryService.getRuleEntityByKey(ruleKey);

        if (Boolean.TRUE.equals(rule.isEnabled())) {
            rule.setIsEnabled(false);
            rule.setUpdatedAt(Instant.now());
            ruleRepository.save(rule);

            logger.info("Alert rule disabled: {}", ruleKey);
        } else {
            logger.debug("Rule already disabled: {}", ruleKey);
        }
    }

    @Caching(
            evict = {
                    @CacheEvict(value = "alertRules", key = "'rule_' + #ruleKey"),
                    @CacheEvict(value = "alertRules", key = "'all'"),
                    @CacheEvict(value = "alertRules", key = "'enabled'"),
                    @CacheEvict(value = "alertRules", key = "'vehicle_*'"),
                    @CacheEvict(value = "alertRulesPaged", allEntries = true),
                    @CacheEvict(value = "vehicleRules", allEntries = true)
            }
    )
    public void deleteRule(String ruleKey) {
        logger.info("Deleting alert rule: {}", ruleKey);

        AlertRule rule = ruleQueryService.getRuleEntityByKey(ruleKey);
        ruleRepository.deleteByRuleKey(ruleKey);

        logger.info("Alert rule deleted successfully: {}", ruleKey);
    }

    // ========== BATCH OPERATIONS ==========

    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public List<AlertRuleResponse> batchCreateRules(List<CreateAlertRuleRequest> requests) {
        logger.info("Batch creating {} alert rules", requests.size());

        List<AlertRuleResponse> responses = new ArrayList<>();

        for (CreateAlertRuleRequest request : requests) {
            try {
                AlertRuleResponse response = createRule(request);
                responses.add(response);
            } catch (Exception e) {
                logger.error("Failed to create rule {} in batch: {}",
                        request.getRuleKey(), e.getMessage());
            }
        }

        logger.info("Batch creation completed: {} successful, {} total",
                responses.size(), requests.size());

        return responses;
    }

    @CacheEvict(value = {"alertRules", "alertRulesPaged", "vehicleRules"}, allEntries = true)
    public void batchEnableRules(Set<String> ruleKeys) {
        logger.info("Batch enabling {} alert rules", ruleKeys.size());

        int enabledCount = 0;
        for (String ruleKey : ruleKeys) {
            try {
                enableRule(ruleKey);
                enabledCount++;
            } catch (Exception e) {
                logger.error("Failed to enable rule {} in batch: {}", ruleKey, e.getMessage());
            }
        }

        logger.info("Batch enabling completed: {} enabled, {} total",
                enabledCount, ruleKeys.size());
    }

    // ========== HELPER METHODS ==========

    private AlertRule saveRule(AlertRule rule) {
        Instant now = Instant.now();
        if (rule.getCreatedAt() == null) {
            rule.setCreatedAt(now);
        }
        rule.setUpdatedAt(now);

        return ruleRepository.save(rule);
    }

    private void validateRuleKeyUniqueness(String ruleKey) {
        if (ruleQueryService.existsByRuleKey(ruleKey)) {
            throw AlertException.ruleAlreadyExists(ruleKey);
        }
    }

    private void validateOverspeedRuleRequest(OverspeedRuleTemplateRequest request) {
        if (request.getSpeedLimit() <= 0) {
            throw new IllegalArgumentException("Speed limit must be positive");
        }
        if (request.getBuffer() < 0) {
            throw new IllegalArgumentException("Buffer must be non-negative");
        }
        if (CollectionUtils.isEmpty(request.getVehicleIds())) {
            throw new IllegalArgumentException("At least one vehicle ID is required");
        }
    }

    private void validateIdleTimeoutRuleRequest(IdleTimeoutRuleTemplateRequest request) {
        if (request.getMaxIdleMinutes() <= 0) {
            throw new IllegalArgumentException("Max idle minutes must be positive");
        }
        if (CollectionUtils.isEmpty(request.getVehicleIds())) {
            throw new IllegalArgumentException("At least one vehicle ID is required");
        }
    }

    private Map<String, Object> buildOverspeedParameters(OverspeedRuleTemplateRequest request) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("speedLimit", request.getSpeedLimit());
        parameters.put("buffer", request.getBuffer());
        parameters.put("severity", "CRITICAL");
        parameters.put("vehicleIds", new ArrayList<>(request.getVehicleIds()));
        parameters.put("unit", "km/h");
        parameters.put("evaluationInterval", 60);
        return parameters;
    }

    private Map<String, Object> buildIdleTimeoutParameters(IdleTimeoutRuleTemplateRequest request) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("maxIdleMinutes", request.getMaxIdleMinutes());
        parameters.put("severity", "WARNING");
        parameters.put("vehicleIds", new ArrayList<>(request.getVehicleIds()));
        parameters.put("ignoreEngineOff", false);
        parameters.put("notificationThreshold", request.getMaxIdleMinutes() / 2);
        return parameters;
    }

    private Map<String, Object> buildGeofenceParameters(GeofenceRuleTemplateRequest request,
                                                        Geofence geofence) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("geofenceId", request.getGeofenceId());
        parameters.put("geofenceName", geofence.getName());
        parameters.put("action", request.getAction().name());
        parameters.put("severity", "WARNING");
        parameters.put("vehicleIds", new ArrayList<>(request.getVehicleIds()));
        parameters.put("shapeType", geofence.getShapeType().name());

        if (geofence.getCenterLatitude() != null) {
            parameters.put("centerLatitude", geofence.getCenterLatitude());
        }
        if (geofence.getCenterLongitude() != null) {
            parameters.put("centerLongitude", geofence.getCenterLongitude());
        }
        if (geofence.getRadiusMeters() != null) {
            parameters.put("radiusMeters", geofence.getRadiusMeters());
        }

        return parameters;
    }

    private String buildOverspeedActions() {
        Map<String, Object> actions = new HashMap<>();
        actions.put("sendNotification", true);
        actions.put("executeCommand", false);
        actions.put("notificationChannels", Arrays.asList("SMS", "EMAIL", "DASHBOARD"));
        actions.put("escalationEnabled", true);
        actions.put("escalationAfterMinutes", 5);

        try {
            return objectMapper.writeValueAsString(actions);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize actions", e);
        }
    }

    private AlertRule createBaseRule(Object request, AlertRuleType ruleType,
                                     Map<String, Object> parameters) {
        AlertRule rule = new AlertRule();

        if (request instanceof OverspeedRuleTemplateRequest overspeedRequest) {
            rule.setRuleKey(overspeedRequest.getRuleKey());
            rule.setRuleName(overspeedRequest.getRuleName());
            rule.setPriority(overspeedRequest.getPriority());
            rule.setIsEnabled(overspeedRequest.isEnabled());
        } else if (request instanceof IdleTimeoutRuleTemplateRequest idleRequest) {
            rule.setRuleKey(idleRequest.getRuleKey());
            rule.setRuleName(idleRequest.getRuleName());
            rule.setPriority(idleRequest.getPriority());
            rule.setIsEnabled(idleRequest.isEnabled());
        } else if (request instanceof GeofenceRuleTemplateRequest geofenceRequest) {
            rule.setRuleKey(geofenceRequest.getRuleKey());
            rule.setRuleName(geofenceRequest.getRuleName());
            rule.setPriority(geofenceRequest.getPriority());
            rule.setIsEnabled(geofenceRequest.isEnabled());
        }

        rule.setRuleType(ruleType);
        rule.setParameters(parameters);
        rule.setCooldownMinutes(5);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());

        return rule;
    }

    private Map<String, Object> parseAndValidateParameters(String parametersJson, AlertRuleType ruleType) {
        Map<String, Object> parameters = ruleQueryService.parseParameters(parametersJson);
        validateRuleParameters(ruleType, parameters);
        return parameters;
    }

    private Set<String> extractVehicleIdsFromParameters(Map<String, Object> parameters) {
        Set<String> vehicleIds = new HashSet<>();

        if (parameters != null && parameters.containsKey("vehicleIds")) {
            Object vehicleIdsObj = parameters.get("vehicleIds");
            if (vehicleIdsObj instanceof Collection) {
                ((Collection<?>) vehicleIdsObj).forEach(id -> {
                    if (id != null) {
                        vehicleIds.add(id.toString().trim());
                    }
                });
            } else if (vehicleIdsObj instanceof String) {
                String[] ids = ((String) vehicleIdsObj).split(",");
                for (String id : ids) {
                    if (StringUtils.hasText(id)) {
                        vehicleIds.add(id.trim());
                    }
                }
            }
        }

        return vehicleIds;
    }

    private void validateVehicleGeofenceAssociation(Geofence geofence, Set<String> vehicleIds) {
        for (String vehicleId : vehicleIds) {
            if (!geofence.hasVehicle(vehicleId)) {
                throw new IllegalArgumentException(
                        String.format("Vehicle %s is not associated with geofence %s",
                                vehicleId, geofence.getGeofenceId()));
            }
        }
    }

    private void validateRuleParameters(AlertRuleType ruleType, Map<String, Object> parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }

        switch (ruleType) {
            case SPEED:
                validateSpeedRuleParameters(parameters);
                break;
            case TIME:
                validateTimeRuleParameters(parameters);
                break;
            case GEOFENCE:
                validateGeofenceRuleParameters(parameters);
                break;
            default:
                if (!parameters.containsKey("vehicleIds") ||
                        ((Collection<?>) parameters.get("vehicleIds")).isEmpty()) {
                    throw new IllegalArgumentException("Custom rules require at least one vehicle ID");
                }
                break;
        }
    }

    private void validateSpeedRuleParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey("speedLimit")) {
            throw new IllegalArgumentException("Speed rule requires 'speedLimit' parameter");
        }
        Object speedLimit = parameters.get("speedLimit");
        if (!(speedLimit instanceof Number)) {
            throw new IllegalArgumentException("'speedLimit' must be a number");
        }
        if (((Number) speedLimit).floatValue() <= 0) {
            throw new IllegalArgumentException("'speedLimit' must be positive");
        }

        if (parameters.containsKey("buffer")) {
            Object buffer = parameters.get("buffer");
            if (!(buffer instanceof Number)) {
                throw new IllegalArgumentException("'buffer' must be a number");
            }
            if (((Number) buffer).floatValue() < 0) {
                throw new IllegalArgumentException("'buffer' cannot be negative");
            }
        }
    }

    private void validateTimeRuleParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey("maxIdleMinutes")) {
            throw new IllegalArgumentException("Time rule requires 'maxIdleMinutes' parameter");
        }
        Object idleMinutes = parameters.get("maxIdleMinutes");
        if (!(idleMinutes instanceof Number)) {
            throw new IllegalArgumentException("'maxIdleMinutes' must be a number");
        }
        if (((Number) idleMinutes).intValue() <= 0) {
            throw new IllegalArgumentException("'maxIdleMinutes' must be positive");
        }
    }

    private void validateGeofenceRuleParameters(Map<String, Object> parameters) {
        if (!parameters.containsKey("geofenceId")) {
            throw new IllegalArgumentException("Geofence rule requires 'geofenceId' parameter");
        }

        String geofenceId = parameters.get("geofenceId").toString();
        if (!StringUtils.hasText(geofenceId)) {
            throw new IllegalArgumentException("Geofence ID cannot be empty");
        }

        geofenceRuleValidator.getValidatedGeofence(geofenceId);
    }
}