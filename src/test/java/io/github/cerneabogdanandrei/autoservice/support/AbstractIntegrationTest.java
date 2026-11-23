package io.github.cerneabogdanandrei.autoservice.support;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for integration tests: full Spring context on a random port, MockMvc, a real
 * PostgreSQL database (with the dev seed users) and real JWT authentication.
 *
 * <p>All subclasses share one cached context and therefore one container. The database is
 * emptied before every test (seed users excepted) instead of relying on transaction rollback,
 * so tests exercise real commits and real transaction boundaries.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    /** Generated per test run, so no signing secret is ever stored in the repository. */
    private static final String JWT_SECRET = randomSecret();

    private static final Map<TestUsers, String> TOKENS = new EnumMap<>(TestUsers.class);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    protected TestData data;

    protected QueryCounter queries;

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("app.security.jwt.secret", () -> JWT_SECRET);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE intervention_parts, interventions, appointments, vehicles, clients, parts
                RESTART IDENTITY""");
        jdbcTemplate.update("DELETE FROM users WHERE username NOT IN ('admin', 'owner', 'employee')");
        data = new TestData(jdbcTemplate);
        queries = new QueryCounter(entityManagerFactory);
    }

    /** Logs in through the real endpoint; tokens are cached per user for the whole test run. */
    protected String tokenFor(TestUsers user) {
        return TOKENS.computeIfAbsent(user, seeded -> login(seeded.username(), seeded.password()));
    }

    /** Logs in a user created by the test itself (not cached: its id changes between tests). */
    protected RequestPostProcessor asUser(String username, String password) {
        return bearer(login(username, password));
    }

    protected RequestPostProcessor as(TestUsers user) {
        return bearer(tokenFor(user));
    }

    protected Long idOf(TestUsers user) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, user.username());
    }

    protected static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    private String login(String username, String password) {
        try {
            String body = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "%s", "password": "%s"}""".formatted(username, password)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            return JsonPath.read(body, "$.accessToken");
        } catch (Exception e) {
            throw new IllegalStateException("Login failed for " + username, e);
        }
    }

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
