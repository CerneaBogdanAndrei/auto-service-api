package io.github.cerneabogdanandrei.autoservice.user.dto;

import io.github.cerneabogdanandrei.autoservice.common.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain only letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank @Size(min = 8, max = 72) String password,

        @NotBlank @Size(max = 100) String fullName,

        @NotNull Role role) {

    @Override
    public String toString() {
        return "UserCreateRequest[username=%s, password=****, fullName=%s, role=%s]".formatted(username, fullName, role);
    }
}
