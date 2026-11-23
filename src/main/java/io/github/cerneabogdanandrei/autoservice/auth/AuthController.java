package io.github.cerneabogdanandrei.autoservice.auth;

import io.github.cerneabogdanandrei.autoservice.auth.dto.CurrentUserResponse;
import io.github.cerneabogdanandrei.autoservice.auth.dto.LoginRequest;
import io.github.cerneabogdanandrei.autoservice.auth.dto.TokenResponse;
import io.github.cerneabogdanandrei.autoservice.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in with username and password and obtain a JWT access token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Return the user the access token belongs to")
    public CurrentUserResponse me(AuthenticatedUser user) {
        return new CurrentUserResponse(user.id(), user.username(), user.role());
    }
}
