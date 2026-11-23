package io.github.cerneabogdanandrei.autoservice.report;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Read-only reporting queries. Aggregation happens in PostgreSQL ({@code NUMERIC}, so sums are
 * exact): the cost of each completed appointment is its labour plus quantity × recorded unit
 * price of its parts, grouped by the month it was completed in the shop's time zone.
 */
@Repository
class ReportRepository {

    private static final String MONTHLY_REVENUE = """
            WITH appointment_totals AS (
                SELECT a.completed_at,
                       COALESCE((SELECT SUM(i.labor_cost)
                                 FROM interventions i
                                 WHERE i.appointment_id = a.id), 0)
                     + COALESCE((SELECT SUM(ip.quantity * ip.unit_price)
                                 FROM intervention_parts ip
                                 JOIN interventions i ON i.id = ip.intervention_id
                                 WHERE i.appointment_id = a.id), 0) AS total
                FROM appointments a
                WHERE a.status = 'COMPLETED'
                  AND a.completed_at >= :from
                  AND a.completed_at < :to
            )
            SELECT to_char(completed_at AT TIME ZONE :zone, 'YYYY-MM') AS month,
                   SUM(total)                                          AS revenue,
                   COUNT(*)                                            AS completed_appointments
            FROM appointment_totals
            GROUP BY 1
            ORDER BY 1
            """;

    private final JdbcClient jdbcClient;

    ReportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * @param from first instant included
     * @param to   first instant excluded
     * @return only months with at least one completed appointment, in order
     */
    List<MonthlyRevenueRow> monthlyRevenue(Instant from, Instant to, ZoneId zone) {
        return jdbcClient.sql(MONTHLY_REVENUE)
                .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .param("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .param("zone", zone.getId())
                .query((rs, rowNum) -> new MonthlyRevenueRow(
                        YearMonth.parse(rs.getString("month")),
                        rs.getBigDecimal("revenue"),
                        rs.getLong("completed_appointments")))
                .list();
    }
}
