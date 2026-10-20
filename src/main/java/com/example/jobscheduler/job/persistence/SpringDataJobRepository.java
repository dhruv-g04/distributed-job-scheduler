package com.example.jobscheduler.job.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJobRepository extends JpaRepository<JobEntity, UUID> {

    Page<JobEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}