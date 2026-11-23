package io.github.cerneabogdanandrei.autoservice.auth;

import io.github.cerneabogdanandrei.autoservice.user.Role;
import io.github.cerneabogdanandrei.autoservice.user.User;
import io.github.cerneabogdanandrei.autoservice.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private DatabaseUserDetailsService service;

    @Test
    void loadsUserWithRoleAuthorityAndStatus() {
        User employee = AuthServiceTest.user(3L, "employee", Role.EMPLOYEE);
        employee.setEnabled(false);
        when(userRepository.findByUsername("employee")).thenReturn(Optional.of(employee));

        UserDetails details = service.loadUserByUsername("employee");

        assertThat(details.getUsername()).isEqualTo("employee");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(details.isEnabled()).isFalse();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_EMPLOYEE");
        assertThat(((AuthUserDetails) details).getId()).isEqualTo(3L);
    }

    @Test
    void unknownUsernameThrows() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost")).isInstanceOf(UsernameNotFoundException.class);
    }
}
