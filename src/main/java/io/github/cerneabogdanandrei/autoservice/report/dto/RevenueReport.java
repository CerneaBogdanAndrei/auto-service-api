package io.github.cerneabogdanandrei.autoservice.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Revenue of the appointments completed between {@code from} and {@code to} (inclusive, shop
 * time zone). Every month of the range is listed, with zero when nothing was completed.
 */
public record RevenueReport(
        LocalDate from,
        LocalDate to,
        String currency,
        BigDecimal total,
        long completedAppointments,
        List<MonthlyRevenue> months) {

    public record MonthlyRevenue(YearMonth month, BigDecimal revenue, long completedAppointments) {
    }
}
