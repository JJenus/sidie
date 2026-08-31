package com.jjenus.tracker.userauth.api;

import com.jjenus.tracker.userauth.application.dto.*;
import com.jjenus.tracker.userauth.application.service.AuthService;
import com.jjenus.tracker.shared.security.TenantContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        Long userId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(authService.getCurrentUser(userId));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        Long orgId = TenantContext.getCurrentOrgId();
        if (orgId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(authService.listUsersInOrg(orgId));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        Long orgId = TenantContext.getCurrentOrgId();
        UserResponse created = authService.createUser(
            request.getEmail(), request.getPassword(),
            request.getFirstName(), request.getLastName(),
            orgId != null ? orgId : request.getOrganizationId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<UserResponse> assignRoles(@PathVariable Long id,
                                                    @Valid @RequestBody AssignRolesRequest request) {
        Long orgId = TenantContext.getCurrentOrgId();
        UserResponse updated = authService.assignRoles(id, request.getRoleIds(),
            orgId != null ? orgId : request.getOrganizationId());
        return ResponseEntity.ok(updated);
    }
}
