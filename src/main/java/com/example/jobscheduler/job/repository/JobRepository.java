package com.example.jobscheduler.job.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.jobscheduler.job.domain.Job;

public interface JobRepository {

    Job save(Job job);

    Optional<Job> findById(UUID id);

    Page findAll(int page, int size);

    record Page(List<Job> jobs, long totalElements, int page, int size) {
        public Page {
            jobs = List.copyOf(jobs);
        }
    }
}