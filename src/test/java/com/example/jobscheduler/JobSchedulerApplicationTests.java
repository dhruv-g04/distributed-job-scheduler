package com.example.jobscheduler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.domain.JobAttempt;
import com.example.jobscheduler.job.domain.JobStatus;
import com.example.jobscheduler.job.repository.JobRepository;
import com.example.jobscheduler.worker.domain.Worker;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class JobSchedulerApplicationTests {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @Test
    void flywaySchemaPersistsJobAndAttemptHistory() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Job job = new Job(UUID.randomUUID(), "{}", 3, createdAt, createdAt, 0);
        UUID attemptId = UUID.randomUUID();
        job.startAttempt(new Worker(UUID.randomUUID()), attemptId, createdAt.plusSeconds(1));
        job.failPermanently(attemptId, createdAt.plusSeconds(2), "expected test failure");

        jobRepository.save(job);
        Job loaded = jobRepository.findById(job.id()).orElseThrow();

        assertEquals(JobStatus.FAILED, loaded.status());
        assertEquals(1, loaded.attempts().size());
        JobAttempt attempt = loaded.attempts().get(0);
        assertEquals(JobAttempt.Status.FAILED, attempt.status());
        assertEquals("expected test failure", attempt.failureReason().orElseThrow());
    }
}
