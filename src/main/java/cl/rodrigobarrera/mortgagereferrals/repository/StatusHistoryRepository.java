package cl.rodrigobarrera.mortgagereferrals.repository;

import cl.rodrigobarrera.mortgagereferrals.model.StatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    @EntityGraph(attributePaths = {"referral", "author", "previousStatus", "newStatus"})
    List<StatusHistory> findByReferralIdOrderByIdAsc(Long referralId);
}
