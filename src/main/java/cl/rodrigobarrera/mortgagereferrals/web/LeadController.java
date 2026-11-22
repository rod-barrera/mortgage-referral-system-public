package cl.rodrigobarrera.mortgagereferrals.web;

import cl.rodrigobarrera.mortgagereferrals.config.AccountPrincipal;
import cl.rodrigobarrera.mortgagereferrals.service.LeadService;
import cl.rodrigobarrera.mortgagereferrals.service.UserAccountService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LeadController {
    private final LeadService leads;
    private final UserAccountService users;

    public LeadController(LeadService leads, UserAccountService users) {
        this.leads = leads;
        this.users = users;
    }

    @GetMapping("/leads")
    String lookup(@AuthenticationPrincipal AccountPrincipal principal, @RequestParam(required = false) String rut,
                  HttpSession session, Model model) {
        AccountView account = prepareModel(principal, model);
        LeadForm form = new LeadForm();
        model.addAttribute("form", form);
        if (rut == null) {
            return "leads";
        }
        model.addAttribute("lookupRut", rut);

        try {
            var match = leads.findByRut(account, rut);
            if (match.isPresent()) {
                LeadView lead = match.get();
                LeadSelection.grant(session, lead.id());
                model.addAttribute("lead", lead);
            } else {
                form.setRut(rut);
                model.addAttribute("lookupMissing", true);
            }
        } catch (IllegalArgumentException e) {
            model.addAttribute("lookupError", e.getMessage());
        }
        return "leads";
    }

    @PostMapping("/leads")
    String register(@AuthenticationPrincipal AccountPrincipal principal,
                    @Valid @ModelAttribute("form") LeadForm form, BindingResult errors,
                    HttpSession session, Model model, RedirectAttributes redirect) {
        AccountView account = prepareModel(principal, model);
        if (!form.hasContact()) {
            errors.reject("lead.contact.required", "Ingresa al menos un teléfono o correo.");
        }
        if (errors.hasErrors()) {
            return "leads";
        }

        try {
            var result = leads.register(account, form);
            LeadSelection.grant(session, result.lead().id());
            redirect.addAttribute("leadId", result.lead().id());
            redirect.addFlashAttribute("leadNotice", result.created()
                ? "La ficha se registró correctamente."
                : "Este RUT ya tiene una ficha. Se utilizarán sus datos registrados.");
            return "redirect:/referrals/new";
        } catch (IllegalArgumentException e) {
            errors.rejectValue("rut", "lead.rut.invalid", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            errors.reject("lead.registration", "No se pudo registrar la ficha. Revisa los datos ingresados.");
        }
        return "leads";
    }

    private AccountView prepareModel(AccountPrincipal principal, Model model) {
        AccountView account = users.getAccount(principal.accountId());
        leads.requireExecutive(account);
        model.addAttribute("account", account);
        return account;
    }
}
