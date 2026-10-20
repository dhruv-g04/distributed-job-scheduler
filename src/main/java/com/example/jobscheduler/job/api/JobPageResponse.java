package com.example.jobscheduler.job.api;

import java.util.List;

import com.example.jobscheduler.job.service.JobPage;

public record JobPageResponse(
        List<JobResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public JobPageResponse {
        content = List.copyOf(content);
    }

    static JobPageResponse from(JobPage jobPage) {
        List<JobResponse> content = jobPage.jobs().stream().map(JobResponse::from).toList();
        int totalPages = (int) ((jobPage.totalElements() + jobPage.size() - 1) / jobPage.size());
        return new JobPageResponse(content, jobPage.page(), jobPage.size(), jobPage.totalElements(), totalPages);
    }
}