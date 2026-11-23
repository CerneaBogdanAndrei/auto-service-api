package io.github.cerneabogdanandrei.autoservice.intervention;

import io.github.cerneabogdanandrei.autoservice.part.Part;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A part used by an intervention. The unit price is copied from the catalogue when the part is
 * first added, so later price changes never alter the cost of work already done.
 */
@Entity
@Table(name = "intervention_parts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterventionPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intervention_id", nullable = false)
    private Intervention intervention;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    InterventionPart(Intervention intervention, Part part, int quantity) {
        this.intervention = intervention;
        this.part = part;
        this.quantity = quantity;
        this.unitPrice = part.getUnitPrice();
    }

    void increaseQuantity(int amount) {
        quantity += amount;
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}
