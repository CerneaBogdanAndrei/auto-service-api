package io.github.cerneabogdanandrei.autoservice.auth;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.cerneabogdanandrei.autoservice.common.security.AuthenticatedUser;
import io.github.cerneabogdanandrei.autoservice.common.security.Role;
import io.github.cerneabogdanandrei.autoservice.support.AbstractIntegrationTest;
import io.github.cerneabogdanandrei.autoservice.user.User;
import io.github.cerneabogdanandrei.autoservice.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AbstractIntegrationTest {

    private static final String DISABLED_USERNAME = "disabled.user";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private Clock clock;

    @AfterEach
    void removeDisabledUser() {
        userRepository.findByUsername(DISABLED_USERNAME).ifPresent(userRepository::delete);
    }

    // ------------------------------------------------------------------ login

    @ParameterizedTest
    @CsvSource({"admin, Admin123!, ADMIN", "owner, Owner123!, OWNER", "employee, Employee123!, EMPLOYEE"})
    void seededUserCanLogInAndUseTheToken(String username, String password, Role role) throws Exception {
        String body = login(username, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/auth/me").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value(role.name()));
    }

    @Test
    void usernameIsCaseInsensitive() throws Exception {
        login("ADMIN", "Admin123!").andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        expectInvalidCredentials(login("admin", "wrong-password"));
    }

    @Test
    void unknownUserGetsTheSameAnswerAsWrongPassword() throws Exception {
        expectInvalidCredentials(login("nobody", "Admin123!"));
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        User disabled = new User(DISABLED_USERNAME, passwordEncoder.encode("Secret123!"), "Disabled User", Role.EMPLOYEE);
        disabled.setEnabled(false);
        userRepository.save(disabled);

        expectInvalidCredentials(login(DISABLED_USERNAME, "Secret123!"));
    }

    @Test
    void blankCredentialsAreRejectedWithFieldErrors() throws Exception {
        login(" ", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[1].field").value("username"));
    }

    // ------------------------------------------------------------------ protected endpoints

    @Test
    void requestWithoutTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."))
                .andExpect(jsonPath("$.instance").value("/api/auth/me"));
    }

    @Test
    void malformedTokenIsRejectedWith401() throws Exception {
        expectInvalidToken(mockMvc.perform(get("/api/auth/me").with(bearer("not-a-jwt"))));
    }

    @Test
    void expiredTokenIsRejectedWith401() throws Exception {
        Instant now = Instant.now(clock);
        String token = encode(jwtEncoder, jwtProperties.issuer(),
                now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)));

        expectInvalidToken(mockMvc.perform(get("/api/auth/me").with(bearer(token))));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejectedWith401() throws Exception {
        JwtEncoder foreignEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "a-completely-different-secret-of-sufficient-length".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now(clock);
        String token = encode(foreignEncoder, jwtProperties.issuer(), now, now.plus(Duration.ofHours(1)));

        expectInvalidToken(mockMvc.perform(get("/api/auth/me").with(bearer(token))));
    }

    @Test
    void tokenFromAnotherIssuerIsRejectedWith401() throws Exception {
        Instant now = Instant.now(clock);
        String token = encode(jwtEncoder, "someone-else", now, now.plus(Duration.ofHours(1)));

        expectInvalidToken(mockMvc.perform(get("/api/auth/me").with(bearer(token))));
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}""".formatted(username, password)));
    }

    private static void expectInvalidCredentials(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password."))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    private static void expectInvalidToken(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("The access token is invalid or has expired."));
    }

    private static String encode(JwtEncoder encoder, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject("admin")
                .claim(AuthenticatedUser.USER_ID_CLAIM, 1L)
                .claim(AuthenticatedUser.ROLE_CLAIM, Role.ADMIN.name())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
