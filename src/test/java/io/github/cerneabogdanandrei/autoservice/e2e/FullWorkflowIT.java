package io.github.cerneabogdanandrei.autoservice.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.cerneabogdanandrei.autoservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The complete business flow over real HTTP (random port, full security filter chain, real
 * PostgreSQL), as a client of the API would drive it:
 * login → client → vehicle → appointment → intervention with parts → completion →
 * stock, total cost and revenue checks.
 */
class FullWorkflowIT extends AbstractIntegrationTest {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Europe/Bucharest");

    @LocalServerPort
    private int port;

    private RestClient http;

    @BeforeEach
    void httpClient() {
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void fromLoginToCompletedAndInvoicedAppointment() {
        // 1. Staff log in
        String owner = login("owner", "Owner123!");
        String employee = login("employee", "Employee123!");
        long employeeId = call(employee, HttpMethod.GET, "/api/auth/me", null, HttpStatus.OK).get("id").asLong();

        // 2. The owner registers a client and their car
        long clientId = call(owner, HttpMethod.POST, "/api/clients", Map.of(
                "name", "Andrei Popescu", "phone", "+40 721 000 111", "email", "andrei.popescu@example.com"),
                HttpStatus.CREATED).get("id").asLong();
        long vehicleId = call(owner, HttpMethod.POST, "/api/vehicles", Map.of(
                "clientId", clientId, "vin", "UU1HSDAG512345678", "licensePlate", "B-77-POP",
                "make", "Dacia", "model", "Logan", "manufactureYear", 2021),
                HttpStatus.CREATED).get("id").asLong();

        // 3. ...stocks the parts it will need...
        long filterId = createPart(owner, "FLT-LOGAN", "49.90", 10);
        long oilId = createPart(owner, "OIL-5W30", "35.00", 5);

        // 4. ...and books the car in for tomorrow morning
        OffsetDateTime start = LocalDate.now(SHOP_ZONE).plusDays(1).atTime(9, 0).atZone(SHOP_ZONE).toOffsetDateTime();
        JsonNode appointment = call(owner, HttpMethod.POST, "/api/appointments", Map.of(
                "vehicleId", vehicleId, "startAt", start.toString(), "endAt", start.plusHours(2).toString(),
                "notes", "Oil service"), HttpStatus.CREATED);
        long appointmentId = appointment.get("id").asLong();
        assertThat(appointment.get("status").asText()).isEqualTo("SCHEDULED");

        // 5. The car arrives: work starts and is assigned to the employee, with two filters
        changeStatus(owner, appointmentId, "IN_PROGRESS", HttpStatus.OK);
        JsonNode intervention = call(owner, HttpMethod.POST, "/api/interventions", Map.of(
                "appointmentId", appointmentId, "assignedUserId", employeeId,
                "description", "Oil and filter change", "laborCost", "150.00",
                "parts", new Object[]{Map.of("partId", filterId, "quantity", 2)}), HttpStatus.CREATED);
        long interventionId = intervention.get("id").asLong();

        // 6. The employee adds the oil they used to their own intervention
        JsonNode updated = call(employee, HttpMethod.POST, "/api/interventions/" + interventionId + "/parts",
                Map.of("partId", oilId, "quantity", 1), HttpStatus.OK);
        assertThat(updated.get("parts")).hasSize(2);

        // 7. Work is finished
        JsonNode completed = changeStatus(owner, appointmentId, "COMPLETED", HttpStatus.OK);
        assertThat(completed.get("completedAt").isNull()).isFalse();

        // 8. Stock reflects exactly what was used...
        assertThat(call(owner, HttpMethod.GET, "/api/parts/" + filterId, null, HttpStatus.OK).get("stock").asInt()).isEqualTo(8);
        assertThat(call(owner, HttpMethod.GET, "/api/parts/" + oilId, null, HttpStatus.OK).get("stock").asInt()).isEqualTo(4);

        // ...and the total is labour + Σ quantity × unit price = 150.00 + 2 × 49.90 + 1 × 35.00
        JsonNode cost = call(owner, HttpMethod.GET, "/api/appointments/" + appointmentId, null, HttpStatus.OK).get("cost");
        assertThat(cost.get("labor").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(cost.get("parts").decimalValue()).isEqualByComparingTo("134.80");
        assertThat(cost.get("total").decimalValue()).isEqualByComparingTo("284.80");

        // 9. Completed work is frozen: no more parts, stock untouched
        JsonNode rejected = call(employee, HttpMethod.POST, "/api/interventions/" + interventionId + "/parts",
                Map.of("partId", oilId, "quantity", 1), HttpStatus.CONFLICT);
        assertThat(rejected.get("code").asText()).isEqualTo("APPOINTMENT_NOT_IN_PROGRESS");
        assertThat(call(owner, HttpMethod.GET, "/api/parts/" + oilId, null, HttpStatus.OK).get("stock").asInt()).isEqualTo(4);

        // 10. The revenue shows up in today's report
        String today = LocalDate.now(SHOP_ZONE).toString();
        JsonNode report = call(owner, HttpMethod.GET, "/api/reports/revenue?from=" + today + "&to=" + today, null, HttpStatus.OK);
        assertThat(report.get("total").decimalValue()).isEqualByComparingTo("284.80");
        assertThat(report.get("completedAppointments").asLong()).isEqualTo(1);

        // 11. The client's history now protects them from deletion
        assertThat(call(owner, HttpMethod.DELETE, "/api/clients/" + clientId, null, HttpStatus.CONFLICT)
                .get("code").asText()).isEqualTo("CLIENT_HAS_APPOINTMENTS");
    }

    // ------------------------------------------------------------------ helpers

    private String login(String username, String password) {
        return call(null, HttpMethod.POST, "/api/auth/login", Map.of("username", username, "password", password),
                HttpStatus.OK).get("accessToken").asText();
    }

    private long createPart(String token, String code, String unitPrice, int stock) {
        return call(token, HttpMethod.POST, "/api/parts", Map.of(
                "code", code, "name", "Part " + code, "unitPrice", unitPrice, "stock", stock),
                HttpStatus.CREATED).get("id").asLong();
    }

    private JsonNode changeStatus(String token, long appointmentId, String status, HttpStatus expected) {
        return call(token, HttpMethod.PATCH, "/api/appointments/" + appointmentId + "/status", Map.of("status", status), expected);
    }

    /** Sends a JSON request and asserts the response status before returning the body. */
    private JsonNode call(String token, HttpMethod method, String uri, Object body, HttpStatus expected) {
        RestClient.RequestBodySpec request = http.method(method).uri(uri).accept(MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_PROBLEM_JSON);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        ResponseEntity<JsonNode> response = request.exchange((req, res) ->
                ResponseEntity.status(res.getStatusCode()).body(res.bodyTo(JsonNode.class)));
        assertThat(response.getStatusCode())
                .as("%s %s -> %s", method, uri, response.getBody())
                .isEqualTo(expected);
        return response.getBody();
    }
}
