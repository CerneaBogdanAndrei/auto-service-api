package io.github.cerneabogdanandrei.autoservice.user.dto;

import io.github.cerneabogdanandrei.autoservice.common.security.Role;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        Role role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
