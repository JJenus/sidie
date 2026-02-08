package com.jjenus.tracker.alerting.api;

import com.jjenus.tracker.alerting.api.dto.*;
import com.jjenus.tracker.alerting.application.service.AlertRuleQueryService;
import com.jjenus.tracker.alerting.application.service.AlertRuleCommandService;
import com.jjenus.tracker.alerting.domain.enums.AlertRuleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/alerts/rules")
@Tag(name = "Alert Rules", description = "Alert rule management endpoints")
public class AlertRuleController {

    private final AlertRuleCommandService ruleCommandService;
    private final AlertRuleQueryService ruleQueryService;

    public AlertRuleController(AlertRuleCommandService ruleCommandService, AlertRuleQueryService ruleQueryService) {
        this.ruleCommandService = ruleCommandService;
        this.ruleQueryService = ruleQueryService;
    }

    // ========== CRUD ENDPOINTS ==========

    @PostMapping
    @Operation(summary = "Create a new custom alert rule")
    public ResponseEntity<AlertRuleResponse> createRule(@Valid @RequestBody CreateAlertRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleCommandService.createRule(request));
    }

    @GetMapping
    @Operation(summary = "Get all alert rules with pagination and filtering")
    public ResponseEntity<PagedResponse<AlertRuleResponse>> getAllRules(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection,

            @Parameter(description = "Search term for rule name")
            @RequestParam(required = false) String search,

            @Parameter(description = "Filter by rule type")
            @RequestParam(required = false) AlertRuleType ruleType,

            @Parameter(description = "Filter by enabled status")
            @RequestParam(required = false) Boolean enabled) {

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setPage(page);
        searchRequest.setSize(size);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortDirection(sortDirection);
        searchRequest.setSearch(search);
        searchRequest.setRuleType(ruleType);
        searchRequest.setEnabled(enabled);

        return ResponseEntity.ok(ruleQueryService.getAllRulesPaged(searchRequest));
    }

    @GetMapping("/enabled")
    @Operation(summary = "Get all enabled alert rules with pagination")
    public ResponseEntity<PagedResponse<AlertRuleResponse>> getEnabledRules(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "priority") String sortBy,

            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection,

            @Parameter(description = "Search term for rule name")
            @RequestParam(required = false) String search) {

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setPage(page);
        searchRequest.setSize(size);
        searchRequest.setSortBy(sortBy);
        searchRequest.setSortDirection(sortDirection);
        searchRequest.setSearch(search);
        searchRequest.setEnabled(true);

        return ResponseEntity.ok(ruleQueryService.getEnabledRulesPaged(searchRequest));
    }

    @GetMapping("/list")
    @Operation(summary = "Get all alert rules (without pagination)")
    public ResponseEntity<List<AlertRuleResponse>> getAllRulesList() {
        return ResponseEntity.ok(ruleQueryService.getAllRules());
    }

    @GetMapping("/enabled/list")
    @Operation(summary = "Get all enabled alert rules (without pagination)")
    public ResponseEntity<List<AlertRuleResponse>> getEnabledRulesList() {
        return ResponseEntity.ok(ruleQueryService.getEnabledRules());
    }

    @GetMapping("/{ruleKey}")
    @Operation(summary = "Get alert rule by key")
    public ResponseEntity<AlertRuleResponse> getRuleByKey(@PathVariable String ruleKey) {
        return ResponseEntity.ok(ruleQueryService.getRuleByKey(ruleKey));
    }

    @PutMapping("/{ruleKey}")
    @Operation(summary = "Update an existing alert rule")
    public ResponseEntity<AlertRuleResponse> updateRule(
            @PathVariable String ruleKey,
            @Valid @RequestBody UpdateAlertRuleRequest request) {
        return ResponseEntity.ok(ruleCommandService.updateRule(ruleKey, request));
    }

    @DeleteMapping("/{ruleKey}")
    @Operation(summary = "Delete an alert rule")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable String ruleKey) {
        ruleCommandService.deleteRule(ruleKey);
    }

    @PatchMapping("/{ruleKey}/enable")
    @Operation(summary = "Enable an alert rule")
    public ResponseEntity<Void> enableRule(@PathVariable String ruleKey) {
        ruleCommandService.enableRule(ruleKey);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{ruleKey}/disable")
    @Operation(summary = "Disable an alert rule")
    public ResponseEntity<Void> disableRule(@PathVariable String ruleKey) {
        ruleCommandService.disableRule(ruleKey);
        return ResponseEntity.ok().build();
    }

    // ========== TEMPLATE ENDPOINTS ==========

    @PostMapping("/templates/overspeed")
    @Operation(summary = "Create overspeed rule from template")
    public ResponseEntity<AlertRuleResponse> createOverspeedRule(
            @Valid @RequestBody OverspeedRuleTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleCommandService.createOverspeedRule(request));
    }

    @PostMapping("/templates/idle-timeout")
    @Operation(summary = "Create idle timeout rule from template")
    public ResponseEntity<AlertRuleResponse> createIdleTimeoutRule(
            @Valid @RequestBody IdleTimeoutRuleTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleCommandService.createIdleTimeoutRule(request));
    }

    @PostMapping("/templates/geofence")
    @Operation(summary = "Create geofence rule from template")
    public ResponseEntity<AlertRuleResponse> createGeofenceRule(
            @Valid @RequestBody GeofenceRuleTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleCommandService.createGeofenceRule(request));
    }

    // ========== BATCH OPERATIONS ==========

    @PostMapping("/batch")
    @Operation(summary = "Create multiple alert rules in batch")
    public ResponseEntity<List<AlertRuleResponse>> batchCreateRules(
            @Valid @RequestBody List<CreateAlertRuleRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleCommandService.batchCreateRules(requests));
    }

    @PostMapping("/batch/enable")
    @Operation(summary = "Enable multiple alert rules")
    public ResponseEntity<Void> batchEnableRules(@RequestBody List<String> ruleKeys) {
        ruleCommandService.batchEnableRules(new java.util.HashSet<>(ruleKeys));
        return ResponseEntity.ok().build();
    }
}