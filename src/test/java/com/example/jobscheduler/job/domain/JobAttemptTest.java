package com.example.jobscheduler.job.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JobAttemptTest {

    private static final Instant STARTED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID ATTEMPT_ID = uuid(1);
    private static final UUID JOB_ID = uuid(2);
    private static final UUID WORKER_ID = uuid(3);

    @Test
    void newAttemptIsRunningAndAssociatedWithJobAndWorker() {
        JobAttempt attempt = newAttempt();

        assertEquals(ATTEMPT_ID, attempt.id());
        assertEquals(JOB_ID, attempt.jobId());
        assertEquals(1, attempt.attemptNumber());
        assertEquals(WORKER_ID, attempt.workerId());
        assertEquals(STARTED_AT, attempt.startedAt());
        assertEquals(JobAttempt.Status.RUNNING, attempt.status());
        assertTrue(attempt.finishedAt().isEmpty());
        assertTrue(attempt.failureReason().isEmpty());
    }

    @Test
    void constructorRejectsInvalidAttemptData() {
        assertThrows(IllegalArgumentException.class,
                () -> new JobAttempt(null, JOB_ID, 1, WORKER_ID, STARTED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new JobAttempt(ATTEMPT_ID, null, 1, WORKER_ID, STARTED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new JobAttempt(ATTEMPT_ID, JOB_ID, 0, WORKER_ID, STARTED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new JobAttempt(ATTEMPT_ID, JOB_ID, 1, null, STARTED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new JobAttempt(ATTEMPT_ID, JOB_ID, 1, WORKER_ID, null));
    }

    @Test
    void successfulAttemptRecordsCompletionTime() {
        JobAttempt attempt = newAttempt();
        Instant finishedAt = STARTED_AT.plusSeconds(2);

        attempt.completeSuccessfully(finishedAt);

        assertEquals(JobAttempt.Status.SUCCESS, attempt.status());
        assertEquals(finishedAt, attempt.finishedAt().orElseThrow());
        assertTrue(attempt.failureReason().isEmpty());
    }

    @Test
    void failedAttemptRequiresReasonAndRecordsFailure() {
        JobAttempt attempt = newAttempt();
        Instant finishedAt = STARTED_AT.plusSeconds(2);

        attempt.completeWithFailure(finishedAt, "execution failed");

        assertEquals(JobAttempt.Status.FAILED, attempt.status());
        assertEquals(finishedAt, attempt.finishedAt().orElseThrow());
        assertEquals("execution failed", attempt.failureReason().orElseThrow());
    }

    @Test
    void completionRejectsInvalidTimeOrReasonWithoutChangingAttempt() {
        JobAttempt attempt = newAttempt();

        assertThrows(IllegalArgumentException.class, () -> attempt.completeSuccessfully(null));
        assertThrows(IllegalArgumentException.class,
                () -> attempt.completeSuccessfully(STARTED_AT.minusNanos(1)));
        assertThrows(IllegalArgumentException.class,
                () -> attempt.completeWithFailure(STARTED_AT.plusSeconds(1), " "));
        assertEquals(JobAttempt.Status.RUNNING, attempt.status());
        assertTrue(attempt.finishedAt().isEmpty());
    }

    @Test
    void completedAttemptCannotBeChangedAgain() {
        JobAttempt attempt = newAttempt();
        Instant finishedAt = STARTED_AT.plusSeconds(1);
        attempt.completeSuccessfully(finishedAt);

        assertThrows(IllegalStateException.class,
                () -> attempt.completeWithFailure(finishedAt, "late failure"));
        assertEquals(JobAttempt.Status.SUCCESS, attempt.status());
        assertEquals(finishedAt, attempt.finishedAt().orElseThrow());
    }

    private static JobAttempt newAttempt() {
        return new JobAttempt(ATTEMPT_ID, JOB_ID, 1, WORKER_ID, STARTED_AT);
    }

    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0, leastSignificantBits);
    }
}