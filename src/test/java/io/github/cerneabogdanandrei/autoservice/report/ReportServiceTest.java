package io.github.cerneabogdanandrei.autoservice.report;

import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.time.BusinessCalendar;
import io.github.cerneabogdanandrei.autoservice.report.dto.RevenueReport;
import io.github.cerneabogdanandrei.autoservice.report.dto.RevenueReport.MonthlyRevenue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");

    @Mock
    private ReportRepository reportRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, new BusinessCalendar(BUCHAREST));
    }

    @Test
    void everyMonthOfTheRangeIsListedAndTotalsAreSummed() {
        when(reportRepository.monthlyRevenue(any(), any(), any())).thenReturn(List.of(
                new MonthlyRevenueRow(YearMonth.of(2030, 1), new BigDecimal("384.80"), 2),
                new MonthlyRevenueRow(YearMonth.of(2030, 3), new BigDecimal("50"), 1)));

        RevenueReport report = reportService.revenue(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 4, 30));

        assertThat(report.currency()).isEqualTo("RON");
        assertThat(report.total()).isEqualTo(new BigDecimal("434.80"));
        assertThat(report.completedAppointments()).isEqualTo(3);
        assertThat(report.months()).containsExactly(
                new MonthlyRevenue(YearMonth.of(2030, 1), new BigDecimal("384.80"), 2),
                new MonthlyRevenue(YearMonth.of(2030, 2), new BigDecimal("0.00"), 0),
                new MonthlyRevenue(YearMonth.of(2030, 3), new BigDecimal("50.00"), 1),
                new MonthlyRevenue(YearMonth.of(2030, 4), new BigDecimal("0.00"), 0));
    }

    @Test
    void rangeCoversWholeDaysInTheShopTimeZone() {
        reportService.revenue(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 3, 31));

        // 1 Jan 00:00 at UTC+2, and 1 Apr 00:00 at UTC+3 (DST starts on 31 March 2030)
        verify(reportRepository).monthlyRevenue(
                Instant.parse("2029-12-31T22:00:00Z"), Instant.parse("2030-03-31T21:00:00Z"), BUCHAREST);
    }

    @Test
    void singleDayReportHasOneMonth() {
        RevenueReport report = reportService.revenue(LocalDate.of(2030, 5, 15), LocalDate.of(2030, 5, 15));

        assertThat(report.months()).hasSize(1);
        assertThat(report.total()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void rangeEndingBeforeItStartsIsRejected() {
        assertInvalid(() -> reportService.revenue(LocalDate.of(2030, 2, 1), LocalDate.of(2030, 1, 1)));
    }

    @Test
    void rangeLongerThanTenYearsIsRejected() {
        assertInvalid(() -> reportService.revenue(LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 2)));
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessRuleException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_PARAMETER));
        verifyNoInteractions(reportRepository);
    }
}
