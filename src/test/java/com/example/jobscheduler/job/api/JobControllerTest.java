package com.example.jobscheduler.job.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.jobscheduler.common.exception.JobNotFoundException;
import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.domain.JobStatus;
import com.example.jobscheduler.job.service.JobPage;
import com.example.jobscheduler.job.service.JobService;

@WebMvcTest(JobController.class)
class JobControllerTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @Test
    void createsJobAndReturnsLocation() throws Exception {
        Job createdJob = job(JobStatus.QUEUED);
        when(jobService.createJob(any(CreateJobRequest.class))).thenReturn(createdJob);

        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                                {"payload":"{\\"task\\":\\"email\\"}","priority":4,"scheduledAt":"2026-01-01T00:00:00Z","maxRetries":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/jobs/" + JOB_ID))
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.payload").value("{\"task\":\"email\"}"));
    }

    @Test
    void rejectsInvalidCreateRequestWithConsistentError() throws Exception {
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"payload":" ","priority":-1,"maxRetries":101}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/jobs"))
                .andExpect(jsonPath("$.details.payload").exists())
                .andExpect(jsonPath("$.details.priority").exists())
                .andExpect(jsonPath("$.details.maxRetries").exists());
        verify(jobService, never()).createJob(any());
    }

    @Test
    void malformedJsonUsesSameErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/jobs"));
    }

    @Test
    void getsExistingJob() throws Exception {
        when(jobService.getJob(JOB_ID)).thenReturn(job(JobStatus.QUEUED));

        mockMvc.perform(get("/api/jobs/{id}", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void missingJobReturnsNotFoundError() throws Exception {
        when(jobService.getJob(JOB_ID)).thenThrow(new JobNotFoundException(JOB_ID));

        mockMvc.perform(get("/api/jobs/{id}", JOB_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/jobs/" + JOB_ID));
    }

    @Test
    void listsJobsWithPaginationMetadata() throws Exception {
        when(jobService.listJobs(1, 2)).thenReturn(new JobPage(List.of(job(JobStatus.QUEUED)), 1, 2, 5));

        mockMvc.perform(get("/api/jobs").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/jobs").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void deleteCancelsQueuedJobWithoutPhysicallyDeletingIt() throws Exception {
        mockMvc.perform(delete("/api/jobs/{id}", JOB_ID))
                .andExpect(status().isNoContent());

        verify(jobService).cancelJob(JOB_ID);
    }

    @Test
    void invalidCancellationTransitionReturnsConflict() throws Exception {
        doThrow(new IllegalStateException("Cannot cancel while job status is RUNNING"))
                .when(jobService).cancelJob(JOB_ID);

        mockMvc.perform(delete("/api/jobs/{id}", JOB_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value("Cannot cancel while job status is RUNNING"));
    }

    private static Job job(JobStatus status) {
        if (status != JobStatus.QUEUED) {
            throw new IllegalArgumentException("Test helper only constructs queued jobs");
        }
        return new Job(JOB_ID, "{\"task\":\"email\"}", 4, CREATED_AT, CREATED_AT, 2);
    }
}