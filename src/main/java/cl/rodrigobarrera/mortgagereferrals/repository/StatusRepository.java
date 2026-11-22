package cl.rodrigobarrera.mortgagereferrals.repository;

import cl.rodrigobarrera.mortgagereferrals.model.Status;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatusRepository extends JpaRepository<Status, Short> {
    @Query(value = """
        SELECT s.status_id, s.name, s.is_final
        FROM public.status_transition t
        JOIN public.status s ON s.status_id = t.to_status_id
        WHERE t.from_status_id = :fromId
        ORDER BY s.status_id
        """, nativeQuery = true)
    List<Status> findTransitionsFrom(@Param("fromId") short fromId);
}
