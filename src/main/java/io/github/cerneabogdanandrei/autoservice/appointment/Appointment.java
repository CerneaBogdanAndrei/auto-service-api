package io.github.cerneabogdanandrei.autoservice.appointment;

import io.github.cerneabogdanandrei.autoservice.common.persistence.BaseEntity;
import io.github.cerneabogdanandrei.autoservice.vehicle.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A booking of one vehicle over the half-open interval [startAt, endAt).
 * Status changes go through {@link #changeStatus(AppointmentStatus, Instant)} so the
 * completion time always matches the status.
 */
@Entity
@Table(name = "appointments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment extends BaseEntity {

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Setter
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Setter
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Setter
    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Appointment(Vehicle vehicle, Instant startAt, Instant endAt, String notes) {
        this.vehicle = vehicle;
        this.startAt = startAt;
        this.endAt = endAt;
        this.notes = notes;
    }

    /**
     * Applies a transition already validated by the caller; records the completion time when
     * the appointment is completed.
     */
    void changeStatus(AppointmentStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Transition %s -> %s is not allowed".formatted(status, target));
        }
        status = target;
        if (target == AppointmentStatus.COMPLETED) {
            completedAt = now;
        }
    }
}
