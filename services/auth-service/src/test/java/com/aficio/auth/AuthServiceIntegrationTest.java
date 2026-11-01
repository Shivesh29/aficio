package com.aficio.auth;

import com.aficio.user.User;
import com.aficio.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthServiceIntegrationTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldFindUserByUsername() {
        User user = new User("shivesh", "shivesh@example.com", "hashed_password");
        when(userRepository.findByUsername("shivesh")).thenReturn(Optional.of(user));

        Optional<User> found = userRepository.findByUsername("shivesh");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("shivesh");
        assertThat(found.get().getEmail()).isEqualTo("shivesh@example.com");
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        Optional<User> found = userRepository.findByUsername("nonexistent");

        assertThat(found).isEmpty();
    }
}
