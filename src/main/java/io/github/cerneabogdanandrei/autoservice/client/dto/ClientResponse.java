package io.github.cerneabogdanandrei.autoservice.client.dto;

import java.time.Instant;

public record ClientResponse(
        Long id,
        String name,
        String phone,
        String email,
        Instant createdAt,
        Instant updatedAt) {
}
