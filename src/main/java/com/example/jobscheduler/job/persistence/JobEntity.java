package com.example.jobscheduler.job.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.domain.JobAttempt;
import com.example.jobscheduler.job.domain.JobStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "jobs")
public class JobEntity {

    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL)
    @OrderBy("attemptNumber ASC")
    private List<JobAttemptEntity> attempts = new ArrayList<>();

    protected JobEntity() {
    }

    JobEntity(Job job) {
        id = job.id();
        payload = job.payload();
        priority = job.priority();
        status = job.status();
        scheduledAt = job.scheduledAt();
        createdAt = job.createdAt();
        updatedAt = job.updatedAt();
        retryCount = job.retryCount();
        maxRetries = job.maxRetries();
        nextAttemptAt = job.nextAttemptAt();
        attempts = job.attempts().stream().map(attempt -> new JobAttemptEntity(attempt, this)).toList();
    }

    Job toDomain() {
        List<JobAttempt> domainAttempts = attempts.stream().map(JobAttemptEntity::toDomain).toList();
        return Job.reconstitute(
                id,
                payload,
                priority,
                scheduledAt,
                createdAt,
                maxRetries,
                status,
                retryCount,
                nextAttemptAt,
                updatedAt,
                domainAttempts);
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}