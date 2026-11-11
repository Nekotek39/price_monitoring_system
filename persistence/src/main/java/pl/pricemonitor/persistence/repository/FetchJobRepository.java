package pl.pricemonitor.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.pricemonitor.persistence.entity.FetchJob;
import pl.pricemonitor.persistence.entity.JobStatus;
import pl.pricemonitor.persistence.entity.JobType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface FetchJobRepository extends JpaRepository<FetchJob, Long> {

    boolean existsByShop_IdAndProduct_IdAndStatusIn(Long shopId, Long productId, Collection<JobStatus> statuses);

    boolean existsByShop_IdAndTypeAndStatusIn(Long shopId, JobType type, Collection<JobStatus> statuses);

    List<FetchJob> findByStatusInAndFinishedAtAfterOrderByFinishedAtAsc(Collection<JobStatus> statuses, Instant after);

    @Query("select max(j.finishedAt) from FetchJob j")
    Instant findLastFinishedAt();

    List<FetchJob> findTop100ByOrderByCreatedAtDesc();

    List<FetchJob> findTop100ByStatusOrderByCreatedAtDesc(JobStatus status);

    long countByStatus(JobStatus status);
}
