package com.jjenus.tracker.userauth.application.service;

import com.jjenus.tracker.shared.exception.DomainException;
import com.jjenus.tracker.shared.exception.ValidationException;
import com.jjenus.tracker.userauth.domain.entity.Permission;
import com.jjenus.tracker.userauth.infrastructure.repository.PermissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<Permission> listAll() {
        List<Permission> result = new ArrayList<>();
        findAllBatched(PageRequest.of(0, 500), result::add);
        return result;
    }

    private void findAllBatched(Pageable pageable, Consumer<Permission> consumer) {
        Page<Permission> page;
        do {
            page = permissionRepository.findAll(pageable);
            page.forEach(consumer);
            pageable = PageRequest.of(pageable.getPageNumber() + 1, pageable.getPageSize());
        } while (page.hasNext());
    }

    @Transactional(readOnly = true)
    public Set<String> resolveForUser(Collection<String> roleNames, Long orgId) {
        Set<String> result = new HashSet<>();
        for (Permission permission : listAll()) {
            if (permission.getId() != null) {
                result.add(permission.getKey());
            }
        }
        return result;
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
