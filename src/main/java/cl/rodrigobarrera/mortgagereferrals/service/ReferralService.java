package cl.rodrigobarrera.mortgagereferrals.service;

import cl.rodrigobarrera.mortgagereferrals.model.FollowUp;
import cl.rodrigobarrera.mortgagereferrals.model.Referral;
import cl.rodrigobarrera.mortgagereferrals.model.Status;
import cl.rodrigobarrera.mortgagereferrals.model.StatusHistory;
import cl.rodrigobarrera.mortgagereferrals.model.UserAccount;
import cl.rodrigobarrera.mortgagereferrals.repository.FollowUpRepository;
import cl.rodrigobarrera.mortgagereferrals.repository.LeadRepository;
import cl.rodrigobarrera.mortgagereferrals.repository.ReferralRepository;
import cl.rodrigobarrera.mortgagereferrals.repository.StatusHistoryRepository;
import cl.rodrigobarrera.mortgagereferrals.repository.StatusRepository;
import cl.rodrigobarrera.mortgagereferrals.repository.UserAccountRepository;
import cl.rodrigobarrera.mortgagereferrals.web.AccountView;
import cl.rodrigobarrera.mortgagereferrals.web.ReferralDetailView;
import cl.rodrigobarrera.mortgagereferrals.web.ReferralDetailView.Activity;
import cl.rodrigobarrera.mortgagereferrals.web.ReferralDetailView.Transition;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReferralService {
    private static final String EXECUTIVE = "EXECUTIVE";
    private static final String SUPERVISOR = "SUPERVISOR";
    private static final short PENDING_RECEIPT = 1;
    private static final short RECEIVED = 2;
    private static final int TEXT_LIMIT = 4000;
    private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");

    private final ReferralRepository referrals;
    private final LeadRepository leads;
    private final UserAccountRepository users;
    private final StatusRepository statuses;
    private final StatusHistoryRepository history;
    private final FollowUpRepository followUps;

    public ReferralService(ReferralRepository referrals, LeadRepository leads, UserAccountRepository users,
                           StatusRepository statuses, StatusHistoryRepository history, FollowUpRepository followUps) {
        this.referrals = referrals;
        this.leads = leads;
        this.users = users;
        this.statuses = statuses;
        this.history = history;
        this.followUps = followUps;
    }

    @Transactional
    public Long create(AccountView account, Long leadId, String summary,
                       BigDecimal propertyValueUf, BigDecimal downPaymentUf) {
        var executive = requireActor(account, EXECUTIVE);
        if (leadId == null || leadId <= 0) {
            throw new IllegalArgumentException("Selecciona una ficha de interesado.");
        }
        var lead = leads.findById(leadId)
            .orElseThrow(() -> new IllegalArgumentException("La ficha de interesado no está disponible."));
        var supervisor = users.findFirstByRole_NameAndActiveTrueOrderByIdAsc(SUPERVISOR)
            .orElseThrow(() -> new IllegalArgumentException("No hay una supervisora activa para recibir la derivación."));

        var referral = new Referral();
        referral.setLead(lead);
        referral.setExecutive(executive);
        referral.setSupervisor(supervisor);
        applyDetails(referral, summary, propertyValueUf, downPaymentUf);
        referrals.save(referral);
        history.saveAndFlush(new StatusHistory(referral, executive, null, requireStatus(PENDING_RECEIPT), ""));
        return referral.getId();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReferralDetailView detail(AccountView account, Long id) {
        requireKnownProfile(account);
        var referral = requireOwnedReferral(account, id, false);
        var status = referral.getCurrentStatus();
        boolean pending = status.getId() == PENDING_RECEIPT;
        boolean supervisor = SUPERVISOR.equals(account.role());
        boolean canManage = supervisor && !pending && !status.isFinal();
        List<Transition> transitions = canManage
            ? statuses.findTransitionsFrom(status.getId()).stream()
                .map(next -> new Transition(next.getId(), next.getName(), next.isFinal()))
                .toList()
            : List.of();
        var lead = referral.getLead();
        return new ReferralDetailView(
            referral.getId(), lead.getName(), lead.getRut(), lead.getPhone(), lead.getContactEmail(),
            referral.getExecutive().getName(), referral.getSupervisor().getName(),
            referral.getSummary(), referral.getPropertyValueUf(), referral.getDownPaymentUf(),
            inSantiago(referral.getCreatedAt()), status.getId(), status.getName(), status.isFinal(),
            !supervisor && pending, supervisor && pending, canManage, transitions, activity(referral.getId())
        );
    }

    @Transactional
    public void correct(AccountView account, Long id, String summary,
                        BigDecimal propertyValueUf, BigDecimal downPaymentUf) {
        requireActor(account, EXECUTIVE);
        var referral = requireOwnedReferral(account, id, true);
        if (referral.getCurrentStatus().getId() != PENDING_RECEIPT) {
            throw new IllegalArgumentException("Solo puedes corregir una derivación pendiente de recepción.");
        }
        applyDetails(referral, summary, propertyValueUf, downPaymentUf);
    }

    @Transactional
    public void receive(AccountView account, Long id) {
        var supervisor = requireActor(account, SUPERVISOR);
        var referral = requireOwnedReferral(account, id, true);
        var current = referral.getCurrentStatus();
        if (current.getId() != PENDING_RECEIPT) {
            throw new IllegalArgumentException("La derivación ya fue recibida.");
        }
        history.saveAndFlush(new StatusHistory(referral, supervisor, current, requireStatus(RECEIVED), ""));
    }

    @Transactional
    public void addFollowUp(AccountView account, Long id, String description) {
        var supervisor = requireActor(account, SUPERVISOR);
        var referral = requireOwnedReferral(account, id, true);
        requireReceivedAndOpen(referral.getCurrentStatus());
        followUps.saveAndFlush(new FollowUp(referral, supervisor, requiredText(description, "una descripción")));
    }

    @Transactional
    public void changeStatus(AccountView account, Long id, Short expectedStatusId, Short nextStatusId, String comment) {
        var supervisor = requireActor(account, SUPERVISOR);
        var referral = requireOwnedReferral(account, id, true);
        var current = referral.getCurrentStatus();
        requireReceivedAndOpen(current);
        if (!current.getId().equals(expectedStatusId)) {
            throw new IllegalArgumentException("El estado cambió desde que abriste el formulario. Revisa el estado vigente.");
        }
        var next = statuses.findTransitionsFrom(current.getId()).stream()
            .filter(candidate -> candidate.getId().equals(nextStatusId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Selecciona un cambio permitido desde el estado vigente."));
        String observation = next.isFinal()
            ? requiredText(comment, "una observación de cierre")
            : limitedText(comment);
        history.saveAndFlush(new StatusHistory(referral, supervisor, current, next, observation));
    }

    private List<Activity> activity(Long id) {
        var entries = new ArrayList<Activity>();
        for (var event : history.findByReferralIdOrderByIdAsc(id)) {
            var previous = event.getPreviousStatus();
            String next = event.getNewStatus().getName();
            String type = "Cambio de estado";
            String detail = next;
            if (previous == null) {
                type = "Creación";
                detail = "Estado inicial " + next;
            } else {
                if (previous.getId() == PENDING_RECEIPT && event.getNewStatus().getId() == RECEIVED) {
                    type = "Recepción";
                }
                detail = previous.getName() + " → " + next;
            }
            if (!event.getComment().isBlank()) {
                detail += "\n" + event.getComment();
            }
            entries.add(new Activity(inSantiago(event.getChangedAt()), event.getAuthor().getName(), type, detail));
        }
        for (var followUp : followUps.findByReferralIdOrderByCreatedAtAscIdAsc(id)) {
            entries.add(new Activity(inSantiago(followUp.getCreatedAt()), followUp.getAuthor().getName(),
                "Gestión", followUp.getDescription()));
        }
        entries.sort(Comparator.comparing(entry -> entry.date().toInstant()));
        return List.copyOf(entries);
    }

    private Referral requireOwnedReferral(AccountView account, Long id, boolean forUpdate) {
        if (id == null || id <= 0) {
            throw unavailableReferral();
        }
        var referral = (forUpdate ? referrals.findByIdForUpdate(id) : referrals.findById(id))
            .orElseThrow(ReferralService::unavailableReferral);
        Long ownerId = SUPERVISOR.equals(account.role())
            ? referral.getSupervisor().getId()
            : referral.getExecutive().getId();
        if (!ownerId.equals(account.id())) {
            throw unavailableReferral();
        }
        return referral;
    }

    private static ResponseStatusException unavailableReferral() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "La derivación no está disponible.");
    }

    private UserAccount requireActor(AccountView account, String role) {
        requireKnownProfile(account);
        if (!role.equals(account.role())) {
            throw new AccessDeniedException("Esta operación no está habilitada para tu perfil.");
        }
        return users.findById(account.id())
            .filter(user -> user.isActive() && role.equals(user.getRole().getName()))
            .orElseThrow(() -> new AccessDeniedException("Cuenta no disponible."));
    }

    private static void requireKnownProfile(AccountView account) {
        if (account == null || !account.active() || account.id() == null
            || !(EXECUTIVE.equals(account.role()) || SUPERVISOR.equals(account.role()))) {
            throw new AccessDeniedException("Cuenta no disponible.");
        }
    }

    private Status requireStatus(short id) {
        return statuses.findById(id).orElseThrow(() -> new IllegalStateException("Falta un estado del catálogo."));
    }

    private static void requireReceivedAndOpen(Status current) {
        if (current.isFinal()) {
            throw new IllegalArgumentException("La derivación está cerrada.");
        }
        if (current.getId() == PENDING_RECEIPT) {
            throw new IllegalArgumentException("Confirma la recepción antes de continuar.");
        }
    }

    private static void applyDetails(Referral referral, String summary,
                                     BigDecimal propertyValueUf, BigDecimal downPaymentUf) {
        referral.setSummary(requiredText(summary, "un resumen"));
        referral.setPropertyValueUf(validatedUf(propertyValueUf, "valor de la propiedad"));
        referral.setDownPaymentUf(validatedUf(downPaymentUf, "pie"));
    }

    private static String requiredText(String value, String field) {
        String text = limitedText(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException("Ingresa " + field + ".");
        }
        return text;
    }

    private static String limitedText(String value) {
        String text = value == null ? "" : value.strip();
        if (text.length() > TEXT_LIMIT) {
            throw new IllegalArgumentException("El texto debe tener hasta " + TEXT_LIMIT + " caracteres.");
        }
        return text;
    }

    private static BigDecimal validatedUf(BigDecimal value, String field) {
        if (value == null) {
            return null;
        }
        if (value.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (value.signum() > 0 && (long) value.precision() - value.scale() <= 10) {
            BigDecimal normalized = value.stripTrailingZeros();
            if (normalized.scale() <= 2) {
                return normalized.setScale(2);
            }
        }
        throw new IllegalArgumentException(
            "El " + field + " debe estar entre 0 y 9999999999,99 UF y tener hasta dos decimales.");
    }

    private static OffsetDateTime inSantiago(OffsetDateTime time) {
        return time.atZoneSameInstant(SANTIAGO).toOffsetDateTime();
    }
}
