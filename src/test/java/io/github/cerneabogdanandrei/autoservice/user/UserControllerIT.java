package io.github.cerneabogdanandrei.autoservice.user;

import com.jayway.jsonpath.JsonPath;
import io.github.cerneabogdanandrei.autoservice.support.AbstractIntegrationTest;
import io.github.cerneabogdanandrei.autoservice.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIT extends AbstractIntegrationTest {

    private static final String NEW_USER = """
            {"username": "Ion.Popescu", "password": "Secret123!", "fullName": "Ion Popescu", "role": "EMPLOYEE"}""";

    // ------------------------------------------------------------------ happy path

    @Test
    void adminManagesTheFullUserLifecycle() throws Exception {
        var created = createUser(NEW_USER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("ion.popescu"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse();
        int id = JsonPath.read(created.getContentAsString(), "$.id");
        assertThat(created.getHeader("Location")).endsWith("/api/users/" + id);

        mockMvc.perform(get("/api/users/{id}", id).with(as(TestUsers.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ion Popescu"));

        mockMvc.perform(put("/api/users/{id}", id).with(as(TestUsers.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Ion Popescu Jr", "role": "OWNER", "enabled": true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ion Popescu Jr"))
                .andExpect(jsonPath("$.role").value("OWNER"));

        mockMvc.perform(put("/api/users/{id}/password", id).with(as(TestUsers.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "BrandNew123!"}"""))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "ion.popescu", "password": "BrandNew123!"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/{id}", id).with(as(TestUsers.ADMIN)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/{id}", id).with(as(TestUsers.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void listIsPagedSortedAndFilterable() throws Exception {
        mockMvc.perform(get("/api/users").with(as(TestUsers.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username").value(hasItems("admin", "employee", "owner")))
                .andExpect(jsonPath("$.content[0].username").value("admin"))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.size").value(20));

        mockMvc.perform(get("/api/users").param("role", "EMPLOYEE").with(as(TestUsers.ADMIN)))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("employee"));

        mockMvc.perform(get("/api/users").param("sort", "username,desc").param("size", "1").with(as(TestUsers.ADMIN)))
                .andExpect(jsonPath("$.content[0].username").value("owner"))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    // ------------------------------------------------------------------ 400

    @Test
    void invalidUserIsRejectedWithFieldErrors() throws Exception {
        createUser("""
                {"username": "x", "password": "short", "fullName": "", "role": null}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("username", "password", "fullName", "role")));
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        createUser("""
                {"username": "someone", "password": "Secret123!", "fullName": "Some One", "role": "ANGAJAT"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Invalid or missing value for field 'role'."));
    }

    @Test
    void unknownSortPropertyIsRejected() throws Exception {
        mockMvc.perform(get("/api/users").param("sort", "passwordz").with(as(TestUsers.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    // ------------------------------------------------------------------ 401 / 403

    @Test
    void anonymousRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @EnumSource(value = TestUsers.class, names = {"OWNER", "EMPLOYEE"})
    void onlyAdminsCanManageUsers(TestUsers user) throws Exception {
        mockMvc.perform(get("/api/users").with(as(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/users").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(NEW_USER))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/{id}", idOf(TestUsers.EMPLOYEE)).with(as(user)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 404 / 409

    @Test
    void unknownUserReturns404() throws Exception {
        mockMvc.perform(get("/api/users/{id}", 999).with(as(TestUsers.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User with id 999 was not found"));
    }

    @Test
    void duplicateUsernameIsRejectedIgnoringCase() throws Exception {
        createUser("""
                {"username": "EMPLOYEE", "password": "Secret123!", "fullName": "Clone", "role": "EMPLOYEE"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void adminCannotDeleteOrDisableOwnAccount() throws Exception {
        Long adminId = idOf(TestUsers.ADMIN);

        mockMvc.perform(delete("/api/users/{id}", adminId).with(as(TestUsers.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWN_ACCOUNT_PROTECTED"));
        mockMvc.perform(put("/api/users/{id}", adminId).with(as(TestUsers.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Ana Admin", "role": "ADMIN", "enabled": false}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWN_ACCOUNT_PROTECTED"));
    }

    @Test
    void userWithAssignedInterventionsCannotBeDeleted() throws Exception {
        Long employeeId = idOf(TestUsers.EMPLOYEE);
        Long clientId = jdbcTemplate.queryForObject(
                "INSERT INTO clients (name, phone, email) VALUES ('C', '+40700000000', 'c@example.com') RETURNING id", Long.class);
        Long vehicleId = jdbcTemplate.queryForObject("""
                INSERT INTO vehicles (client_id, vin, license_plate, make, model, manufacture_year)
                VALUES (?, 'WVWZZZ1JZXW000001', 'B-01-ABC', 'VW', 'Golf', 2019) RETURNING id""", Long.class, clientId);
        Long appointmentId = jdbcTemplate.queryForObject("""
                INSERT INTO appointments (vehicle_id, start_at, end_at, status)
                VALUES (?, now(), now() + interval '1 hour', 'IN_PROGRESS') RETURNING id""", Long.class, vehicleId);
        jdbcTemplate.update("""
                INSERT INTO interventions (appointment_id, assigned_user_id, description, labor_cost)
                VALUES (?, ?, 'Oil change', 100)""", appointmentId, employeeId);

        mockMvc.perform(delete("/api/users/{id}", employeeId).with(as(TestUsers.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_HAS_INTERVENTIONS"));
    }

    private ResultActions createUser(String json) throws Exception {
        return mockMvc.perform(post("/api/users").with(as(TestUsers.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
