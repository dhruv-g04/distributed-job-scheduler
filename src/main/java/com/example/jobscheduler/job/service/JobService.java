package com.example.jobscheduler.job.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.jobscheduler.common.exception.JobNotFoundException;
import com.example.jobscheduler.job.api.CreateJobRequest;
import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.repository.JobRepository;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final Clock clock;

    public JobService(JobRepository jobRepository, Clock clock) {
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    @Transactional
    public Job createJob(CreateJobRequest request) {
        Instant now = clock.instant();
        Instant scheduledAt = request.scheduledAt() == null ? now : request.scheduledAt();
        int priority = request.priority() == null ? 0 : request.priority();
        int maxRetries = request.maxRetries() == null ? 0 : request.maxRetries();
        Job job = new Job(UUID.randomUUID(), request.payload(), priority, scheduledAt, now, maxRetries);
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public Job getJob(UUID id) {
        return jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public JobPage listJobs(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        JobRepository.Page result = jobRepository.findAll(page, size);
        return new JobPage(result.jobs(), result.page(), result.size(), result.totalElements());
    }

    @Transactional
    public void cancelJob(UUID id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        job.cancel(clock.instant());
        jobRepository.save(job);
    }
}