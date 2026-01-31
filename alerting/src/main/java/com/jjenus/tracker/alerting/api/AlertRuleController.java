package com.jjenus.tracker.alerting.api;

import com.jjenus.tracker.alerting.api.dto.*;
import com.jjenus.tracker.alerting.application.AlertRuleCommandService;
import com.jjenus.tracker.alerting.application.AlertRuleQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/alert-rules")
@Tag(name = "Alert Rules", description = "Alert rule management endpoints")
public class AlertRuleController {
    
    private final AlertRuleCommandService commandService;
    private final AlertRuleQueryService queryService;
    
    public AlertRuleController(
        AlertRuleCommandService commandService,
        AlertRuleQueryService queryService
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
    }
    
    @PostMapping
    @Operation(summary = "Create a new alert rule")
    public ResponseEntity<AlertRuleResponse> createRule(@Valid @RequestBody CreateAlertRuleRequest request) {
        AlertRuleResponse rule = commandService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }
    
    @GetMapping
    @Operation(summary = "Get all alert rules")
    public ResponseEntity<List<AlertRuleResponse>> getAllRules() {
        List<AlertRuleResponse> rules = queryService.getAllRules();
        return ResponseEntity.ok(rules);
    }
    
    @GetMapping("/enabled")
    @Operation(summary = "Get all enabled alert rules")
    public ResponseEntity<List<AlertRuleResponse>> getEnabledRules() {
        List<AlertRuleResponse> rules = queryService.getEnabledRules();
        return ResponseEntity.ok(rules);
    }
    
    @GetMapping("/{ruleKey}")
    @Operation(summary = "Get alert rule by key")
    public ResponseEntity<AlertRuleResponse> getRuleByKey(@PathVariable String ruleKey) {
        AlertRuleResponse rule = queryService.getRuleByKey(ruleKey);
        return ResponseEntity.ok(rule);
    }
    
    @PutMapping("/{ruleKey}")
    @Operation(summary = "Update an existing alert rule")
    public ResponseEntity<AlertRuleResponse> updateRule(
            @PathVariable String ruleKey,
            @Valid @RequestBody UpdateAlertRuleRequest request) {
        AlertRuleResponse rule = commandService.updateRule(ruleKey, request);
        return ResponseEntity.ok(rule);
    }
    
    @DeleteMapping("/{ruleKey}")
    @Operation(summary = "Delete an alert rule")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteRule(@PathVariable String ruleKey) {
        commandService.deleteRule(ruleKey);
        return ResponseEntity.noContent().build();
    }
    
    @PatchMapping("/{ruleKey}/enable")
    @Operation(summary = "Enable an alert rule")
    public ResponseEntity<Void> enableRule(@PathVariable String ruleKey) {
        commandService.enableRule(ruleKey);
        return ResponseEntity.ok().build();
    }
    
    @PatchMapping("/{ruleKey}/disable")
    @Operation(summary = "Disable an alert rule")
    public ResponseEntity<Void> disableRule(@PathVariable String ruleKey) {
        commandService.disableRule(ruleKey);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{ruleKey}/test")
    @Operation(summary = "Test an alert rule with sample data")
    public ResponseEntity<TestAlertResponse> testRule(
            @PathVariable String ruleKey,
            @Valid @RequestBody TestAlertRequest request) {
        TestAlertResponse result = commandService.testRule(ruleKey, request);
        return ResponseEntity.ok(result);
    }
}
