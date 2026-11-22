package cl.rodrigobarrera.mortgagereferrals.service;

import cl.rodrigobarrera.mortgagereferrals.repository.ReferralRepository;
import cl.rodrigobarrera.mortgagereferrals.repository.StatusRepository;
import cl.rodrigobarrera.mortgagereferrals.web.ReferralView;
import cl.rodrigobarrera.mortgagereferrals.web.AccountView;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxService {
    private static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private static final int PAGE_SIZE = 15;
    private static final Pattern CASE_CODE = Pattern.compile("DER-([1-9][0-9]*)", Pattern.CASE_INSENSITIVE);
    private final ReferralRepository referrals;
    private final StatusRepository statuses;

    public InboxService(ReferralRepository referrals, StatusRepository statuses) {
        this.referrals = referrals;
        this.statuses = statuses;
    }

    @Transactional(readOnly = true)
    public Inbox getInbox(AccountView account, short statusId, long executiveId, String search, int page) {
        boolean supervisor = "SUPERVISOR".equals(account.role());
        if (!account.active() || (!supervisor && !"EXECUTIVE".equals(account.role()))) {
            throw new AccessDeniedException("No tienes permiso para consultar la bandeja.");
        }

        var stateOptions = statuses.findAll(Sort.by("id")).stream()
            .map(s -> new Option(s.getId().longValue(), s.getName())).toList();
        var executiveOptions = supervisor
            ? referrals.findExecutivesForSupervisor(account.id()).stream()
                .map(e -> new Option(e.getId(), e.getName())).toList()
            : List.<Option>of();
        var order = supervisor ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(Math.clamp(page, 0, Integer.MAX_VALUE / PAGE_SIZE),
            PAGE_SIZE, Sort.by(order, "createdAt", "id"));

        var match = CASE_CODE.matcher(search);
        long caseId = 0;
        String rut = "";
        try {
            if (match.matches()) {
                caseId = Long.parseLong(match.group(1));
            } else if (!search.isEmpty()) {
                rut = Rut.normalize(search);
            }
        } catch (IllegalArgumentException ex) {
            return new Inbox(Page.empty(pageable), stateOptions, executiveOptions,
                "Usa DER-12 para buscar un caso o ingresa un RUT válido.");
        }
        var authorized = referrals.findAuthorizedInbox(account.id(), supervisor, statusId,
            supervisor ? executiveId : 0, caseId, rut, pageable);
        return new Inbox(authorized.map(c -> new ReferralView(c.getId(), c.getLead().getName(),
            c.getExecutive().getName(), c.getCurrentStatus().getName(),
            c.getCreatedAt().atZoneSameInstant(ZONE).toOffsetDateTime())),
            stateOptions, executiveOptions, null);
    }

    public record Option(long id, String name) {}

    public record Inbox(Page<ReferralView> cases, List<Option> states, List<Option> executives,
                        String searchError) {}
}
