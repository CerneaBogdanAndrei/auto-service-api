package io.github.cerneabogdanandrei.autoservice.appointment;

import java.math.BigDecimal;

/**
 * Cost breakdown of an appointment, in RON, always with two decimals.
 *
 * @param labor sum of the labour cost of all interventions
 * @param parts sum of quantity × unit price of all parts used
 * @param total labor + parts
 */
public record AppointmentCost(BigDecimal labor, BigDecimal parts, BigDecimal total) {
}
