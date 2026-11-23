package io.github.cerneabogdanandrei.autoservice.vehicle;

import com.jayway.jsonpath.JsonPath;
import io.github.cerneabogdanandrei.autoservice.support.AbstractIntegrationTest;
import io.github.cerneabogdanandrei.autoservice.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.Year;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VehicleControllerIT extends AbstractIntegrationTest {

    @Test
    void ownerManagesTheFullVehicleLifecycle() throws Exception {
        long clientId = data.client("John Doe", "john@example.com");
        long otherClientId = data.client("Jane Roe", "jane@example.com");

        var created = send(post("/api/vehicles"), TestUsers.OWNER, vehicleJson(clientId, "wvwzzz1jzxw000001", 2019))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vin").value("WVWZZZ1JZXW000001"))
                .andExpect(jsonPath("$.licensePlate").value("B-123-ABC"))
                .andExpect(jsonPath("$.client.id").value(clientId))
                .andExpect(jsonPath("$.client.name").value("John Doe"))
                .andReturn().getResponse();
        int id = JsonPath.read(created.getContentAsString(), "$.id");
        assertThat(created.getHeader("Location")).endsWith("/api/vehicles/" + id);

        send(put("/api/vehicles/{id}", id), TestUsers.OWNER, vehicleJson(otherClientId, "WVWZZZ1JZXW000001", 2020))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.name").value("Jane Roe"))
                .andExpect(jsonPath("$.manufactureYear").value(2020));

        mockMvc.perform(delete("/api/vehicles/{id}", id).with(as(TestUsers.OWNER)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/vehicles/{id}", id).with(as(TestUsers.OWNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void vehiclesCanBeFilteredByClient() throws Exception {
        long john = data.client();
        long jane = data.client();
        data.vehicle(john);
        data.vehicle(john);
        data.vehicle(jane);

        mockMvc.perform(get("/api/vehicles").param("clientId", String.valueOf(john)).with(as(TestUsers.EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].client.id").value(john))
                .andExpect(jsonPath("$.content[1].client.id").value(john));
    }

    @Test
    void listingFetchesOwnersInTheSameQueryInsteadOfOnePerVehicle() throws Exception {
        for (int i = 0; i < 5; i++) {
            data.vehicle(data.client());
        }

        queries.reset();
        mockMvc.perform(get("/api/vehicles").with(as(TestUsers.EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[4].client.name").isNotEmpty());

        // one query for the page (vehicles joined with clients); the count query is skipped
        // because the page is not full
        assertThat(queries.statements()).isLessThanOrEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WVWZZZ1JZXW00000", "WVWZZZ1JZXW0000012", "WVWZZZ1JZXW00000O", "IVWZZZ1JZXW000001", "WVWZZZ1JZXW00000Q"})
    void invalidVinIsRejected(String vin) throws Exception {
        long clientId = data.client();

        send(post("/api/vehicles"), TestUsers.OWNER, vehicleJson(clientId, vin, 2019))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("vin"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must be a 17-character VIN (digits and letters except I, O and Q)"));
    }

    @Test
    void implausibleManufactureYearIsRejected() throws Exception {
        long clientId = data.client();
        int tooNew = Year.now().getValue() + 2;

        send(post("/api/vehicles"), TestUsers.OWNER, vehicleJson(clientId, "WVWZZZ1JZXW000001", tooNew))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("manufactureYear"));
        send(post("/api/vehicles"), TestUsers.OWNER, vehicleJson(clientId, "WVWZZZ1JZXW000001", 1885))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("must be between 1886 and next year"));
    }

    @Test
    void anonymousRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/vehicles")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCanReadButNotModifyVehicles() throws Exception {
        long clientId = data.client();
        long vehicleId = data.vehicle(clientId);

        mockMvc.perform(get("/api/vehicles/{id}", vehicleId).with(as(TestUsers.EMPLOYEE))).andExpect(status().isOk());
        send(post("/api/vehicles"), TestUsers.EMPLOYEE, vehicleJson(clientId, "WVWZZZ1JZXW000777", 2019))
                .andExpect(status().isForbidden());
        send(put("/api/vehicles/{id}", vehicleId), TestUsers.EMPLOYEE, vehicleJson(clientId, "WVWZZZ1JZXW000777", 2019))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/vehicles/{id}", vehicleId).with(as(TestUsers.EMPLOYEE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownVehicleOrClientReturns404() throws Exception {
        mockMvc.perform(get("/api/vehicles/{id}", 404).with(as(TestUsers.OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Vehicle with id 404 was not found"));
        send(post("/api/vehicles"), TestUsers.OWNER, vehicleJson(404, "WVWZZZ1JZXW000001", 2019))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Client with id 404 was not found"));
    }

    @Test
    void duplicateVinIsRejectedIgnoringCase() throws Exception {
        long clientId = data.client();
        data.vehicle(clientId, "WVWZZZ1JZXW000001");

        send(post("/api/vehicles"), TestUsers.OWNER, vehicleJson(clientId, "wvwzzz1jzxw000001", 2019))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VIN_ALREADY_EXISTS"));
    }

    @Test
    void vehicleWithAnyAppointmentCannotBeDeleted() throws Exception {
        long vehicleId = data.vehicle(data.client());
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        data.appointment(vehicleId, start, start.plus(2, ChronoUnit.HOURS), "COMPLETED");

        mockMvc.perform(delete("/api/vehicles/{id}", vehicleId).with(as(TestUsers.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VEHICLE_HAS_APPOINTMENTS"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, TestUsers user, String json) throws Exception {
        return mockMvc.perform(request.with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String vehicleJson(long clientId, String vin, int year) {
        return """
                {"clientId": %d, "vin": "%s", "licensePlate": "b-123-abc", "make": "Volkswagen",
                 "model": "Golf", "manufactureYear": %d}""".formatted(clientId, vin, year);
    }
}
