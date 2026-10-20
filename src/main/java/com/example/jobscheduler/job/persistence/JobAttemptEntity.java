package com.example.jobscheduler.job.persistence;

import java.time.Instant;
import java.util.UUID;

import com.example.jobscheduler.job.domain.JobAttempt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_attempts")
public class JobAttemptEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private JobEntity job;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobAttempt.Status status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    protected JobAttemptEntity() {
    }

    JobAttemptEntity(JobAttempt attempt, JobEntity job) {
        id = attempt.id();
        this.job = job;
        attemptNumber = attempt.attemptNumber();
        workerId = attempt.workerId();
        status = attempt.status();
        startedAt = attempt.startedAt();
        finishedAt = attempt.finishedAt().orElse(null);
        failureReason = attempt.failureReason().orElse(null);
    }

    JobAttempt toDomain() {
        return JobAttempt.reconstitute(
                id,
                job.getId(),
                attemptNumber,
                workerId,
                startedAt,
                status,
                finishedAt,
                failureReason);
    }
}