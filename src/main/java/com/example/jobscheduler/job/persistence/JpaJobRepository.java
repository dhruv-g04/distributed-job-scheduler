package com.example.jobscheduler.job.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.repository.JobRepository;

@Repository
public class JpaJobRepository implements JobRepository {

    private final SpringDataJobRepository springDataRepository;

    public JpaJobRepository(SpringDataJobRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    @Transactional
    public Job save(Job job) {
        return springDataRepository.save(new JobEntity(job)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Job> findById(UUID id) {
        return springDataRepository.findById(id).map(JobEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public JobRepository.Page findAll(int page, int size) {
        org.springframework.data.domain.Page<JobEntity> result = springDataRepository
                .findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size));
        return new JobRepository.Page(
                result.getContent().stream().map(JobEntity::toDomain).toList(),
                result.getTotalElements(),
                page,
                size);
    }
}