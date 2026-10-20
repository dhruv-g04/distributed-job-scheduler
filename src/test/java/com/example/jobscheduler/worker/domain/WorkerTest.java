package com.example.jobscheduler.worker.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkerTest {

    @Test
    void workerContainsOnlyItsIdentity() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals(id, new Worker(id).id());
    }

    @Test
    void nullWorkerIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Worker(null));
    }
}