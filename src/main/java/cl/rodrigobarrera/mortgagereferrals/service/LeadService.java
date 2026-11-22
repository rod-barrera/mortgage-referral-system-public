package cl.rodrigobarrera.mortgagereferrals.service;

import cl.rodrigobarrera.mortgagereferrals.model.Lead;
import cl.rodrigobarrera.mortgagereferrals.repository.LeadRepository;
import cl.rodrigobarrera.mortgagereferrals.web.AccountView;
import cl.rodrigobarrera.mortgagereferrals.web.LeadForm;
import cl.rodrigobarrera.mortgagereferrals.web.LeadView;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {
    private final LeadRepository leads;

    public LeadService(LeadRepository leads) {
        this.leads = leads;
    }

    @Transactional(readOnly = true)
    public Optional<LeadView> findByRut(AccountView account, String rut) {
        requireExecutive(account);
        return leads.findByRut(Rut.normalize(rut)).map(LeadService::toView);
    }

    @Transactional(readOnly = true)
    public Optional<LeadView> findById(AccountView account, Long id) {
        requireExecutive(account);
        return leads.findById(id).map(LeadService::toView);
    }

    @Transactional
    public Registration register(AccountView account, LeadForm form) {
        requireExecutive(account);
        String rut = Rut.normalize(form.getRut());
        boolean created = leads.insertIfAbsent(rut, form.getName(), form.getPhone(), form.getContactEmail()) == 1;
        LeadView lead = leads.findByRut(rut).map(LeadService::toView).orElseThrow();
        return new Registration(lead, created);
    }

    public void requireExecutive(AccountView account) {
        if (!account.active() || !"EXECUTIVE".equals(account.role())) {
            throw new AccessDeniedException("Esta operación requiere el perfil ejecutivo.");
        }
    }

    private static LeadView toView(Lead lead) {
        return new LeadView(lead.getId(), lead.getName(), lead.getRut(), lead.getPhone(), lead.getContactEmail());
    }

    public record Registration(LeadView lead, boolean created) {
    }
}
