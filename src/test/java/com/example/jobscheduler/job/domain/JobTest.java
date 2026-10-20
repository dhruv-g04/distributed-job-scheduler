package com.example.jobscheduler.job.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.jobscheduler.worker.domain.Worker;

class JobTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void newJobStartsQueuedWithNoAttempts() {
        Job job = newJob(3, CREATED_AT);

        assertEquals(JOB_ID, job.id());
        assertEquals("{}", job.payload());
        assertEquals(5, job.priority());
        assertEquals(JobStatus.QUEUED, job.status());
        assertEquals(0, job.retryCount());
        assertEquals(3, job.maxRetries());
        assertEquals(CREATED_AT, job.nextAttemptAt());
        assertEquals(CREATED_AT, job.updatedAt());
        assertTrue(job.attempts().isEmpty());
        assertFalse(job.isTerminal());
    }

    @Test
    void constructorRejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new Job(null, "{}", 1, CREATED_AT, CREATED_AT, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Job(JOB_ID, null, 1, CREATED_AT, CREATED_AT, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Job(JOB_ID, " ", 1, CREATED_AT, CREATED_AT, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Job(JOB_ID, "{}", -1, CREATED_AT, CREATED_AT, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Job(JOB_ID, "{}", 1, null, CREATED_AT, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Job(JOB_ID, "{}", 1, CREATED_AT, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Job(JOB_ID, "{}", 1, CREATED_AT, CREATED_AT, -1));
    }

    @Test
    void scheduledTimeMayBeInThePastButFutureJobCannotStartEarly() {
        Instant pastSchedule = CREATED_AT.minusSeconds(60);
        Job pastJob = newJob(0, pastSchedule);
        Job futureJob = newJob(0, CREATED_AT.plusSeconds(60));

        assertEquals(pastSchedule, pastJob.scheduledAt());
        assertThrows(IllegalStateException.class,
                () -> futureJob.startAttempt(worker(), uuid(3), CREATED_AT));
        assertEquals(JobStatus.QUEUED, futureJob.status());
        assertTrue(futureJob.attempts().isEmpty());
    }

    @Test
    void queuedJobCanBeCancelledOnlyOnce() {
        Job job = newJob(0, CREATED_AT);
        Instant cancelledAt = CREATED_AT.plusSeconds(5);

        job.cancel(cancelledAt);

        assertEquals(JobStatus.CANCELLED, job.status());
        assertEquals(cancelledAt, job.updatedAt());
        assertTrue(job.isTerminal());
        assertThrows(IllegalStateException.class, () -> job.cancel(cancelledAt));
        assertThrows(IllegalStateException.class,
                () -> job.startAttempt(worker(), uuid(3), cancelledAt));
    }

    @Test
    void successfulAttemptCompletesJobAndHistoryIsReadOnly() {
        Job job = newJob(0, CREATED_AT);
        UUID attemptId = uuid(3);
        Instant startedAt = CREATED_AT.plusSeconds(1);
        Instant completedAt = startedAt.plusSeconds(10);
        JobAttempt attempt = job.startAttempt(worker(), attemptId, startedAt);

        job.completeSuccessfully(attemptId, completedAt);

        assertEquals(JobStatus.SUCCESS, job.status());
        assertTrue(job.isTerminal());
        assertEquals(JobAttempt.Status.SUCCESS, attempt.status());
        assertEquals(completedAt, attempt.finishedAt().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> job.attempts().clear());
        assertThrows(IllegalStateException.class, () -> job.completeSuccessfully(attemptId, completedAt));
    }

    @Test
    void retryReturnsJobToQueueAndPreservesEveryAttempt() {
        Job job = newJob(1, CREATED_AT);
        UUID firstAttemptId = uuid(3);
        Instant firstStart = CREATED_AT.plusSeconds(1);
        Instant firstFailure = firstStart.plusSeconds(2);
        Instant retryAt = firstFailure.plusSeconds(30);
        JobAttempt firstAttempt = job.startAttempt(worker(), firstAttemptId, firstStart);

        job.retryAfterFailure(firstAttemptId, firstFailure, "temporary error", retryAt);

        assertEquals(JobStatus.QUEUED, job.status());
        assertEquals(1, job.retryCount());
        assertEquals(retryAt, job.nextAttemptAt());
        assertEquals(JobAttempt.Status.FAILED, firstAttempt.status());
        assertEquals(List.of(firstAttempt), job.attempts());
        assertThrows(IllegalStateException.class,
                () -> job.startAttempt(worker(), uuid(4), retryAt.minusNanos(1)));

        UUID secondAttemptId = uuid(4);
        Instant secondStart = retryAt;
        Instant secondFailure = secondStart.plusSeconds(1);
        job.startAttempt(worker(), secondAttemptId, secondStart);
        job.deadLetterAfterFailure(secondAttemptId, secondFailure, "retry limit reached");

        assertEquals(JobStatus.DLQ, job.status());
        assertEquals(1, job.retryCount());
        assertEquals(2, job.attempts().size());
        assertEquals(1, job.attempts().get(0).attemptNumber());
        assertEquals(2, job.attempts().get(1).attemptNumber());
        assertTrue(job.isTerminal());
    }

    @Test
    void exhaustedRetriesCannotBeScheduledAgain() {
        Job job = newJob(0, CREATED_AT);
        UUID attemptId = uuid(3);
        Instant startedAt = CREATED_AT.plusSeconds(1);
        Instant failedAt = startedAt.plusSeconds(1);
        job.startAttempt(worker(), attemptId, startedAt);

        assertThrows(IllegalStateException.class,
                () -> job.retryAfterFailure(attemptId, failedAt, "failure", failedAt));
        assertEquals(JobStatus.RUNNING, job.status());
        assertEquals(JobAttempt.Status.RUNNING, job.attempts().get(0).status());
    }

    @Test
    void deadLetterRequiresRetryExhaustion() {
        Job job = newJob(1, CREATED_AT);
        UUID attemptId = uuid(3);
        Instant startedAt = CREATED_AT.plusSeconds(1);
        Instant failedAt = startedAt.plusSeconds(1);
        job.startAttempt(worker(), attemptId, startedAt);

        assertThrows(IllegalStateException.class,
                () -> job.deadLetterAfterFailure(attemptId, failedAt, "failure"));
        assertEquals(JobStatus.RUNNING, job.status());
    }

    @Test
    void permanentFailureCanBeManuallyReplayedAndResetsRetryBudget() {
        Job job = newJob(2, CREATED_AT);
        UUID attemptId = uuid(3);
        Instant startedAt = CREATED_AT.plusSeconds(1);
        Instant failedAt = startedAt.plusSeconds(1);
        job.startAttempt(worker(), attemptId, startedAt);
        job.failPermanently(attemptId, failedAt, "permanent error");

        Instant replayedAt = failedAt.plusSeconds(5);
        job.replay(replayedAt);

        assertEquals(JobStatus.QUEUED, job.status());
        assertEquals(0, job.retryCount());
        assertEquals(replayedAt, job.nextAttemptAt());
        assertEquals(1, job.attempts().size());
        assertFalse(job.isTerminal());
    }

    @Test
    void deadLetterJobCanBeManuallyReplayedWithoutLosingHistory() {
        Job job = newJob(0, CREATED_AT);
        UUID attemptId = uuid(3);
        Instant startedAt = CREATED_AT.plusSeconds(1);
        Instant failedAt = startedAt.plusSeconds(1);
        job.startAttempt(worker(), attemptId, startedAt);
        job.deadLetterAfterFailure(attemptId, failedAt, "retry limit reached");
        Instant replayedAt = failedAt.plusSeconds(5);

        job.replay(replayedAt);

        assertEquals(JobStatus.QUEUED, job.status());
        assertEquals(0, job.retryCount());
        assertEquals(1, job.attempts().size());
        assertEquals(JobAttempt.Status.FAILED, job.attempts().get(0).status());
    }

    @Test
    void invalidLifecycleCallsAndTimestampsAreRejectedWithoutMutation() {
        Job job = newJob(1, CREATED_AT);
        assertThrows(IllegalStateException.class, () -> job.completeSuccessfully(uuid(3), CREATED_AT));
        assertThrows(IllegalStateException.class, () -> job.replay(CREATED_AT));
        assertThrows(IllegalArgumentException.class, () -> job.cancel(CREATED_AT.minusNanos(1)));

        UUID attemptId = uuid(3);
        Instant startedAt = CREATED_AT.plusSeconds(1);
        job.startAttempt(worker(), attemptId, startedAt);
        assertThrows(IllegalStateException.class, () -> job.cancel(startedAt));
        assertThrows(IllegalArgumentException.class,
                () -> job.completeSuccessfully(attemptId, startedAt.minusNanos(1)));
        assertThrows(IllegalArgumentException.class,
                () -> job.retryAfterFailure(attemptId, startedAt.plusSeconds(1), "failure", startedAt));
        assertEquals(JobStatus.RUNNING, job.status());
        assertEquals(JobAttempt.Status.RUNNING, job.attempts().get(0).status());
    }

    @Test
    void onlyTheActiveAttemptCanCompleteTheRunningJob() {
        Job job = newJob(1, CREATED_AT);
        UUID attemptId = uuid(3);
        Instant startedAt = CREATED_AT.plusSeconds(1);
        job.startAttempt(worker(), attemptId, startedAt);

        assertThrows(IllegalArgumentException.class,
                () -> job.completeSuccessfully(uuid(4), startedAt.plusSeconds(1)));
        assertEquals(JobStatus.RUNNING, job.status());
    }

    private static Job newJob(int maxRetries, Instant scheduledAt) {
        return new Job(JOB_ID, "{}", 5, scheduledAt, CREATED_AT, maxRetries);
    }

    private static Worker worker() {
        return new Worker(WORKER_ID);
    }

    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0, leastSignificantBits);
    }
}