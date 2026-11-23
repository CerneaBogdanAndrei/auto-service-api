package io.github.cerneabogdanandrei.autoservice.client;

import com.jayway.jsonpath.JsonPath;
import io.github.cerneabogdanandrei.autoservice.support.AbstractIntegrationTest;
import io.github.cerneabogdanandrei.autoservice.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientControllerIT extends AbstractIntegrationTest {

    private static final String CLIENT = """
            {"name": "John Doe", "phone": "+40 712 345 678", "email": "John.Doe@Example.com"}""";

    @Test
    void ownerManagesTheFullClientLifecycle() throws Exception {
        var created = send(post("/api/clients"), TestUsers.OWNER, CLIENT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse();
        int id = JsonPath.read(created.getContentAsString(), "$.id");
        assertThat(created.getHeader("Location")).endsWith("/api/clients/" + id);

        send(put("/api/clients/{id}", id), TestUsers.OWNER, """
                {"name": "John A. Doe", "phone": "0712345678", "email": "john.a.doe@example.com"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John A. Doe"))
                .andExpect(jsonPath("$.email").value("john.a.doe@example.com"))
                .andExpect(result -> {
                    String json = result.getResponse().getContentAsString();
                    String createdAt = JsonPath.read(json, "$.createdAt");
                    String updatedAt = JsonPath.read(json, "$.updatedAt");
                    assertThat(java.time.Instant.parse(updatedAt)).isAfter(java.time.Instant.parse(createdAt));
                });

        mockMvc.perform(get("/api/clients/{id}", id).with(as(TestUsers.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0712345678"));

        mockMvc.perform(delete("/api/clients/{id}", id).with(as(TestUsers.OWNER)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/clients/{id}", id).with(as(TestUsers.OWNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listIsPagedAndSortedByName() throws Exception {
        data.client("Zoe", "zoe@example.com");
        data.client("Adam", "adam@example.com");
        data.client("Maria", "maria@example.com");

        mockMvc.perform(get("/api/clients").param("size", "2").with(as(TestUsers.EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Adam"))
                .andExpect(jsonPath("$.content[1].name").value("Maria"))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void invalidClientIsRejectedWithFieldErrors() throws Exception {
        send(post("/api/clients"), TestUsers.ADMIN, """
                {"name": "", "phone": "call me", "email": "not-an-email"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("name", "phone", "email")));
    }

    @Test
    void anonymousRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCanReadButNotModifyClients() throws Exception {
        long id = data.client();

        mockMvc.perform(get("/api/clients/{id}", id).with(as(TestUsers.EMPLOYEE))).andExpect(status().isOk());
        send(post("/api/clients"), TestUsers.EMPLOYEE, CLIENT)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        send(put("/api/clients/{id}", id), TestUsers.EMPLOYEE, CLIENT).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/clients/{id}", id).with(as(TestUsers.EMPLOYEE))).andExpect(status().isForbidden());
        assertThat(data.count("clients")).isEqualTo(1);
    }

    @Test
    void unknownClientReturns404() throws Exception {
        mockMvc.perform(get("/api/clients/{id}", 404).with(as(TestUsers.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        send(put("/api/clients/{id}", 404), TestUsers.ADMIN, CLIENT).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/clients/{id}", 404).with(as(TestUsers.ADMIN))).andExpect(status().isNotFound());
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCase() throws Exception {
        data.client("Existing", "john.doe@example.com");

        send(post("/api/clients"), TestUsers.OWNER, CLIENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void updatingToTheEmailOfAnotherClientIsRejected() throws Exception {
        data.client("Existing", "john.doe@example.com");
        long other = data.client("Other", "other@example.com");

        send(put("/api/clients/{id}", other), TestUsers.OWNER, CLIENT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void clientWithVehicleThatHadAnyAppointmentCannotBeDeleted() throws Exception {
        long clientId = data.client();
        long vehicleId = data.vehicle(clientId);
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        data.appointment(vehicleId, start, start.plus(1, ChronoUnit.HOURS), "CANCELLED");

        mockMvc.perform(delete("/api/clients/{id}", clientId).with(as(TestUsers.OWNER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_HAS_APPOINTMENTS"));
        assertThat(data.count("vehicles")).isEqualTo(1);
    }

    @Test
    void deletingClientRemovesVehiclesThatHaveNoHistory() throws Exception {
        long clientId = data.client();
        data.vehicle(clientId);
        data.vehicle(clientId);

        mockMvc.perform(delete("/api/clients/{id}", clientId).with(as(TestUsers.OWNER)))
                .andExpect(status().isNoContent());
        assertThat(data.count("vehicles")).isZero();
        assertThat(data.count("clients")).isZero();
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               TestUsers user, String json) throws Exception {
        return mockMvc.perform(request.with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
