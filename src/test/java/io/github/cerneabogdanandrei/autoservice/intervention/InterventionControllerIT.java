package io.github.cerneabogdanandrei.autoservice.intervention;

import com.jayway.jsonpath.JsonPath;
import io.github.cerneabogdanandrei.autoservice.support.AbstractIntegrationTest;
import io.github.cerneabogdanandrei.autoservice.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InterventionControllerIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private long appointmentId;
    private long employeeId;
    private long filterId;
    private long oilId;

    @BeforeEach
    void inProgressAppointmentAndParts() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        appointmentId = data.appointment(data.vehicle(data.client()), start, start.plus(3, ChronoUnit.HOURS), "IN_PROGRESS");
        employeeId = idOf(TestUsers.EMPLOYEE);
        filterId = data.part("FLT-001", "49.90", 10);
        oilId = data.part("OIL-5W30", "35.00", 3);
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void ownerCreatesInterventionUsingPartsAndStockIsTaken() throws Exception {
        var created = send(post("/api/interventions"), as(TestUsers.OWNER), createJson(employeeId, """
                [{"partId": %d, "quantity": 2}, {"partId": %d, "quantity": 1}]""".formatted(filterId, oilId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(appointmentId))
                .andExpect(jsonPath("$.assignee.username").value("employee"))
                .andExpect(jsonPath("$.parts.length()").value(2))
                .andExpect(jsonPath("$.parts[0].code").value("FLT-001"))
                .andExpect(jsonPath("$.parts[0].lineTotal").value(99.80))
                .andReturn().getResponse();
        assertThat(created.getContentAsString()).contains("\"partsCost\":134.80", "\"total\":284.80");
        int id = JsonPath.read(created.getContentAsString(), "$.id");
        assertThat(created.getHeader("Location")).endsWith("/api/interventions/" + id);

        assertThat(data.stockOf(filterId)).isEqualTo(8);
        assertThat(data.stockOf(oilId)).isEqualTo(2);
        mockMvc.perform(get("/api/appointments/{id}", appointmentId).with(as(TestUsers.OWNER)))
                .andExpect(jsonPath("$.cost.total").value(284.80));
    }

    @Test
    void assignedEmployeeWorksOnTheirIntervention() throws Exception {
        long id = data.intervention(appointmentId, employeeId, "150.00");

        mockMvc.perform(get("/api/interventions/{id}", id).with(as(TestUsers.EMPLOYEE)))
                .andExpect(status().isOk());
        send(post("/api/interventions/{id}/parts", id), as(TestUsers.EMPLOYEE), partJson(filterId, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts[0].quantity").value(3))
                .andExpect(jsonPath("$.total").value(299.70));
        send(put("/api/interventions/{id}", id), as(TestUsers.EMPLOYEE), """
                {"description": "Oil and filter changed", "laborCost": 180.00}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Oil and filter changed"))
                .andExpect(jsonPath("$.total").value(329.70));
        mockMvc.perform(delete("/api/interventions/{id}/parts/{partId}", id, filterId).with(as(TestUsers.EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parts.length()").value(0));

        assertThat(data.stockOf(filterId)).isEqualTo(10);
    }

    @Test
    void deletingAnInterventionReturnsItsPartsToStock() throws Exception {
        long id = data.intervention(appointmentId, employeeId, "150.00");
        send(post("/api/interventions/{id}/parts", id), as(TestUsers.OWNER), partJson(oilId, 3)).andExpect(status().isOk());
        assertThat(data.stockOf(oilId)).isZero();

        mockMvc.perform(delete("/api/interventions/{id}", id).with(as(TestUsers.OWNER))).andExpect(status().isNoContent());

        assertThat(data.stockOf(oilId)).isEqualTo(3);
        assertThat(data.count("intervention_parts")).isZero();
    }

    // ------------------------------------------------------------------ employee scope

    @Test
    void employeesOnlySeeTheirOwnInterventions() throws Exception {
        long mechanic = data.employee("mechanic");
        data.intervention(appointmentId, employeeId, "100.00");
        data.intervention(appointmentId, mechanic, "200.00");

        mockMvc.perform(get("/api/interventions").with(as(TestUsers.EMPLOYEE)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].assignee.username").value("employee"));
        // the assignee filter cannot be used to look at someone else's work
        mockMvc.perform(get("/api/interventions").param("assigneeId", String.valueOf(mechanic)).with(as(TestUsers.EMPLOYEE)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].assignee.username").value("employee"));
        mockMvc.perform(get("/api/interventions").with(as(TestUsers.OWNER)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void employeeCannotTouchSomeoneElsesIntervention() throws Exception {
        long mechanic = data.employee("mechanic");
        long id = data.intervention(appointmentId, mechanic, "200.00");

        mockMvc.perform(get("/api/interventions/{id}", id).with(as(TestUsers.EMPLOYEE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        send(post("/api/interventions/{id}/parts", id), as(TestUsers.EMPLOYEE), partJson(filterId, 1))
                .andExpect(status().isForbidden());
        send(put("/api/interventions/{id}", id), as(TestUsers.EMPLOYEE), """
                {"description": "Hijacked", "laborCost": 1.00}""")
                .andExpect(status().isForbidden());

        assertThat(data.stockOf(filterId)).isEqualTo(10);
        mockMvc.perform(get("/api/interventions/{id}", id).with(asUser("mechanic", "Employee123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Scheduled maintenance"));
    }

    @Test
    void employeeCannotCreateDeleteOrReassignInterventions() throws Exception {
        long mechanic = data.employee("mechanic");
        long id = data.intervention(appointmentId, employeeId, "150.00");

        send(post("/api/interventions"), as(TestUsers.EMPLOYEE), createJson(employeeId, "[]"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/interventions/{id}", id).with(as(TestUsers.EMPLOYEE)))
                .andExpect(status().isForbidden());
        send(put("/api/interventions/{id}", id), as(TestUsers.EMPLOYEE), """
                {"assignedUserId": %d, "description": "Not mine anymore", "laborCost": 150.00}""".formatted(mechanic))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/interventions")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 400 / 404

    @Test
    void invalidRequestsAreRejected() throws Exception {
        send(post("/api/interventions"), as(TestUsers.OWNER), """
                {"appointmentId": %d, "assignedUserId": %d, "description": "", "laborCost": -1,
                 "parts": [{"partId": %d, "quantity": 0}]}""".formatted(appointmentId, employeeId, filterId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void unknownResourcesReturn404() throws Exception {
        long id = data.intervention(appointmentId, employeeId, "150.00");

        mockMvc.perform(get("/api/interventions/{id}", 404).with(as(TestUsers.OWNER))).andExpect(status().isNotFound());
        send(post("/api/interventions/{id}/parts", id), as(TestUsers.OWNER), partJson(404, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Part with id 404 was not found"));
        mockMvc.perform(delete("/api/interventions/{id}/parts/{partId}", id, filterId).with(as(TestUsers.OWNER)))
                .andExpect(status().isNotFound());
        send(post("/api/interventions"), as(TestUsers.OWNER), createJson(employeeId, "[]").replace(
                "\"appointmentId\": " + appointmentId, "\"appointmentId\": 404"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 409

    @Test
    void insufficientStockRejectsTheWholeRequestWithoutPartialChanges() throws Exception {
        send(post("/api/interventions"), as(TestUsers.OWNER), createJson(employeeId, """
                [{"partId": %d, "quantity": 2}, {"partId": %d, "quantity": 4}]""".formatted(filterId, oilId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.partId").value(oilId))
                .andExpect(jsonPath("$.requested").value(4))
                .andExpect(jsonPath("$.available").value(3));

        assertThat(data.stockOf(filterId)).isEqualTo(10);
        assertThat(data.stockOf(oilId)).isEqualTo(3);
        assertThat(data.count("interventions")).isZero();
    }

    @Test
    void interventionsAreFrozenOnceTheAppointmentIsCompleted() throws Exception {
        long id = data.intervention(appointmentId, employeeId, "150.00");
        jdbcTemplate.update("UPDATE appointments SET status = 'COMPLETED', completed_at = now() WHERE id = ?", appointmentId);

        send(post("/api/interventions/{id}/parts", id), as(TestUsers.EMPLOYEE), partJson(filterId, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_IN_PROGRESS"))
                .andExpect(jsonPath("$.appointmentStatus").value("COMPLETED"));
        send(post("/api/interventions"), as(TestUsers.OWNER), createJson(employeeId, "[]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_IN_PROGRESS"));
        mockMvc.perform(delete("/api/interventions/{id}", id).with(as(TestUsers.OWNER)))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyActiveEmployeesCanBeAssigned() throws Exception {
        send(post("/api/interventions"), as(TestUsers.OWNER), createJson(idOf(TestUsers.OWNER), "[]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ASSIGNEE"));
    }

    @Test
    void concurrentUsesOfAPartNeverOversellTheStock() throws Exception {
        long id = data.intervention(appointmentId, employeeId, "150.00");
        jdbcTemplate.update("UPDATE parts SET stock = 5 WHERE id = ?", filterId);
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(TestUsers.OWNER))
                .build();
        int requests = 10;
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                results.add(pool.submit(() -> {
                    startGate.await();
                    return client.post().uri("/api/interventions/{id}/parts", id)
                            .contentType(MediaType.APPLICATION_JSON).body(partJson(filterId, 1))
                            .exchange((request, response) -> response.getStatusCode().value());
                }));
            }
            startGate.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }

            assertThat(statuses).filteredOn(code -> code == 200).hasSize(5);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(5);
        }
        assertThat(data.stockOf(filterId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT quantity FROM intervention_parts WHERE intervention_id = ? AND part_id = ?",
                Integer.class, id, filterId)).isEqualTo(5);
    }

    @Test
    void listingFetchesAssigneesInTheSameQuery() throws Exception {
        long mechanic = data.employee("mechanic");
        for (int i = 0; i < 5; i++) {
            data.intervention(appointmentId, i % 2 == 0 ? employeeId : mechanic, "100.00");
        }

        queries.reset();
        mockMvc.perform(get("/api/interventions").with(as(TestUsers.OWNER)))
                .andExpect(jsonPath("$.content.length()").value(5));

        assertThat(queries.statements()).isLessThanOrEqualTo(2);
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions send(MockHttpServletRequestBuilder request, RequestPostProcessor user, String json) throws Exception {
        return mockMvc.perform(request.with(user).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createJson(long assigneeId, String partsJson) {
        return """
                {"appointmentId": %d, "assignedUserId": %d, "description": "Oil change", "laborCost": 150.00,
                 "parts": %s}""".formatted(appointmentId, assigneeId, partsJson);
    }

    private static String partJson(long partId, int quantity) {
        return """
                {"partId": %d, "quantity": %d}""".formatted(partId, quantity);
    }
}
