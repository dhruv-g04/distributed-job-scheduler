package com.example.jobscheduler.worker.domain;

import java.util.UUID;

public record Worker(UUID id) {

    public Worker {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
    }
}