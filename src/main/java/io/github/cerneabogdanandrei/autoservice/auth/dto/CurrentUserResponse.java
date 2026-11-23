package io.github.cerneabogdanandrei.autoservice.auth.dto;

import io.github.cerneabogdanandrei.autoservice.common.security.Role;

public record CurrentUserResponse(Long id, String username, Role role) {
}
