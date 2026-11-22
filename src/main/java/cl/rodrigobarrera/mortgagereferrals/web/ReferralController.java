package cl.rodrigobarrera.mortgagereferrals.web;

import cl.rodrigobarrera.mortgagereferrals.config.AccountPrincipal;
import cl.rodrigobarrera.mortgagereferrals.service.LeadService;
import cl.rodrigobarrera.mortgagereferrals.service.ReferralService;
import cl.rodrigobarrera.mortgagereferrals.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/referrals")
public class ReferralController {
    private static final String CONCURRENT_CHANGE = "El caso cambió mientras lo consultabas. Revisa su estado actual e intenta nuevamente.";
    private final UserAccountService users;
    private final LeadService leads;
    private final ReferralService referrals;

    public ReferralController(UserAccountService users, LeadService leads, ReferralService referrals) {
        this.users = users;
        this.leads = leads;
        this.referrals = referrals;
    }

    @GetMapping("/new")
    String newReferral(@AuthenticationPrincipal AccountPrincipal principal, @RequestParam(required = false) Long leadId,
                       HttpServletRequest request, Model model) {
        AccountView account = executive(principal);
        if (leadId == null) return "redirect:/leads";

        ReferralForm form = new ReferralForm();
        form.setLeadId(leadId);
        model.addAttribute("form", form);
        return showNewForm(model, account, findSelectedLead(account, leadId, request));
    }

    @PostMapping("/new")
    String create(@AuthenticationPrincipal AccountPrincipal principal, @Valid @ModelAttribute("form") ReferralForm form,
                  BindingResult errors, HttpServletRequest request, Model model, RedirectAttributes redirect) {
        AccountView account = executive(principal);
        LeadView lead = form.getLeadId() == null ? null : findSelectedLead(account, form.getLeadId(), request);
        if (lead == null) errors.reject("lead", "Selecciona la ficha de un interesado.");
        checkAmounts(errors);
        if (!errors.hasErrors()) {
            try {
                Long id = referrals.create(account, lead.id(), form.getSummary(),
                    form.getPropertyValueUf(), form.getDownPaymentUf());
                return saved(redirect, id, "La derivación quedó registrada y está pendiente de recepción.");
            } catch (IllegalArgumentException e) {
                errors.reject("referral", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errors.reject("referral", "No se pudo guardar la derivación. Revisa los datos e intenta nuevamente.");
            }
        }
        return showNewForm(model, account, lead);
    }

    @GetMapping("/{id}")
    String detail(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Long id, Model model) {
        AccountView account = account(principal);
        return showDetail(model, account, referrals.detail(account, id));
    }

    @GetMapping("/{id}/edit")
    String edit(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Long id, Model model) {
        AccountView account = executive(principal);
        ReferralDetailView referral = referrals.detail(account, id);
        if (!referral.editable()) {
            model.addAttribute("actionError", "Solo puedes corregir una derivación pendiente de recepción.");
            return showDetail(model, account, referral);
        }

        ReferralForm form = new ReferralForm();
        form.setSummary(referral.summary());
        form.setPropertyValueUf(referral.propertyValueUf());
        form.setDownPaymentUf(referral.downPaymentUf());
        model.addAttribute("form", form);
        return showEditForm(model, account, referral);
    }

    @PostMapping("/{id}/edit")
    String correct(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Long id,
                   @Valid @ModelAttribute("form") ReferralForm form,
                   BindingResult errors, Model model, RedirectAttributes redirect) {
        AccountView account = executive(principal);
        checkAmounts(errors);
        if (!errors.hasErrors()) {
            try {
                referrals.correct(account, id, form.getSummary(), form.getPropertyValueUf(), form.getDownPaymentUf());
                return saved(redirect, id, "Los antecedentes de la derivación se actualizaron.");
            } catch (IllegalArgumentException e) {
                errors.reject("referral", e.getMessage());
            } catch (ConcurrencyFailureException e) {
                errors.reject("referral", CONCURRENT_CHANGE);
            } catch (DataIntegrityViolationException e) {
                errors.reject("referral", "No se pudieron guardar las correcciones. Revisa los datos.");
            }
        }
        ReferralDetailView referral = referrals.detail(account, id);
        if (!referral.editable()) {
            model.addAttribute("actionError", "El caso ya fue recibido. Los antecedentes registrados están disponibles en el detalle.");
            return showDetail(model, account, referral);
        }
        return showEditForm(model, account, referral);
    }

    @PostMapping("/{id}/receive")
    String receive(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Long id, Model model,
                   RedirectAttributes redirect) {
        AccountView account = account(principal);
        try {
            referrals.receive(account, id);
            return saved(redirect, id, "Se confirmó la recepción de la derivación.");
        } catch (IllegalArgumentException e) {
            model.addAttribute("actionError", e.getMessage());
        } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
            model.addAttribute("actionError", CONCURRENT_CHANGE);
        }
        return showDetail(model, account, referrals.detail(account, id));
    }

    @PostMapping("/{id}/follow-ups")
    String followUp(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Long id,
                    @Valid @ModelAttribute("followUpForm") FollowUpForm form, BindingResult errors,
                    Model model, RedirectAttributes redirect) {
        AccountView account = account(principal);
        if (!errors.hasErrors()) {
            try {
                referrals.addFollowUp(account, id, form.getDescription());
                return saved(redirect, id, "Se registró la gestión.");
            } catch (IllegalArgumentException e) {
                errors.reject("followUp", e.getMessage());
            } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
                errors.reject("followUp", CONCURRENT_CHANGE);
            }
        }
        return showDetail(model, account, referrals.detail(account, id));
    }

    @PostMapping("/{id}/status")
    String changeStatus(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Long id,
                        @Valid @ModelAttribute("statusForm") StatusChangeForm form, BindingResult errors,
                        Model model, RedirectAttributes redirect) {
        AccountView account = account(principal);
        if (errors.hasFieldErrors("expectedStatusId")) {
            errors.reject("status", "Recarga el caso para consultar su estado vigente.");
        }
        if (errors.hasFieldErrors("nextStatusId") && errors.getFieldError("nextStatusId").isBindingFailure()) {
            errors.rejectValue("nextStatusId", "statusFormat", "Selecciona un estado de la lista.");
        }
        if (!errors.hasErrors()) {
            try {
                referrals.changeStatus(account, id, form.getExpectedStatusId(), form.getNextStatusId(), form.getComment());
                return saved(redirect, id, "El estado de la derivación se actualizó.");
            } catch (IllegalArgumentException e) {
                errors.reject("status", e.getMessage());
            } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
                errors.reject("status", CONCURRENT_CHANGE);
            }
        }
        ReferralDetailView referral = referrals.detail(account, id);
        if (referral.canManage() && !referral.currentStatusId().equals(form.getExpectedStatusId())) {
            form.setExpectedStatusId(referral.currentStatusId());
            model.addAttribute("actionError", "Comprueba el estado vigente antes de volver a enviar el cambio.");
        }
        return showDetail(model, account, referral);
    }

    private String showNewForm(Model model, AccountView account, LeadView lead) {
        model.addAttribute("account", account);
        model.addAttribute("editing", false);
        model.addAttribute("lead", lead);
        return "referral-form";
    }

    private String showEditForm(Model model, AccountView account, ReferralDetailView referral) {
        model.addAttribute("account", account);
        model.addAttribute("editing", true);
        model.addAttribute("caseId", referral.id());
        model.addAttribute("lead", new LeadView(null, referral.leadName(), referral.rut(),
            referral.leadPhone(), referral.leadContactEmail()));
        return "referral-form";
    }

    private String showDetail(Model model, AccountView account, ReferralDetailView referral) {
        model.addAttribute("account", account);
        model.addAttribute("referral", referral);
        if (!model.containsAttribute("followUpForm")) {
            model.addAttribute("followUpForm", new FollowUpForm());
        }
        if (!model.containsAttribute("statusForm")) {
            StatusChangeForm form = new StatusChangeForm();
            form.setExpectedStatusId(referral.currentStatusId());
            model.addAttribute("statusForm", form);
        }
        return "referral-detail";
    }

    private LeadView findSelectedLead(AccountView account, Long leadId, HttpServletRequest request) {
        if (!LeadSelection.allows(request.getSession(false), leadId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ficha de interesado no disponible.");
        }
        return leads.findById(account, leadId).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ficha de interesado no disponible.")
        );
    }

    private AccountView account(AccountPrincipal principal) {
        return users.getAccount(principal.accountId());
    }

    private AccountView executive(AccountPrincipal principal) {
        AccountView account = account(principal);
        if (!"EXECUTIVE".equals(account.role())) {
            throw new AccessDeniedException("Esta operación requiere el perfil de ejecutivo.");
        }
        return account;
    }

    private void checkAmounts(BindingResult errors) {
        checkAmount(errors, "propertyValueUf");
        checkAmount(errors, "downPaymentUf");
    }

    private void checkAmount(BindingResult errors, String field) {
        if (errors.hasFieldErrors(field) && errors.getFieldError(field).isBindingFailure()) {
            errors.rejectValue(field, "amountFormat", "Ingresa un valor numérico en UF.");
        }
    }

    private String saved(RedirectAttributes redirect, Long id, String message) {
        redirect.addFlashAttribute("referralNotice", message);
        return "redirect:/referrals/" + id;
    }
}
