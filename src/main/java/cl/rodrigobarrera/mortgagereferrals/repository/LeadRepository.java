package cl.rodrigobarrera.mortgagereferrals.repository;

import cl.rodrigobarrera.mortgagereferrals.model.Lead;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    Optional<Lead> findByRut(String rut);

    boolean existsByRut(String rut);

    @Modifying
    @Query(value = """
        INSERT INTO public.lead (rut, name, phone, contact_email)
        VALUES (:rut, :name, :phone, :contactEmail)
        ON CONFLICT (rut) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("rut") String rut, @Param("name") String name,
                       @Param("phone") String phone, @Param("contactEmail") String contactEmail);
}
