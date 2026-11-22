package cl.rodrigobarrera.mortgagereferrals.repository;

import cl.rodrigobarrera.mortgagereferrals.model.Referral;
import cl.rodrigobarrera.mortgagereferrals.model.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    @EntityGraph(attributePaths = {"lead", "executive", "currentStatus"})
    @Query("""
        select r from Referral r
        where ((:supervisor = true and r.supervisor.id = :accountId)
            or (:supervisor = false and r.executive.id = :accountId))
          and (:statusId = 0 or r.currentStatus.id = :statusId)
          and (:executiveId = 0 or r.executive.id = :executiveId)
          and (:caseId = 0 or r.id = :caseId)
          and (:rut = '' or r.lead.rut = :rut)
        """)
    Page<Referral> findAuthorizedInbox(@Param("accountId") Long accountId,
        @Param("supervisor") boolean supervisor, @Param("statusId") short statusId,
        @Param("executiveId") long executiveId, @Param("caseId") long caseId,
        @Param("rut") String rut, Pageable pageable);

    @Query("""
        select distinct e from Referral r join r.executive e
        where r.supervisor.id = :supervisorId order by e.name, e.id
        """)
    List<UserAccount> findExecutivesForSupervisor(@Param("supervisorId") Long supervisorId);

    long countByExecutiveId(Long executiveId);

    long countBySupervisorId(Long supervisorId);

    @Override
    @EntityGraph(attributePaths = {"lead", "executive", "supervisor", "currentStatus"})
    Optional<Referral> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select referral from Referral referral where referral.id = :id")
    Optional<Referral> findByIdForUpdate(@Param("id") Long id);
}
