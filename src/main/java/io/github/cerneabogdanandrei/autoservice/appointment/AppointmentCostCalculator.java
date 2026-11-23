package io.github.cerneabogdanandrei.autoservice.appointment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Total cost of an appointment = labour + Σ(quantity × unit price), computed exactly with
 * {@link BigDecimal}. Prices have two decimals and quantities are integers, so the result is
 * exact; the scale is normalized to two decimals for presentation.
 */
public final class AppointmentCostCalculator {

    private static final int MONEY_SCALE = 2;

    private AppointmentCostCalculator() {
    }

    public record PartLine(int quantity, BigDecimal unitPrice) {
    }

    public static AppointmentCost calculate(Collection<BigDecimal> laborCosts, Collection<PartLine> partLines) {
        BigDecimal labor = laborCosts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal parts = partLines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AppointmentCost(money(labor), money(parts), money(labor.add(parts)));
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
