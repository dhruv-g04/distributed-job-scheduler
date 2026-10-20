package com.example.jobscheduler.job.api;

import java.time.Instant;
import java.util.UUID;

import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.domain.JobStatus;

public record JobResponse(
        UUID id,
        String payload,
        int priority,
        JobStatus status,
        Instant scheduledAt,
        Instant createdAt,
        Instant updatedAt,
        int retryCount,
        int maxRetries,
        Instant nextAttemptAt) {

    static JobResponse from(Job job) {
        return new JobResponse(
                job.id(),
                job.payload(),
                job.priority(),
                job.status(),
                job.scheduledAt(),
                job.createdAt(),
                job.updatedAt(),
                job.retryCount(),
                job.maxRetries(),
                job.nextAttemptAt());
    }
}