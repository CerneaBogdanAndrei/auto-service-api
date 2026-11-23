package io.github.cerneabogdanandrei.autoservice.appointment;

import io.github.cerneabogdanandrei.autoservice.appointment.AppointmentCostCalculator.PartLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentCostCalculatorTest {

    @Test
    void totalIsLabourPlusQuantityTimesUnitPrice() {
        AppointmentCost cost = AppointmentCostCalculator.calculate(
                List.of(new BigDecimal("150.00"), new BigDecimal("100.50")),
                List.of(new PartLine(2, new BigDecimal("49.90")), new PartLine(1, new BigDecimal("35.00"))));

        assertThat(cost.labor()).isEqualByComparingTo("250.50");
        assertThat(cost.parts()).isEqualByComparingTo("134.80");
        assertThat(cost.total()).isEqualByComparingTo("385.30");
    }

    @Test
    void noInterventionsMeansZeroWithTwoDecimals() {
        AppointmentCost cost = AppointmentCostCalculator.calculate(Collections.emptyList(), Collections.emptyList());

        assertThat(cost.total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(cost.labor()).hasScaleOf(2);
        assertThat(cost.parts()).hasScaleOf(2);
    }

    @Test
    void arithmeticIsExactWhereDoublesWouldDrift() {
        // 0.1 + 0.2 != 0.3 in binary floating point; ten parts at 0.10 must be exactly 1.00
        AppointmentCost cost = AppointmentCostCalculator.calculate(
                List.of(new BigDecimal("0.10"), new BigDecimal("0.20")),
                List.of(new PartLine(10, new BigDecimal("0.10"))));

        assertThat(cost.labor()).isEqualTo(new BigDecimal("0.30"));
        assertThat(cost.parts()).isEqualTo(new BigDecimal("1.00"));
        assertThat(cost.total()).isEqualTo(new BigDecimal("1.30"));
    }

    @Test
    void largeAmountsKeepFullPrecision() {
        AppointmentCost cost = AppointmentCostCalculator.calculate(
                List.of(new BigDecimal("9999999999.99")),
                List.of(new PartLine(1_000, new BigDecimal("9999999.99"))));

        assertThat(cost.total()).isEqualTo(new BigDecimal("19999999989.99"));
    }
}
