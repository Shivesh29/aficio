package com.aficio.kafka;

import java.util.UUID;

public record EmailVerificationEvent(
    Long userId,
    String email,
    String token
) {}
