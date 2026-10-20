package com.example.jobscheduler.job.api;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(
        @NotBlank @Size(max = 1_000_000) String payload,
        @Min(0) @Max(1_000) Integer priority,
        Instant scheduledAt,
        @Min(0) @Max(100) Integer maxRetries) {
}