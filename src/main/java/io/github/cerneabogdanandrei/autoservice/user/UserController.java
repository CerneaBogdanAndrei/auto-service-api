package io.github.cerneabogdanandrei.autoservice.user;

import io.github.cerneabogdanandrei.autoservice.common.security.AuthenticatedUser;
import io.github.cerneabogdanandrei.autoservice.common.security.Role;
import io.github.cerneabogdanandrei.autoservice.user.dto.PasswordChangeRequest;
import io.github.cerneabogdanandrei.autoservice.user.dto.UserCreateRequest;
import io.github.cerneabogdanandrei.autoservice.user.dto.UserResponse;
import io.github.cerneabogdanandrei.autoservice.user.dto.UserUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "User management (ADMIN only)")
public class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List users, optionally filtered by role and status")
    public PagedModel<UserResponse> findAll(@RequestParam(required = false) Role role,
                                            @RequestParam(required = false) Boolean enabled,
                                            @ParameterObject @SortDefault("username") Pageable pageable) {
        return new PagedModel<>(userService.findAll(role, enabled, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a user")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        UserResponse created = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user's name, role and status")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request,
                               AuthenticatedUser currentUser) {
        return userService.update(id, request, currentUser);
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Set a new password for a user")
    public ResponseEntity<Void> changePassword(@PathVariable Long id, @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a user that has no assigned interventions")
    public ResponseEntity<Void> delete(@PathVariable Long id, AuthenticatedUser currentUser) {
        userService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
