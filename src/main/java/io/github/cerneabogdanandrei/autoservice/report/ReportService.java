package io.github.cerneabogdanandrei.autoservice.report;

import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.time.BusinessCalendar;
import io.github.cerneabogdanandrei.autoservice.report.dto.RevenueReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    static final String CURRENCY = "RON";
    static final Period MAX_RANGE = Period.ofYears(10);

    private final ReportRepository reportRepository;
    private final BusinessCalendar calendar;

    ReportService(ReportRepository reportRepository, BusinessCalendar calendar) {
        this.reportRepository = reportRepository;
        this.calendar = calendar;
    }

    /**
     * Revenue of the appointments completed in [from, to] (whole days, shop time zone), in total
     * and per month.
     */
    public RevenueReport revenue(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException(ErrorCode.INVALID_PARAMETER, "'from' must not be after 'to'.");
        }
        if (to.isAfter(from.plus(MAX_RANGE))) {
            throw new BusinessRuleException(ErrorCode.INVALID_PARAMETER, "The report range cannot exceed 10 years.");
        }
        Map<YearMonth, MonthlyRevenueRow> byMonth = reportRepository
                .monthlyRevenue(calendar.startOf(from), calendar.endOf(to), calendar.zone())
                .stream()
                .collect(Collectors.toMap(MonthlyRevenueRow::month, Function.identity()));

        List<RevenueReport.MonthlyRevenue> months = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        long completed = 0;
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            MonthlyRevenueRow row = byMonth.get(month);
            BigDecimal revenue = row == null ? BigDecimal.ZERO : row.revenue();
            long count = row == null ? 0 : row.completedAppointments();
            months.add(new RevenueReport.MonthlyRevenue(month, money(revenue), count));
            total = total.add(revenue);
            completed += count;
        }
        return new RevenueReport(from, to, CURRENCY, money(total), completed, months);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
