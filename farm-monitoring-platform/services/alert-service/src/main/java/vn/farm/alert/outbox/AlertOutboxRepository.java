package vn.farm.alert.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertOutboxRepository extends JpaRepository<AlertOutboxEvent, UUID> {
    @Query("""
            select e.eventId from AlertOutboxEvent e
            where e.publishedAt is null and e.nextRetryAt <= :now
            order by e.nextRetryAt, e.eventId
            """)
    List<UUID> findDueIds(@Param("now") Instant now, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from AlertOutboxEvent e where e.eventId = :id")
    Optional<AlertOutboxEvent> findForPublishing(@Param("id") UUID id);
}
