package cl.rodrigobarrera.mortgagereferrals.service;

import cl.rodrigobarrera.mortgagereferrals.model.UserAccount;
import cl.rodrigobarrera.mortgagereferrals.repository.RoleRepository;
import cl.rodrigobarrera.mortgagereferrals.repository.UserAccountRepository;
import cl.rodrigobarrera.mortgagereferrals.web.AccountDetailsForm;
import cl.rodrigobarrera.mortgagereferrals.web.AccountView;
import cl.rodrigobarrera.mortgagereferrals.web.UserAccountForm;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAccountService {
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    public UserAccountService(UserAccountRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users; this.roles = roles; this.encoder = encoder;
    }
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
    private UserAccount requireActiveAccount(Optional<UserAccount> account) {
        UserAccount userAccount = account
            .orElseThrow(() -> new AccessDeniedException("Cuenta no disponible."));
        if (!userAccount.isActive()) throw new AccessDeniedException("Cuenta no disponible.");
        return userAccount;
    }
    private void requireSupervisor(long id) {
        if (!"SUPERVISOR".equals(requireActiveAccount(users.findById(id)).getRole().getName()))
            throw new AccessDeniedException("Esta operación requiere el perfil de supervisora.");
    }
    private UserAccount requireExecutive(Optional<UserAccount> account) {
        UserAccount userAccount = account
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La cuenta no existe."));
        if (!"EXECUTIVE".equals(userAccount.getRole().getName())) {
            throw new AccessDeniedException("Solo se administran cuentas de ejecutivos.");
        }
        return userAccount;
    }
    private AccountView toView(UserAccount u) {
        return new AccountView(u.getId(), u.getName(), u.getEmail(), u.getRole().getName(), u.isActive());
    }

    @Transactional(readOnly = true)
    public AccountView getAccount(long id) {
        return toView(requireActiveAccount(users.findById(id)));
    }
    @Transactional(readOnly = true)
    public List<AccountView> listAccounts(long actor) {
        requireSupervisor(actor);
        return users.findAllByOrderByNameAsc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public AccountView getExecutive(long actor, long id) {
        requireSupervisor(actor);
        return toView(requireExecutive(users.findById(id)));
    }

    @Transactional
    public void createExecutive(long actor, UserAccountForm form) {
        requireSupervisor(actor);
        String email = normalizeEmail(form.getEmail());
        if (users.existsByEmail(email)) throw new IllegalArgumentException("Ya existe una cuenta con ese correo.");
        if (exceedsBcryptLimit(form.getPassword()))
            throw new IllegalArgumentException("La contraseña excede el máximo de 72 bytes; utiliza menos caracteres.");
        UserAccount newAccount = new UserAccount();
        newAccount.setRole(roles.findById((short) 1).orElseThrow());
        newAccount.setName(form.getName().strip());
        newAccount.setEmail(email);
        newAccount.setPasswordHash(encoder.encode(form.getPassword()));
        newAccount.setActive(true);
        users.saveAndFlush(newAccount);
    }

    @Transactional
    public void editExecutive(long actor, long id, AccountDetailsForm form) {
        requireSupervisor(actor);
        updateDetails(requireExecutive(users.findForUpdate(id)), form);
    }

    @Transactional
    public void changeActive(long actor, long id, boolean active) {
        requireSupervisor(actor);
        requireExecutive(users.findForUpdate(id)).setActive(active);
    }

    @Transactional
    public boolean editProfile(long id, AccountDetailsForm form) {
        return updateDetails(requireActiveAccount(users.findForUpdate(id)), form);
    }

    @Transactional
    public boolean changePassword(long id, String currentPassword, String newPassword) {
        if (exceedsBcryptLimit(newPassword)) {
            throw new IllegalArgumentException("La contraseña nueva admite hasta 72 bytes.");
        }
        UserAccount account = requireActiveAccount(users.findForUpdate(id));
        if (exceedsBcryptLimit(currentPassword) || !encoder.matches(currentPassword, account.getPasswordHash())) {
            return false;
        }
        account.setPasswordHash(encoder.encode(newPassword));
        return true;
    }

    private boolean updateDetails(UserAccount account, AccountDetailsForm form) {
        String email = normalizeEmail(form.getEmail());
        boolean emailChanged = !email.equals(account.getEmail());
        if (emailChanged && users.existsByEmail(email)) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese correo.");
        }
        account.setName(form.getName().strip());
        account.setEmail(email);
        try {
            // The unique constraint also covers concurrent edits to distinct accounts.
            users.flush();
        } catch (DataIntegrityViolationException e) {
            if (e.getMostSpecificCause() instanceof SQLException cause && "23505".equals(cause.getSQLState())) {
                throw new IllegalArgumentException("Ya existe una cuenta con ese correo.", e);
            }
            throw e;
        }
        return emailChanged;
    }

    private static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > 72;
    }
}
