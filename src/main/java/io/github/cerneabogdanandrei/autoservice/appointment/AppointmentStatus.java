package io.github.cerneabogdanandrei.autoservice.appointment;

import java.util.Set;

/**
 * Appointment lifecycle:
 * <pre>
 *   SCHEDULED ──► IN_PROGRESS ──► COMPLETED
 *       │
 *       └──────► CANCELLED
 * </pre>
 * COMPLETED and CANCELLED are final.
 */
public enum AppointmentStatus {

    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(AppointmentStatus target) {
        return allowedTargets().contains(target);
    }

    public Set<AppointmentStatus> allowedTargets() {
        return switch (this) {
            case SCHEDULED -> Set.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(COMPLETED);
            case COMPLETED, CANCELLED -> Set.of();
        };
    }
}
