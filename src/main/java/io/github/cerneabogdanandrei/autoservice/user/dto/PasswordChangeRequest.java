package io.github.cerneabogdanandrei.autoservice.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(@NotBlank @Size(min = 8, max = 72) String password) {

    @Override
    public String toString() {
        return "PasswordChangeRequest[password=****]";
    }
}
