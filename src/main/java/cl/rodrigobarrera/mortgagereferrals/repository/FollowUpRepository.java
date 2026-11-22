package cl.rodrigobarrera.mortgagereferrals.repository;

import cl.rodrigobarrera.mortgagereferrals.model.FollowUp;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {

    @EntityGraph(attributePaths = {"referral", "author"})
    List<FollowUp> findByReferralIdOrderByCreatedAtAscIdAsc(Long referralId);
}
