package com.quephoto.portfolio.dto;

import java.time.Instant;
import java.time.LocalDateTime;

public final class AdminPortfolioDtos {
    private AdminPortfolioDtos() {}

    public record ListItem(
            Long id,
            String title,
            String status,
            String location,
            LocalDateTime shotAt,
            String shotTimePrecision,
            String coverImageUrl,
            Instant createdAt,
            Instant updatedAt
    ) {}

}
