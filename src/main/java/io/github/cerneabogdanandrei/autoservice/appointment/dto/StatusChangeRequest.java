package io.github.cerneabogdanandrei.autoservice.appointment.dto;

import io.github.cerneabogdanandrei.autoservice.appointment.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotNull AppointmentStatus status) {
}
