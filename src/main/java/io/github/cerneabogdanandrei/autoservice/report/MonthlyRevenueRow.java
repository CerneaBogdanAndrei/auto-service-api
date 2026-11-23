package io.github.cerneabogdanandrei.autoservice.report;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Revenue of the completed appointments of one month, as aggregated by the database.
 */
record MonthlyRevenueRow(YearMonth month, BigDecimal revenue, long completedAppointments) {
}
