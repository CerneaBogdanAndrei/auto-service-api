package io.github.cerneabogdanandrei.autoservice.part;

import com.jayway.jsonpath.JsonPath;
import io.github.cerneabogdanandrei.autoservice.support.AbstractIntegrationTest;
import io.github.cerneabogdanandrei.autoservice.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

class PartControllerIT extends AbstractIntegrationTest {

    private static final String PART = """
            {"code": "flt-001", "name": "Oil filter", "unitPrice": 49.9, "stock": 10}""";

    @Test
    void ownerManagesTheFullPartLifecycle() throws Exception {
        var created = send(post("/api/parts"), TestUsers.OWNER, PART)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FLT-001"))
                .andExpect(jsonPath("$.unitPrice").value(49.90))
                .andExpect(jsonPath("$.stock").value(10))
                .andReturn().getResponse();
        int id = JsonPath.read(created.getContentAsString(), "$.id");
        assertThat(created.getContentAsString()).contains("\"unitPrice\":49.90");
        assertThat(created.getHeader("Location")).endsWith("/api/parts/" + id);

        send(put("/api/parts/{id}", id), TestUsers.OWNER, """
                {"code": "FLT-001", "name": "Oil filter (premium)", "unitPrice": 59.00, "stock": 25}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Oil filter (premium)"))
                .andExpect(jsonPath("$.stock").value(25));

        mockMvc.perform(delete("/api/parts/{id}", id).with(as(TestUsers.OWNER))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/parts/{id}", id).with(as(TestUsers.OWNER))).andExpect(status().isNotFound());
    }

    @Test
    void partsCanBeFilteredByLowStock() throws Exception {
        data.part("BRK-001", "120.00", 2);
        data.part("FLT-001", "49.90", 4);
        data.part("OIL-5W30", "35.00", 5);
        data.part("SPK-001", "25.00", 40);

        mockMvc.perform(get("/api/parts").param("stockBelow", "5").with(as(TestUsers.EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].code").value("BRK-001"))
                .andExpect(jsonPath("$.content[1].code").value("FLT-001"));
    }

    @Test
    void negativeStockThresholdIsRejected() throws Exception {
        mockMvc.perform(get("/api/parts").param("stockBelow", "-1").with(as(TestUsers.OWNER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("stockBelow"));
    }

    @Test
    void invalidPartIsRejectedWithFieldErrors() throws Exception {
        send(post("/api/parts"), TestUsers.OWNER, """
                {"code": "bad code!", "name": "", "unitPrice": -1, "stock": -5}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("code", "name", "unitPrice", "stock")));
        send(post("/api/parts"), TestUsers.OWNER, """
                {"code": "FLT-001", "name": "Oil filter", "unitPrice": 49.999, "stock": 1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("unitPrice"));
    }

    @Test
    void anonymousRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/parts")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCanReadButNotModifyParts() throws Exception {
        long partId = data.part("FLT-001", "49.90", 10);

        mockMvc.perform(get("/api/parts/{id}", partId).with(as(TestUsers.EMPLOYEE))).andExpect(status().isOk());
        send(post("/api/parts"), TestUsers.EMPLOYEE, PART).andExpect(status().isForbidden());
        send(put("/api/parts/{id}", partId), TestUsers.EMPLOYEE, PART).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/parts/{id}", partId).with(as(TestUsers.EMPLOYEE))).andExpect(status().isForbidden());
        assertThat(data.stockOf(partId)).isEqualTo(10);
    }

    @Test
    void unknownPartReturns404() throws Exception {
        mockMvc.perform(get("/api/parts/{id}", 404).with(as(TestUsers.OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Part with id 404 was not found"));
    }

    @Test
    void duplicateCodeIsRejectedIgnoringCase() throws Exception {
        data.part("FLT-001", "49.90", 10);

        send(post("/api/parts"), TestUsers.OWNER, PART)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PART_CODE_ALREADY_EXISTS"));
    }

    @Test
    void partUsedByAnInterventionCannotBeDeleted() throws Exception {
        long partId = data.part("FLT-001", "49.90", 10);
        long vehicleId = data.vehicle(data.client());
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        long appointmentId = data.appointment(vehicleId, start, start.plus(1, ChronoUnit.HOURS), "COMPLETED");
        long interventionId = data.intervention(appointmentId, idOf(TestUsers.EMPLOYEE), "100.00");
        data.interventionPart(interventionId, partId, 1, "49.90");

        mockMvc.perform(delete("/api/parts/{id}", partId).with(as(TestUsers.OWNER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PART_IN_USE"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, TestUsers user, String json) throws Exception {
        return mockMvc.perform(request.with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
