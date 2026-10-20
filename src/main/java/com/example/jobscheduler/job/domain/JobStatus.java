package com.example.jobscheduler.job.domain;

public enum JobStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    DLQ;

    public JobStatus transitionTo(JobStatus nextStatus) {
        if (nextStatus == null) {
            throw new IllegalArgumentException("nextStatus must not be null");
        }
        if (!allowsTransitionTo(nextStatus)) {
            throw new IllegalStateException("Cannot transition job from " + this + " to " + nextStatus);
        }
        return nextStatus;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == CANCELLED || this == DLQ;
    }

    private boolean allowsTransitionTo(JobStatus nextStatus) {
        return switch (this) {
            case QUEUED -> nextStatus == RUNNING || nextStatus == CANCELLED;
            case RUNNING -> nextStatus == SUCCESS || nextStatus == FAILED
                    || nextStatus == QUEUED || nextStatus == DLQ;
            case FAILED, DLQ -> nextStatus == QUEUED;
            case SUCCESS, CANCELLED -> false;
        };
    }
}