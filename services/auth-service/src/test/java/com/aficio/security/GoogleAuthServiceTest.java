package com.aficio.security;

import com.aficio.controller.UserController.LoginResponse;
import com.aficio.user.User;
import com.aficio.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GoogleAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private ObjectMapper objectMapper;
    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        googleAuthService = new GoogleAuthService(
                userRepository,
                jwtService,
                objectMapper,
                ""
        );
    }

    @Test
    void shouldThrowBadRequestWhenTokenIsBlank() {
        assertThatThrownBy(() -> googleAuthService.authenticate(""))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Google ID token is required");
    }

    @Test
    void shouldThrowUnauthorizedWhenTokenIsInvalid() {
        assertThatThrownBy(() -> googleAuthService.authenticate("fake-invalid-token"))
                .isInstanceOf(ResponseStatusException.class);
    }
}

