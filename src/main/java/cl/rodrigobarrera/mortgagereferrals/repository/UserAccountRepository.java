package cl.rodrigobarrera.mortgagereferrals.repository;

import cl.rodrigobarrera.mortgagereferrals.model.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @EntityGraph(attributePaths = "role")
    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    @Override
    @EntityGraph(attributePaths = "role")
    Optional<UserAccount> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccount account where account.id = :id")
    Optional<UserAccount> findForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "role")
    List<UserAccount> findAllByOrderByNameAsc();

    Optional<UserAccount> findFirstByRole_NameAndActiveTrueOrderByIdAsc(String roleName);
}
