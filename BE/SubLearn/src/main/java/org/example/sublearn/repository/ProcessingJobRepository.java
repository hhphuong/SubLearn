package org.example.sublearn.repository;

import org.example.sublearn.domain.FailureReason;
import org.example.sublearn.domain.JobStatus;
import org.example.sublearn.domain.JobType;
import org.example.sublearn.domain.ProcessingJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    long countByStatus(JobStatus status);

    long countByCreatedAtAfter(Instant after);

    long countByStatusAndCreatedAtAfter(JobStatus status, Instant after);

    long countByJobTypeAndStatus(JobType jobType, JobStatus status);

    long countByJobTypeAndStatusAndCreatedAtAfter(JobType jobType, JobStatus status, Instant after);

    long countByFailureReasonAndStatusAndCreatedAtAfter(FailureReason failureReason, JobStatus status, Instant after);

    @Query("""
            SELECT j.failureReason, COUNT(j) FROM ProcessingJob j
            WHERE j.status = org.example.sublearn.domain.JobStatus.FAILED
            AND j.failureReason IS NOT NULL
            AND (:since IS NULL OR j.createdAt >= :since)
            GROUP BY j.failureReason
            ORDER BY COUNT(j) DESC
            """)
    List<Object[]> countFailedByReasonSince(@Param("since") Instant since);

    @Query("""
            SELECT j.createdAt FROM ProcessingJob j
            WHERE j.status = org.example.sublearn.domain.JobStatus.FAILED
            AND (:since IS NULL OR j.createdAt >= :since)
            """)
    List<Instant> findFailedCreatedAtSince(@Param("since") Instant since);

    @EntityGraph(attributePaths = {"video", "requestedByUser"})
    @Query(
            value = """
                    SELECT j FROM ProcessingJob j
                    LEFT JOIN j.video v
                    LEFT JOIN j.requestedByUser u
                    WHERE (:status IS NULL OR j.status = :status)
                    AND (:jobType IS NULL OR j.jobType = :jobType)
                    AND (:failureReason IS NULL OR j.failureReason = :failureReason)
                    AND (:videoId IS NULL OR v.id = :videoId)
                    AND (:from IS NULL OR j.createdAt >= :from)
                    AND (:to IS NULL OR j.createdAt <= :to)
                    AND (:search IS NULL
                         OR (v.title IS NOT NULL AND LOWER(v.title) LIKE LOWER(CONCAT('%', :search, '%')))
                         OR LOWER(v.youtubeVideoId) LIKE LOWER(CONCAT('%', :search, '%'))
                         OR (u.email IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))))
                    """,
            countQuery = """
                    SELECT COUNT(j) FROM ProcessingJob j
                    LEFT JOIN j.video v
                    LEFT JOIN j.requestedByUser u
                    WHERE (:status IS NULL OR j.status = :status)
                    AND (:jobType IS NULL OR j.jobType = :jobType)
                    AND (:failureReason IS NULL OR j.failureReason = :failureReason)
                    AND (:videoId IS NULL OR v.id = :videoId)
                    AND (:from IS NULL OR j.createdAt >= :from)
                    AND (:to IS NULL OR j.createdAt <= :to)
                    AND (:search IS NULL
                         OR (v.title IS NOT NULL AND LOWER(v.title) LIKE LOWER(CONCAT('%', :search, '%')))
                         OR LOWER(v.youtubeVideoId) LIKE LOWER(CONCAT('%', :search, '%'))
                         OR (u.email IS NOT NULL AND LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))))
                    """)
    Page<ProcessingJob> searchAdminJobs(@Param("status") JobStatus status,
                                        @Param("jobType") JobType jobType,
                                        @Param("failureReason") FailureReason failureReason,
                                        @Param("videoId") Long videoId,
                                        @Param("search") String search,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to,
                                        Pageable pageable);
}
