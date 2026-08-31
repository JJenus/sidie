package com.jjenus.tracker.userauth.api;

import com.jjenus.tracker.userauth.application.dto.OrganizationRequest;
import com.jjenus.tracker.userauth.application.dto.OrganizationResponse;
import com.jjenus.tracker.userauth.application.service.OrganizationService;
import com.jjenus.tracker.userauth.domain.entity.Organization;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> list() {
        List<OrganizationResponse> result = organizationService.listAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganizationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(organizationService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody OrganizationRequest request) {
        Organization created = organizationService.create(request.getName(), request.getSlug());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    private OrganizationResponse toResponse(Organization org) {
        return new OrganizationResponse(org.getId(), org.getName(), org.getSlug(), org.getCreatedAt());
    }
}
