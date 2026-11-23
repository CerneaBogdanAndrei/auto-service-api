package io.github.cerneabogdanandrei.autoservice.auth;

import io.github.cerneabogdanandrei.autoservice.auth.dto.LoginRequest;
import io.github.cerneabogdanandrei.autoservice.auth.dto.TokenResponse;
import io.github.cerneabogdanandrei.autoservice.common.security.Role;
import io.github.cerneabogdanandrei.autoservice.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private TokenService tokenService;
    @InjectMocks
    private AuthService authService;

    @Test
    void loginAuthenticatesCredentialsAndIssuesBearerToken() {
        AuthUserDetails owner = new AuthUserDetails(user(5L, "owner", Role.OWNER));
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(owner, null, owner.getAuthorities()));
        when(tokenService.issue(5L, "owner", Role.OWNER)).thenReturn(new TokenService.IssuedToken("signed.jwt", 3600));

        TokenResponse response = authService.login(new LoginRequest("owner", "Owner123!"));

        assertThat(response).isEqualTo(new TokenResponse("signed.jwt", "Bearer", 3600));
        ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(attempt.capture());
        assertThat(attempt.getValue().getPrincipal()).isEqualTo("owner");
        assertThat(attempt.getValue().getCredentials()).isEqualTo("Owner123!");
        assertThat(attempt.getValue().isAuthenticated()).isFalse();
    }

    @Test
    void failedAuthenticationPropagatesAndIssuesNoToken() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("owner", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(tokenService);
    }

    static User user(Long id, String username, Role role) {
        User user = new User(username, "$2a$10$hash", "Test User", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
