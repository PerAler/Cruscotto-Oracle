package com.example.cruscotto.model;

import java.time.Instant;

public record OracleConnectionProfile(
        String label,
        String connectionTarget,
        String username,
        String schema,
        Instant lastSuccessfulAt
) {
}
