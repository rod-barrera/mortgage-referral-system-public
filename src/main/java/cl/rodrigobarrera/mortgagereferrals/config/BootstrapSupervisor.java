package cl.rodrigobarrera.mortgagereferrals.config;

import cl.rodrigobarrera.mortgagereferrals.model.UserAccount;
import cl.rodrigobarrera.mortgagereferrals.repository.RoleRepository;
import cl.rodrigobarrera.mortgagereferrals.repository.UserAccountRepository;
import cl.rodrigobarrera.mortgagereferrals.service.UserAccountService;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapSupervisor implements ApplicationRunner {
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final Environment env;
    public BootstrapSupervisor(UserAccountRepository users, RoleRepository roles, PasswordEncoder encoder, Environment env) {
        this.users = users; this.roles = roles; this.encoder = encoder; this.env = env;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        String email = UserAccountService.normalizeEmail(env.getProperty("BOOTSTRAP_SUPERVISOR_EMAIL"));
        if (email.isBlank()) return;
        if (users.existsByEmail(email)) return;
        if (users.findAll().stream().anyMatch(u -> "SUPERVISOR".equals(u.getRole().getName()))) return;
        String password = env.getProperty("BOOTSTRAP_SUPERVISOR_PASSWORD", "");
        if (password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("La contraseña inicial debe tener al menos 10 caracteres y hasta 72 bytes.");
        UserAccount u = new UserAccount();
        u.setRole(roles.findById((short) 2).orElseThrow());
        u.setName(env.getProperty("BOOTSTRAP_SUPERVISOR_NAME", "Supervisora de demostración"));
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setActive(true);
        users.save(u);
    }
}
