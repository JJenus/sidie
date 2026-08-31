package com.jjenus.tracker.userauth.application.service;

import com.jjenus.tracker.shared.exception.DomainException;
import com.jjenus.tracker.shared.exception.ValidationException;
import com.jjenus.tracker.userauth.domain.entity.Permission;
import com.jjenus.tracker.userauth.infrastructure.repository.PermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<Permission> listAll() {
        return permissionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Set<String> resolveForUser(Collection<String> roleNames, Long orgId) {
        return permissionRepository.findAll().stream()
            .filter(p -> {
                if (p.getId() == null) return false;
                return true;
            })
            .map(Permission::getKey)
            .collect(Collectors.toSet());
    }

    @Transactional
    public Permission create(String key, String description) {
        if (key == null || key.isBlank()) {
            throw new ValidationException("PERM_KEY_REQUIRED", "permission key required");
        }
        if (permissionRepository.findByKey(key).isPresent()) {
            throw new ValidationException("PERM_EXISTS", "permission already exists: " + key);
        }
        Permission p = Permission.of(key, description);
        return permissionRepository.save(p);
    }
}
