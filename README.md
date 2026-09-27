# Distributed Job Scheduler

A small, interview-focused foundation for a distributed job scheduler. The current application provides only a health endpoint; scheduling and job behavior have not been implemented.

## Technology stack

- Java 21
- Spring Boot 3.5.5
- Maven 3.9+
- Spring MVC and JUnit 5 via Spring Boot starters

## Current phase

Phase 1: Foundation.

## Current architecture status

The application consists of a Spring Boot entry point and a single REST controller. No job-domain, persistence, scheduling, or worker packages exist yet.

## Run locally

```bash
mvn spring-boot:run
```

The server listens on `http://localhost:8080`. Check the application endpoint with:

```bash
curl http://localhost:8080/health
```

It returns:

```json
{ "status": "UP" }
```

## Run tests

```bash
mvn test
```

## Current limitations

There is no job API or job execution, PostgreSQL persistence, scheduler, worker pool, retry handling, dead-letter queue, lease recovery, or Redis integration. The health endpoint confirms only that the web application is responding; it does not perform dependency checks.
