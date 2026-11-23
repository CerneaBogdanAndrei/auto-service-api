package io.github.cerneabogdanandrei.autoservice.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Used for both create and full update (PUT).
 */
public record ClientRequest(
        @NotBlank @Size(max = 100) String name,

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number")
        String phone,

        @NotBlank @Email @Size(max = 254) String email) {
}
