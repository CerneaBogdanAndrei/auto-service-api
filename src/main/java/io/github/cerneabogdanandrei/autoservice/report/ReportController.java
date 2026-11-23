package io.github.cerneabogdanandrei.autoservice.report;

import io.github.cerneabogdanandrei.autoservice.report.dto.RevenueReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
@Tag(name = "Reports", description = "Business reports (ADMIN and OWNER)")
public class ReportController {

    private final ReportService reportService;

    ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/revenue")
    @Operation(summary = "Revenue of completed appointments, in total and per month",
            description = "An appointment counts in the month it was completed (shop time zone). "
                    + "Its revenue is the labour of its interventions plus quantity × recorded unit price of the parts used.")
    public RevenueReport revenue(
            @Parameter(description = "First day, inclusive", example = "2026-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day, inclusive", example = "2026-12-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.revenue(from, to);
    }
}
