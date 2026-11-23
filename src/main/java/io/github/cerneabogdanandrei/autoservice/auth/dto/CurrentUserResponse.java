package io.github.cerneabogdanandrei.autoservice.auth.dto;

import io.github.cerneabogdanandrei.autoservice.user.Role;

public record CurrentUserResponse(Long id, String username, Role role) {
}
