package com.quephoto.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PortfolioWriteRequest(
        @JsonProperty(required = true) @NotBlank @Size(max = 100) String title,
        @JsonProperty(required = true) @Size(max = 10000) String description,
        @JsonProperty(required = true) @Size(max = 200) String location,
        @JsonProperty(required = true) LocalDateTime shotAt,
        @JsonProperty(required = true) String shotTimePrecision,
        @JsonProperty(required = true) @NotBlank String status
) {}