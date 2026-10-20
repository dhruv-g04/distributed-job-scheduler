CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    payload TEXT NOT NULL,
    priority INTEGER NOT NULL CHECK (priority >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED', 'DLQ')),
    scheduled_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    retry_count INTEGER NOT NULL CHECK (retry_count >= 0),
    max_retries INTEGER NOT NULL CHECK (max_retries >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    CHECK (retry_count <= max_retries),
    CHECK (updated_at >= created_at),
    CHECK (next_attempt_at >= scheduled_at)
);

CREATE INDEX jobs_created_at_id_idx ON jobs (created_at DESC, id DESC);

CREATE TABLE job_attempts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    worker_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    failure_reason TEXT,
    UNIQUE (job_id, attempt_number),
    CHECK (finished_at IS NULL OR finished_at >= started_at),
    CHECK ((status = 'RUNNING' AND finished_at IS NULL AND failure_reason IS NULL)
        OR (status = 'SUCCESS' AND finished_at IS NOT NULL AND failure_reason IS NULL)
        OR (status = 'FAILED' AND finished_at IS NOT NULL AND failure_reason IS NOT NULL))
);