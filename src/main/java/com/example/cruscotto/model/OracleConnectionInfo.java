package com.example.cruscotto.model;

import java.time.Instant;

public record OracleConnectionInfo(
        String id,
        String label,
        String connectionTarget,
        String jdbcUrl,
        String username,
        String schema,
        Instant createdAt
) {
}
