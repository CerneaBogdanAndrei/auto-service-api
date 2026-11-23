package io.github.cerneabogdanandrei.autoservice.user.dto;

import io.github.cerneabogdanandrei.autoservice.common.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Full replacement of the editable fields. The username is immutable; the password has its
 * own endpoint.
 */
public record UserUpdateRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotNull Role role,
        @NotNull Boolean enabled) {
}
