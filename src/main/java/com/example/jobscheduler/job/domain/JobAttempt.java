package com.example.jobscheduler.job.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class JobAttempt {

    public enum Status {
        RUNNING,
        SUCCESS,
        FAILED
    }

    private final UUID id;
    private final UUID jobId;
    private final int attemptNumber;
    private final UUID workerId;
    private final Instant startedAt;
    private Status status;
    private Instant finishedAt;
    private String failureReason;

    public JobAttempt(UUID id, UUID jobId, int attemptNumber, UUID workerId, Instant startedAt) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (jobId == null) {
            throw new IllegalArgumentException("jobId must not be null");
        }
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        if (workerId == null) {
            throw new IllegalArgumentException("workerId must not be null");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }

        this.id = id;
        this.jobId = jobId;
        this.attemptNumber = attemptNumber;
        this.workerId = workerId;
        this.startedAt = startedAt;
        this.status = Status.RUNNING;
    }

    public static JobAttempt reconstitute(
            UUID id,
            UUID jobId,
            int attemptNumber,
            UUID workerId,
            Instant startedAt,
            Status status,
            Instant finishedAt,
            String failureReason) {
        JobAttempt attempt = new JobAttempt(id, jobId, attemptNumber, workerId, startedAt);
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == Status.RUNNING) {
            if (finishedAt != null || failureReason != null) {
                throw new IllegalArgumentException("running attempt must not have completion details");
            }
        } else {
            if (finishedAt == null || finishedAt.isBefore(startedAt)) {
                throw new IllegalArgumentException("completed attempt must have a valid finishedAt");
            }
            if (status == Status.SUCCESS && failureReason != null) {
                throw new IllegalArgumentException("successful attempt must not have a failure reason");
            }
            if (status == Status.FAILED && (failureReason == null || failureReason.isBlank())) {
                throw new IllegalArgumentException("failed attempt must have a failure reason");
            }
        }

        attempt.status = status;
        attempt.finishedAt = finishedAt;
        attempt.failureReason = failureReason;
        return attempt;
    }

    public UUID id() {
        return id;
    }

    public UUID jobId() {
        return jobId;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public UUID workerId() {
        return workerId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Status status() {
        return status;
    }

    public Optional<Instant> finishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    void completeSuccessfully(Instant completionTime) {
        validateCompletionTime(completionTime);
        status = Status.SUCCESS;
        finishedAt = completionTime;
    }

    void completeWithFailure(Instant completionTime, String reason) {
        validateCompletionTime(completionTime);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("failure reason must not be blank");
        }
        status = Status.FAILED;
        finishedAt = completionTime;
        failureReason = reason;
    }

    private void validateCompletionTime(Instant completionTime) {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Only a running attempt can be completed");
        }
        if (completionTime == null) {
            throw new IllegalArgumentException("completionTime must not be null");
        }
        if (completionTime.isBefore(startedAt)) {
            throw new IllegalArgumentException("completionTime must not be before startedAt");
        }
    }
}