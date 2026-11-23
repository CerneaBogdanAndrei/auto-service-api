package io.github.cerneabogdanandrei.autoservice.intervention;

import io.github.cerneabogdanandrei.autoservice.appointment.Appointment;
import io.github.cerneabogdanandrei.autoservice.common.persistence.BaseEntity;
import io.github.cerneabogdanandrei.autoservice.part.Part;
import io.github.cerneabogdanandrei.autoservice.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Work done during an appointment by one employee: labour cost plus the parts used.
 * Part lines are owned by the intervention (created and removed only through it).
 */
@Entity
@Table(name = "interventions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Intervention extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, updatable = false)
    private Appointment appointment;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_user_id", nullable = false)
    private User assignedUser;

    @Setter
    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Setter
    @Column(name = "labor_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal laborCost;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "intervention", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<InterventionPart> parts = new ArrayList<>();

    public Intervention(Appointment appointment, User assignedUser, String description, BigDecimal laborCost) {
        this.appointment = appointment;
        this.assignedUser = assignedUser;
        this.description = description;
        this.laborCost = laborCost;
    }

    public List<InterventionPart> getParts() {
        return Collections.unmodifiableList(parts);
    }

    public Optional<InterventionPart> findLine(Long partId) {
        return parts.stream().filter(line -> line.getPart().getId().equals(partId)).findFirst();
    }

    /** Adds a new line at the part's current price, or increases the quantity of the existing line. */
    void addPart(Part part, int quantity) {
        findLine(part.getId()).ifPresentOrElse(
                line -> line.increaseQuantity(quantity),
                () -> parts.add(new InterventionPart(this, part, quantity)));
    }

    void removeLine(InterventionPart line) {
        parts.remove(line);
    }

    public boolean isAssignedTo(Long userId) {
        return assignedUser.getId().equals(userId);
    }
}
