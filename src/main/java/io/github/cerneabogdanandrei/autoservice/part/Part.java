package io.github.cerneabogdanandrei.autoservice.part;

import io.github.cerneabogdanandrei.autoservice.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "parts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Part extends BaseEntity {

    /** Stored in upper case; unique. */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Version
    @Column(name = "version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long version;

    /**
     * Removes units from stock. Callers check availability first and report a business error;
     * this guard only protects the invariant.
     */
    public void takeFromStock(int quantity) {
        if (quantity <= 0 || quantity > stock) {
            throw new IllegalArgumentException("Cannot take %d unit(s) from a stock of %d".formatted(quantity, stock));
        }
        stock -= quantity;
    }

    public void returnToStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }
        stock += quantity;
    }
}
