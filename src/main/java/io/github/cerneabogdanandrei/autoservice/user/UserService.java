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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * User management (ADMIN only). Usernames are unique case-insensitively and stored in lower
 * case; passwords are stored as BCrypt hashes only.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final String RESOURCE = "User";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    UserService(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public Page<UserResponse> findAll(Role role, Boolean enabled, Pageable pageable) {
        Specification<User> filter = Specification.allOf(
                UserSpecifications.hasRole(role), UserSpecifications.isEnabled(enabled));
        return userRepository.findAll(filter, pageable).map(userMapper::toResponse);
    }

    public UserResponse findById(Long id) {
        return userMapper.toResponse(getUser(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(username)) {
            throw new BusinessRuleException(ErrorCode.USERNAME_ALREADY_EXISTS,
                    "A user with username '%s' already exists.".formatted(username));
        }
        User user = new User(username, passwordEncoder.encode(request.password()),
                request.fullName().trim(), request.role());
        return userMapper.toResponse(userRepository.save(user));
    }

    /**
     * Updates name, role and status. Administrators cannot disable or demote their own account,
     * which would lock them out (and could leave the system without an administrator).
     */
    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request, AuthenticatedUser currentUser) {
        User user = getUser(id);
        boolean selfLockout = !request.enabled() || request.role() != Role.ADMIN;
        if (user.getId().equals(currentUser.id()) && selfLockout) {
            throw new BusinessRuleException(ErrorCode.OWN_ACCOUNT_PROTECTED,
                    "You cannot disable or change the role of your own account.");
        }
        userMapper.update(request, user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        getUser(id).setPasswordHash(passwordEncoder.encode(request.password()));
    }

    /**
     * Deletes a user that has never been assigned work. Users with interventions keep their
     * history and should be disabled instead.
     */
    @Transactional
    public void delete(Long id, AuthenticatedUser currentUser) {
        User user = getUser(id);
        if (user.getId().equals(currentUser.id())) {
            throw new BusinessRuleException(ErrorCode.OWN_ACCOUNT_PROTECTED, "You cannot delete your own account.");
        }
        if (userRepository.hasAssignedInterventions(id)) {
            throw new BusinessRuleException(ErrorCode.USER_HAS_INTERVENTIONS,
                    "User %d has assigned interventions and cannot be deleted; disable the account instead."
                            .formatted(id));
        }
        userRepository.delete(user);
    }

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }
}
