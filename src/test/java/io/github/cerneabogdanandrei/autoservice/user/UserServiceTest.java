package io.github.cerneabogdanandrei.autoservice.user;

import io.github.cerneabogdanandrei.autoservice.common.exception.BusinessRuleException;
import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ResourceNotFoundException;
import io.github.cerneabogdanandrei.autoservice.common.security.AuthenticatedUser;
import io.github.cerneabogdanandrei.autoservice.common.security.Role;
import io.github.cerneabogdanandrei.autoservice.user.dto.PasswordChangeRequest;
import io.github.cerneabogdanandrei.autoservice.user.dto.UserCreateRequest;
import io.github.cerneabogdanandrei.autoservice.user.dto.UserResponse;
import io.github.cerneabogdanandrei.autoservice.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(1L, "admin", Role.ADMIN);

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, new UserMapperImpl(), passwordEncoder);
    }

    // ------------------------------------------------------------------ queries

    @Test
    @SuppressWarnings("unchecked")
    void findAllReturnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(user(2L, "mechanic", Role.EMPLOYEE)), pageable, 1));

        Page<UserResponse> page = userService.findAll(Role.EMPLOYEE, true, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).singleElement()
                .satisfies(response -> {
                    assertThat(response.username()).isEqualTo("mechanic");
                    assertThat(response.role()).isEqualTo(Role.EMPLOYEE);
                });
    }

    @Test
    void findByIdReturnsUser() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "mechanic", Role.EMPLOYEE)));

        assertThat(userService.findById(2L).fullName()).isEqualTo("Test User");
    }

    @Test
    void findByIdThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User with id 99 was not found");
    }

    // ------------------------------------------------------------------ create

    @Test
    void createNormalizesUsernameAndStoresOnlyThePasswordHash() {
        when(userRepository.existsByUsername("new.mechanic")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("$2a$10$hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.create(
                new UserCreateRequest("  New.Mechanic ", "Secret123!", " Ion Popescu ", Role.EMPLOYEE));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("new.mechanic");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(saved.getValue().getFullName()).isEqualTo("Ion Popescu");
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(response.role()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("employee")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                new UserCreateRequest("Employee", "Secret123!", "Someone", Role.EMPLOYEE)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS));
        verify(userRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ update

    @Test
    void updateChangesNameRoleAndStatus() {
        User user = user(2L, "mechanic", Role.EMPLOYEE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        UserResponse response = userService.update(2L, new UserUpdateRequest("Renamed", Role.OWNER, false), ADMIN);

        assertThat(user.getFullName()).isEqualTo("Renamed");
        assertThat(user.getRole()).isEqualTo(Role.OWNER);
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getUsername()).isEqualTo("mechanic");
        assertThat(response.enabled()).isFalse();
    }

    @Test
    void adminCannotDisableOwnAccount() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", Role.ADMIN)));

        assertOwnAccountProtected(() -> userService.update(1L, new UserUpdateRequest("Admin", Role.ADMIN, false), ADMIN));
    }

    @Test
    void adminCannotDemoteOwnAccount() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", Role.ADMIN)));

        assertOwnAccountProtected(() -> userService.update(1L, new UserUpdateRequest("Admin", Role.OWNER, true), ADMIN));
    }

    @Test
    void adminCanRenameOwnAccount() {
        User admin = user(1L, "admin", Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        userService.update(1L, new UserUpdateRequest("Ana Maria Admin", Role.ADMIN, true), ADMIN);

        assertThat(admin.getFullName()).isEqualTo("Ana Maria Admin");
    }

    @Test
    void updateThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(99L, new UserUpdateRequest("X", Role.OWNER, true), ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void changePasswordStoresNewHash() {
        User user = user(2L, "mechanic", Role.EMPLOYEE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewSecret123!")).thenReturn("$2a$10$newhash");

        userService.changePassword(2L, new PasswordChangeRequest("NewSecret123!"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$newhash");
    }

    // ------------------------------------------------------------------ delete

    @Test
    void deleteRemovesUserWithoutInterventions() {
        User user = user(2L, "mechanic", Role.EMPLOYEE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.hasAssignedInterventions(2L)).thenReturn(false);

        userService.delete(2L, ADMIN);

        verify(userRepository).delete(user);
    }

    @Test
    void adminCannotDeleteOwnAccount() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", Role.ADMIN)));

        assertOwnAccountProtected(() -> userService.delete(1L, ADMIN));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void userWithInterventionsCannotBeDeleted() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "mechanic", Role.EMPLOYEE)));
        when(userRepository.hasAssignedInterventions(2L)).thenReturn(true);

        assertThatThrownBy(() -> userService.delete(2L, ADMIN))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.USER_HAS_INTERVENTIONS));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(99L, ADMIN)).isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).hasAssignedInterventions(anyLong());
    }

    // ------------------------------------------------------------------ helpers

    private static void assertOwnAccountProtected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.OWN_ACCOUNT_PROTECTED));
    }

    static User user(Long id, String username, Role role) {
        User user = new User(username, "$2a$10$hash", "Test User", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
