package com.example.jobscheduler.job.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.jobscheduler.worker.domain.Worker;

public final class Job {

    private final UUID id;
    private final String payload;
    private final int priority;
    private final Instant scheduledAt;
    private final Instant createdAt;
    private final int maxRetries;
    private final List<JobAttempt> attempts = new ArrayList<>();

    private JobStatus status = JobStatus.QUEUED;
    private int retryCount;
    private Instant nextAttemptAt;
    private Instant updatedAt;

    public Job(UUID id, String payload, int priority, Instant scheduledAt, Instant createdAt, int maxRetries) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload must not be blank");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be non-negative");
        }
        if (scheduledAt == null) {
            throw new IllegalArgumentException("scheduledAt must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }

        this.id = id;
        this.payload = payload;
        this.priority = priority;
        this.scheduledAt = scheduledAt;
        this.createdAt = createdAt;
        this.maxRetries = maxRetries;
        this.nextAttemptAt = scheduledAt;
        this.updatedAt = createdAt;
    }

    public static Job reconstitute(
            UUID id,
            String payload,
            int priority,
            Instant scheduledAt,
            Instant createdAt,
            int maxRetries,
            JobStatus status,
            int retryCount,
            Instant nextAttemptAt,
            Instant updatedAt,
            List<JobAttempt> attempts) {
        Job job = new Job(id, payload, priority, scheduledAt, createdAt, maxRetries);
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (retryCount < 0 || retryCount > maxRetries) {
            throw new IllegalArgumentException("retryCount must be between zero and maxRetries");
        }
        requireTimestamp(nextAttemptAt, "nextAttemptAt");
        requireTimestamp(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (nextAttemptAt.isBefore(scheduledAt)) {
            throw new IllegalArgumentException("nextAttemptAt must not be before scheduledAt");
        }
        if (attempts == null) {
            throw new IllegalArgumentException("attempts must not be null");
        }

        List<JobAttempt> restoredAttempts = List.copyOf(attempts);
        for (int index = 0; index < restoredAttempts.size(); index++) {
            JobAttempt attempt = restoredAttempts.get(index);
            if (!attempt.jobId().equals(id) || attempt.attemptNumber() != index + 1) {
                throw new IllegalArgumentException("attempt history must belong to this job and be sequential");
            }
            if (attempt.startedAt().isBefore(createdAt) || attempt.startedAt().isAfter(updatedAt)) {
                throw new IllegalArgumentException("attempt timestamps must be within the job lifetime");
            }
        }

        JobAttempt latestAttempt = restoredAttempts.isEmpty() ? null
                : restoredAttempts.get(restoredAttempts.size() - 1);
        boolean hasRunningAttempt = restoredAttempts.stream()
                .anyMatch(attempt -> attempt.status() == JobAttempt.Status.RUNNING);
        if (hasRunningAttempt != (status == JobStatus.RUNNING)
                || (hasRunningAttempt && latestAttempt.status() != JobAttempt.Status.RUNNING)) {
            throw new IllegalArgumentException("running job state must match its active attempt");
        }
        if (status == JobStatus.SUCCESS && (latestAttempt == null
                || latestAttempt.status() != JobAttempt.Status.SUCCESS)) {
            throw new IllegalArgumentException("successful job must have a successful latest attempt");
        }
        if ((status == JobStatus.FAILED || status == JobStatus.DLQ)
                && (latestAttempt == null || latestAttempt.status() != JobAttempt.Status.FAILED)) {
            throw new IllegalArgumentException("failed or DLQ job must have a failed latest attempt");
        }

        job.status = status;
        job.retryCount = retryCount;
        job.nextAttemptAt = nextAttemptAt;
        job.updatedAt = updatedAt;
        job.attempts.addAll(restoredAttempts);
        return job;
    }

    public UUID id() {
        return id;
    }

    public String payload() {
        return payload;
    }

    public int priority() {
        return priority;
    }

    public JobStatus status() {
        return status;
    }

    public Instant scheduledAt() {
        return scheduledAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public int retryCount() {
        return retryCount;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public List<JobAttempt> attempts() {
        return List.copyOf(attempts);
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public JobAttempt startAttempt(Worker worker, UUID attemptId, Instant startedAt) {
        requireStatus(JobStatus.QUEUED, "start an attempt");
        if (worker == null) {
            throw new IllegalArgumentException("worker must not be null");
        }
        if (attemptId == null) {
            throw new IllegalArgumentException("attemptId must not be null");
        }
        requireTimestamp(startedAt, "startedAt");
        requireNotBeforeUpdatedAt(startedAt, "startedAt");
        if (startedAt.isBefore(scheduledAt) || startedAt.isBefore(nextAttemptAt)) {
            throw new IllegalStateException("Job is not eligible to start at " + startedAt);
        }
        if (attempts.stream().anyMatch(attempt -> attempt.id().equals(attemptId))) {
            throw new IllegalArgumentException("attemptId has already been used for this job");
        }

        JobStatus nextStatus = status.transitionTo(JobStatus.RUNNING);
        JobAttempt attempt = new JobAttempt(attemptId, id, attempts.size() + 1, worker.id(), startedAt);
        status = nextStatus;
        updatedAt = startedAt;
        attempts.add(attempt);
        return attempt;
    }

    public void completeSuccessfully(UUID attemptId, Instant completedAt) {
        JobAttempt attempt = requireActiveAttempt(attemptId);
        requireNotBeforeUpdatedAt(completedAt, "completedAt");
        JobStatus nextStatus = status.transitionTo(JobStatus.SUCCESS);
        attempt.completeSuccessfully(completedAt);
        status = nextStatus;
        updatedAt = completedAt;
    }

    public void failPermanently(UUID attemptId, Instant failedAt, String failureReason) {
        JobAttempt attempt = requireActiveAttempt(attemptId);
        requireNotBeforeUpdatedAt(failedAt, "failedAt");
        JobStatus nextStatus = status.transitionTo(JobStatus.FAILED);
        attempt.completeWithFailure(failedAt, failureReason);
        status = nextStatus;
        updatedAt = failedAt;
    }

    public void retryAfterFailure(UUID attemptId, Instant failedAt, String failureReason, Instant retryAt) {
        JobAttempt attempt = requireActiveAttempt(attemptId);
        requireNotBeforeUpdatedAt(failedAt, "failedAt");
        requireTimestamp(retryAt, "retryAt");
        if (retryAt.isBefore(failedAt)) {
            throw new IllegalArgumentException("retryAt must not be before failedAt");
        }
        if (retryCount >= maxRetries) {
            throw new IllegalStateException("Job has exhausted its retry limit");
        }

        JobStatus nextStatus = status.transitionTo(JobStatus.QUEUED);
        attempt.completeWithFailure(failedAt, failureReason);
        status = nextStatus;
        retryCount++;
        nextAttemptAt = retryAt;
        updatedAt = failedAt;
    }

    public void deadLetterAfterFailure(UUID attemptId, Instant failedAt, String failureReason) {
        JobAttempt attempt = requireActiveAttempt(attemptId);
        requireNotBeforeUpdatedAt(failedAt, "failedAt");
        if (retryCount < maxRetries) {
            throw new IllegalStateException("Job cannot enter the DLQ before exhausting retries");
        }

        JobStatus nextStatus = status.transitionTo(JobStatus.DLQ);
        attempt.completeWithFailure(failedAt, failureReason);
        status = nextStatus;
        updatedAt = failedAt;
    }

    public void cancel(Instant cancelledAt) {
        requireStatus(JobStatus.QUEUED, "cancel");
        requireNotBeforeUpdatedAt(cancelledAt, "cancelledAt");
        status = status.transitionTo(JobStatus.CANCELLED);
        updatedAt = cancelledAt;
    }

    public void replay(Instant replayedAt) {
        if (status != JobStatus.FAILED && status != JobStatus.DLQ) {
            throw new IllegalStateException("Only failed or DLQ jobs can be replayed");
        }
        requireNotBeforeUpdatedAt(replayedAt, "replayedAt");
        JobStatus nextStatus = status.transitionTo(JobStatus.QUEUED);
        status = nextStatus;
        retryCount = 0;
        nextAttemptAt = replayedAt;
        updatedAt = replayedAt;
    }

    private JobAttempt requireActiveAttempt(UUID attemptId) {
        requireStatus(JobStatus.RUNNING, "complete an attempt");
        if (attemptId == null) {
            throw new IllegalArgumentException("attemptId must not be null");
        }
        if (attempts.isEmpty()) {
            throw new IllegalStateException("Running job has no recorded attempt");
        }
        JobAttempt activeAttempt = attempts.get(attempts.size() - 1);
        if (!activeAttempt.id().equals(attemptId) || activeAttempt.status() != JobAttempt.Status.RUNNING) {
            throw new IllegalArgumentException("attemptId does not identify the active attempt");
        }
        return activeAttempt;
    }

    private void requireStatus(JobStatus expectedStatus, String operation) {
        if (status != expectedStatus) {
            throw new IllegalStateException("Cannot " + operation + " while job status is " + status);
        }
    }

    private void requireNotBeforeUpdatedAt(Instant timestamp, String name) {
        requireTimestamp(timestamp, name);
        if (timestamp.isBefore(updatedAt)) {
            throw new IllegalArgumentException(name + " must not be before updatedAt");
        }
    }

    private static void requireTimestamp(Instant timestamp, String name) {
        if (timestamp == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}