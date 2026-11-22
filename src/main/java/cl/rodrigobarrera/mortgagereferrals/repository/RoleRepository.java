package cl.rodrigobarrera.mortgagereferrals.repository;

import cl.rodrigobarrera.mortgagereferrals.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Short> {
}
