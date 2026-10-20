package com.example.jobscheduler.job.service;

import java.util.List;

import com.example.jobscheduler.job.domain.Job;

public record JobPage(List<Job> jobs, int page, int size, long totalElements) {

    public JobPage {
        jobs = List.copyOf(jobs);
    }
}