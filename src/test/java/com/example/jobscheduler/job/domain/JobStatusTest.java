package com.example.jobscheduler.job.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class JobStatusTest {

    private static final Map<JobStatus, Set<JobStatus>> VALID_TRANSITIONS = Map.of(
            JobStatus.QUEUED, Set.of(JobStatus.RUNNING, JobStatus.CANCELLED),
            JobStatus.RUNNING, Set.of(JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.QUEUED, JobStatus.DLQ),
            JobStatus.SUCCESS, Set.of(),
            JobStatus.FAILED, Set.of(JobStatus.QUEUED),
            JobStatus.CANCELLED, Set.of(),
            JobStatus.DLQ, Set.of(JobStatus.QUEUED));

    @ParameterizedTest
    @MethodSource("allTransitions")
    void transitionRulesAreExplicit(JobStatus currentStatus, JobStatus nextStatus, boolean valid) {
        if (valid) {
            assertEquals(nextStatus, currentStatus.transitionTo(nextStatus));
        } else {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> currentStatus.transitionTo(nextStatus));
            assertTrue(exception.getMessage().contains(currentStatus + " to " + nextStatus));
        }
    }

    @Test
    void nullTransitionTargetIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> JobStatus.QUEUED.transitionTo(null));
    }

    @Test
    void terminalStatusesAreExplicit() {
        assertTrue(JobStatus.SUCCESS.isTerminal());
        assertTrue(JobStatus.CANCELLED.isTerminal());
        assertTrue(JobStatus.DLQ.isTerminal());
        assertFalse(JobStatus.QUEUED.isTerminal());
        assertFalse(JobStatus.RUNNING.isTerminal());
        assertFalse(JobStatus.FAILED.isTerminal());
    }

    private static Stream<Arguments> allTransitions() {
        return Stream.of(JobStatus.values())
                .flatMap(current -> Stream.of(JobStatus.values())
                        .map(next -> Arguments.of(
                                current,
                                next,
                                VALID_TRANSITIONS.get(current).contains(next))));
    }
}