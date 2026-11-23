package io.github.cerneabogdanandrei.autoservice.auth;

import io.github.cerneabogdanandrei.autoservice.auth.dto.LoginRequest;
import io.github.cerneabogdanandrei.autoservice.auth.dto.TokenResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthService(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    /**
     * Verifies the credentials (BCrypt) and issues an access token.
     *
     * @throws org.springframework.security.core.AuthenticationException for unknown users,
     *         wrong passwords or disabled accounts (all reported to the client identically)
     */
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        AuthUserDetails user = (AuthUserDetails) authentication.getPrincipal();
        TokenService.IssuedToken token = tokenService.issue(user.getId(), user.getUsername(), user.getRole());
        return TokenResponse.bearer(token.value(), token.expiresInSeconds());
    }
}
