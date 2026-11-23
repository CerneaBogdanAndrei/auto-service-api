package io.github.cerneabogdanandrei.autoservice.appointment;

import com.jayway.jsonpath.JsonPath;
import io.github.cerneabogdanandrei.autoservice.support.AbstractIntegrationTest;
import io.github.cerneabogdanandrei.autoservice.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentControllerIT extends AbstractIntegrationTest {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Europe/Bucharest");

    @LocalServerPort
    private int port;

    // ------------------------------------------------------------------ happy path

    @Test
    void ownerSchedulesWorksOnAndCompletesAnAppointment() throws Exception {
        long vehicleId = data.vehicle(data.client());
        OffsetDateTime start = tomorrowAt(9);

        var created = send(post("/api/appointments"), TestUsers.OWNER, json(vehicleId, start, start.plusHours(2), "Oil change"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.vehicle.id").value(vehicleId))
                .andExpect(jsonPath("$.startAt").value(start.toInstant().toString()))
                .andExpect(jsonPath("$.notes").value("Oil change"))
                .andExpect(jsonPath("$.cost.total").value(0.0))
                .andReturn().getResponse();
        int id = JsonPath.read(created.getContentAsString(), "$.id");
        assertThat(created.getHeader("Location")).endsWith("/api/appointments/" + id);

        changeStatus(id, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
        changeStatus(id, "COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    void scheduledAppointmentCanBeRescheduledAndDeleted() throws Exception {
        long vehicleId = data.vehicle(data.client());
        long id = scheduled(vehicleId, tomorrowAt(9));
        OffsetDateTime later = tomorrowAt(14);

        send(put("/api/appointments/{id}", id), TestUsers.ADMIN, json(vehicleId, later, later.plusHours(1), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").value(later.toInstant().toString()))
                .andExpect(jsonPath("$.notes").doesNotExist());

        mockMvc.perform(delete("/api/appointments/{id}", id).with(as(TestUsers.ADMIN))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/appointments/{id}", id).with(as(TestUsers.ADMIN))).andExpect(status().isNotFound());
    }

    @Test
    void costIsLabourPlusPartsOfAllInterventions() throws Exception {
        long vehicleId = data.vehicle(data.client());
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
        long appointmentId = data.appointment(vehicleId, start, start.plus(3, ChronoUnit.HOURS), "IN_PROGRESS");
        long employee = idOf(TestUsers.EMPLOYEE);
        long first = data.intervention(appointmentId, employee, "150.00");
        long second = data.intervention(appointmentId, employee, "100.50");
        data.interventionPart(first, data.part("FLT-001", "49.90", 10), 2, "49.90");
        data.interventionPart(second, data.part("OIL-5W30", "35.00", 10), 1, "35.00");

        String body = mockMvc.perform(get("/api/appointments/{id}", appointmentId).with(as(TestUsers.OWNER)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"cost\":{\"labor\":250.50,\"parts\":134.80,\"total\":385.30}");
    }

    // ------------------------------------------------------------------ list & filters

    @Test
    void listCanBeFilteredByShopDaysStatusAndVehicle() throws Exception {
        long vehicleId = data.vehicle(data.client());
        long otherVehicle = data.vehicle(data.client());
        LocalDate day = LocalDate.now(SHOP_ZONE).plusDays(10);
        // 23:30 on `day` and 00:30 on the next day, shop time: different days locally, same day in UTC
        Instant lateEvening = day.atTime(23, 30).atZone(SHOP_ZONE).toInstant();
        Instant afterMidnight = day.plusDays(1).atTime(0, 30).atZone(SHOP_ZONE).toInstant();
        data.appointment(vehicleId, lateEvening, lateEvening.plus(30, ChronoUnit.MINUTES), "SCHEDULED");
        data.appointment(vehicleId, afterMidnight, afterMidnight.plus(1, ChronoUnit.HOURS), "CANCELLED");
        data.appointment(otherVehicle, afterMidnight, afterMidnight.plus(1, ChronoUnit.HOURS), "SCHEDULED");

        list("from", day, "to", day).andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].startAt").value(lateEvening.toString()));
        list("from", day.plusDays(1), "to", day.plusDays(1)).andExpect(jsonPath("$.page.totalElements").value(2));
        list("status", "CANCELLED").andExpect(jsonPath("$.page.totalElements").value(1));
        list("vehicleId", otherVehicle).andExpect(jsonPath("$.page.totalElements").value(1));
        list("from", day, "status", "SCHEDULED").andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void dateRangeEndingBeforeItStartsIsRejected() throws Exception {
        list("from", "2030-02-01", "to", "2030-01-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void listingFetchesVehiclesInTheSameQuery() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        for (int i = 0; i < 5; i++) {
            data.appointment(data.vehicle(data.client()), start, start.plus(1, ChronoUnit.HOURS), "SCHEDULED");
        }

        queries.reset();
        list().andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[4].vehicle.vin").isNotEmpty());

        assertThat(queries.statements()).isLessThanOrEqualTo(2);
    }

    // ------------------------------------------------------------------ 400 / 401 / 403 / 404

    @Test
    void invalidAppointmentIsRejected() throws Exception {
        long vehicleId = data.vehicle(data.client());
        OffsetDateTime start = tomorrowAt(9);

        send(post("/api/appointments"), TestUsers.OWNER, json(vehicleId, start, start.minusHours(1), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endAfterStart"))
                .andExpect(jsonPath("$.errors[0].message").value("endAt must be after startAt"));
        send(post("/api/appointments"), TestUsers.OWNER, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void anonymousRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/appointments")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeesHaveNoAccessToAppointments() throws Exception {
        long vehicleId = data.vehicle(data.client());
        long id = scheduled(vehicleId, tomorrowAt(9));

        mockMvc.perform(get("/api/appointments").with(as(TestUsers.EMPLOYEE))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/appointments/{id}", id).with(as(TestUsers.EMPLOYEE))).andExpect(status().isForbidden());
        send(patch("/api/appointments/{id}/status", id), TestUsers.EMPLOYEE, "{\"status\": \"IN_PROGRESS\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void unknownAppointmentOrVehicleReturns404() throws Exception {
        mockMvc.perform(get("/api/appointments/{id}", 404).with(as(TestUsers.OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Appointment with id 404 was not found"));
        OffsetDateTime start = tomorrowAt(9);
        send(post("/api/appointments"), TestUsers.OWNER, json(404, start, start.plusHours(1), null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Vehicle with id 404 was not found"));
    }

    // ------------------------------------------------------------------ 409 business rules

    @Test
    void appointmentCannotStartInThePast() throws Exception {
        long vehicleId = data.vehicle(data.client());
        OffsetDateTime yesterday = tomorrowAt(9).minusDays(2);

        send(post("/api/appointments"), TestUsers.OWNER, json(vehicleId, yesterday, yesterday.plusHours(1), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_IN_PAST"));
    }

    @Test
    void overlappingAppointmentsForTheSameVehicleAreRejected() throws Exception {
        long vehicleId = data.vehicle(data.client());
        OffsetDateTime start = tomorrowAt(9);
        scheduled(vehicleId, start);

        send(post("/api/appointments"), TestUsers.OWNER, json(vehicleId, start.plusMinutes(30), start.plusHours(3), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_OVERLAP"));
        // back-to-back is fine: intervals are half-open
        send(post("/api/appointments"), TestUsers.OWNER, json(vehicleId, start.plusHours(2), start.plusHours(3), null))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelledAppointmentFreesTheSlot() throws Exception {
        long vehicleId = data.vehicle(data.client());
        OffsetDateTime start = tomorrowAt(9);
        long id = scheduled(vehicleId, start);
        changeStatus(id, "CANCELLED").andExpect(status().isOk());

        send(post("/api/appointments"), TestUsers.OWNER, json(vehicleId, start, start.plusHours(2), null))
                .andExpect(status().isCreated());
    }

    @Test
    void concurrentRequestsForTheSameSlotBookItExactlyOnce() throws Exception {
        long vehicleId = data.vehicle(data.client());
        OffsetDateTime start = tomorrowAt(9);
        String body = json(vehicleId, start, start.plusHours(2), null);
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(TestUsers.OWNER))
                .build();
        int requests = 8;
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                Callable<Integer> call = () -> {
                    startGate.await();
                    return client.post().uri("/api/appointments").contentType(MediaType.APPLICATION_JSON).body(body)
                            .exchange((request, response) -> response.getStatusCode().value());
                };
                results.add(pool.submit(call));
            }
            startGate.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }

            // Whether the service check or the database EXCLUDE constraint catches a request
            // depends on timing, but the outcome must always be exactly one booking.
            assertThat(statuses).containsOnly(201, 409).filteredOn(code -> code == 201).hasSize(1);
        }
        assertThat(data.count("appointments")).isEqualTo(1);
    }

    @Test
    void forbiddenStatusTransitionsReturn409() throws Exception {
        long vehicleId = data.vehicle(data.client());
        long id = scheduled(vehicleId, tomorrowAt(9));

        changeStatus(id, "COMPLETED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.currentStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.allowedStatuses[0]").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.allowedStatuses[1]").value("CANCELLED"));

        changeStatus(id, "CANCELLED").andExpect(status().isOk());
        changeStatus(id, "IN_PROGRESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"IN_PROGRESS", "COMPLETED"})
    void appointmentWithWorkCannotBeRescheduledOrDeleted(AppointmentStatus status) throws Exception {
        long vehicleId = data.vehicle(data.client());
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        long id = data.appointment(vehicleId, start, start.plus(2, ChronoUnit.HOURS), status.name());
        OffsetDateTime later = tomorrowAt(9);

        send(put("/api/appointments/{id}", id), TestUsers.OWNER, json(vehicleId, later, later.plusHours(1), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_MODIFIABLE"));
        mockMvc.perform(delete("/api/appointments/{id}", id).with(as(TestUsers.OWNER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_MODIFIABLE"));
    }

    // ------------------------------------------------------------------ helpers

    private long scheduled(long vehicleId, OffsetDateTime start) throws Exception {
        String body = send(post("/api/appointments"), TestUsers.OWNER, json(vehicleId, start, start.plusHours(2), null))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions changeStatus(long id, String status) throws Exception {
        return send(patch("/api/appointments/{id}/status", id), TestUsers.OWNER, "{\"status\": \"%s\"}".formatted(status));
    }

    private ResultActions list(Object... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/appointments").with(as(TestUsers.OWNER));
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i].toString(), params[i + 1].toString());
        }
        return mockMvc.perform(request);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, TestUsers user, String json) throws Exception {
        return mockMvc.perform(request.with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static OffsetDateTime tomorrowAt(int hour) {
        return LocalDate.now(SHOP_ZONE).plusDays(1).atTime(hour, 0).atZone(SHOP_ZONE).toOffsetDateTime();
    }

    private static String json(long vehicleId, OffsetDateTime start, OffsetDateTime end, String notes) {
        return """
                {"vehicleId": %d, "startAt": "%s", "endAt": "%s", "notes": %s}""".formatted(
                vehicleId, start, end, notes == null ? "null" : "\"" + notes + "\"");
    }
}
