package io.github.cerneabogdanandrei.autoservice.report;

import io.github.cerneabogdanandrei.autoservice.support.AbstractIntegrationTest;
import io.github.cerneabogdanandrei.autoservice.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerIT extends AbstractIntegrationTest {

    @Test
    void revenueIsReportedInTotalAndPerShopMonth() throws Exception {
        long employee = idOf(TestUsers.EMPLOYEE);
        long vehicle = data.vehicle(data.client());
        long filter = data.part("FLT-001", "49.90", 10);
        long oil = data.part("OIL-5W30", "35.00", 10);

        // January: 150 + 2 × 49.90 + 35 = 284.80
        long jan1 = completed(vehicle, "2025-01-10T10:00:00Z");
        long work = data.intervention(jan1, employee, "150.00");
        data.interventionPart(work, filter, 2, "49.90");
        data.interventionPart(work, oil, 1, "35.00");
        // January: labour only, 100.00
        data.intervention(completed(vehicle, "2025-01-20T10:00:00Z"), employee, "100.00");
        // 31 Jan 22:30 UTC is already 1 February in Bucharest: 75.50
        data.intervention(completed(vehicle, "2025-01-31T22:30:00Z"), employee, "75.50");
        // March: 50.00
        data.intervention(completed(vehicle, "2025-03-05T10:00:00Z"), employee, "50.00");
        // not counted: still in progress, and completed outside the range
        Instant start = Instant.parse("2025-01-15T08:00:00Z");
        data.intervention(data.appointment(vehicle, start, start.plus(Duration.ofHours(1)), "IN_PROGRESS"), employee, "999.00");
        data.intervention(completed(vehicle, "2025-04-01T10:00:00Z"), employee, "999.00");

        String body = mockMvc.perform(get("/api/reports/revenue").param("from", "2025-01-01").param("to", "2025-03-31")
                        .with(as(TestUsers.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2025-01-01"))
                .andExpect(jsonPath("$.currency").value("RON"))
                .andExpect(jsonPath("$.completedAppointments").value(4))
                .andExpect(jsonPath("$.months.length()").value(3))
                .andExpect(jsonPath("$.months[0].month").value("2025-01"))
                .andExpect(jsonPath("$.months[0].completedAppointments").value(2))
                .andExpect(jsonPath("$.months[1].month").value("2025-02"))
                .andExpect(jsonPath("$.months[2].month").value("2025-03"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("\"total\":510.30")
                .contains("{\"month\":\"2025-01\",\"revenue\":384.80,\"completedAppointments\":2}")
                .contains("{\"month\":\"2025-02\",\"revenue\":75.50,\"completedAppointments\":1}")
                .contains("{\"month\":\"2025-03\",\"revenue\":50.00,\"completedAppointments\":1}");
    }

    @Test
    void adminCanAlsoSeeReports() throws Exception {
        mockMvc.perform(get("/api/reports/revenue").param("from", "2025-01-01").param("to", "2025-01-31")
                        .with(as(TestUsers.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.0))
                .andExpect(jsonPath("$.months[0].revenue").value(0.0));
    }

    @ParameterizedTest
    @EnumSource(value = TestUsers.class, names = "EMPLOYEE")
    void employeesCannotSeeReports(TestUsers user) throws Exception {
        mockMvc.perform(get("/api/reports/revenue").param("from", "2025-01-01").param("to", "2025-01-31").with(as(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void anonymousRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/reports/revenue").param("from", "2025-01-01").param("to", "2025-01-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRangesAreRejected() throws Exception {
        mockMvc.perform(get("/api/reports/revenue").param("from", "2025-01-01").with(as(TestUsers.OWNER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Required parameter 'to' is missing."));
        mockMvc.perform(get("/api/reports/revenue").param("from", "2025-02-01").param("to", "2025-01-01")
                        .with(as(TestUsers.OWNER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mockMvc.perform(get("/api/reports/revenue").param("from", "01/01/2025").param("to", "2025-01-31")
                        .with(as(TestUsers.OWNER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    /** A completed appointment whose completion time is {@code completedAt}. */
    private long completed(long vehicleId, String completedAt) {
        Instant end = Instant.parse(completedAt);
        return data.appointment(vehicleId, end.minus(Duration.ofHours(2)), end, "COMPLETED");
    }
}
