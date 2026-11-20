package org.example.sublearn.repository;

import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.JobType;
import org.example.sublearn.domain.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    Optional<ProcessingJob> findFirstByVideo_IdAndJobTypeInAndTargetLanguageAndStatusInOrderByCreatedAtDesc(
            Long videoId, Collection<JobType> jobTypes, String targetLanguage, Collection<JobStatus> statuses);

    Optional<ProcessingJob> findFirstByVideo_IdAndJobTypeInAndTargetLanguageOrderByCreatedAtDesc(
            Long videoId, Collection<JobType> jobTypes, String targetLanguage);

    Optional<ProcessingJob> findFirstByVideo_IdAndJobTypeOrderByCreatedAtDesc(Long videoId, JobType jobType);

    List<ProcessingJob> findByStatusIn(Collection<JobStatus> statuses);
}
