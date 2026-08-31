package com.jjenus.tracker.userauth.api;

import com.jjenus.tracker.userauth.application.dto.OrganizationRequest;
import com.jjenus.tracker.userauth.application.service.OrganizationService;
import com.jjenus.tracker.userauth.domain.entity.Organization;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        List<Map<String, Object>> result = organizationService.listAll().stream()
            .map(this::toMap)
            .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(toMap(organizationService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody OrganizationRequest request) {
        Organization created = organizationService.create(request.getName(), request.getSlug());
        return ResponseEntity.status(HttpStatus.CREATED).body(toMap(created));
    }

    private Map<String, Object> toMap(Organization org) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", org.getId());
        map.put("name", org.getName());
        map.put("slug", org.getSlug());
        map.put("createdAt", org.getCreatedAt());
        return map;
    }
}
