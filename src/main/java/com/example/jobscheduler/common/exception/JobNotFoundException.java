package com.example.jobscheduler.common.exception;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID id) {
        super("Job not found: " + id);
    }
}