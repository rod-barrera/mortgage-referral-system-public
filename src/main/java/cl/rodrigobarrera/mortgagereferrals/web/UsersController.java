package cl.rodrigobarrera.mortgagereferrals.web;

import cl.rodrigobarrera.mortgagereferrals.config.AccountPrincipal;
import cl.rodrigobarrera.mortgagereferrals.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UsersController {
    private final UserAccountService users;
    public UsersController(UserAccountService users) { this.users = users; }
    private void prepareModel(long actor, Model model) {
        model.addAttribute("account", users.getAccount(actor));
        model.addAttribute("users", users.listAccounts(actor));
    }
    @GetMapping("/users") String listAccounts(@AuthenticationPrincipal AccountPrincipal principal, Model model) {
        prepareModel(principal.accountId(), model);
        model.addAttribute("form", new UserAccountForm());
        return "users";
    }
    @PostMapping("/users") String create(@AuthenticationPrincipal AccountPrincipal principal,
                                          @Valid @ModelAttribute("form") UserAccountForm form,
                                          BindingResult errors, Model model) {
        if (!errors.hasErrors()) {
            try {
                users.createExecutive(principal.accountId(), form);
                return "redirect:/users?created=1";
            } catch (IllegalArgumentException e) {
                errors.reject("account", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errors.reject("account", "No se pudo crear la cuenta. Revisa los datos y el correo ingresado.");
            }
        }
        form.setPassword("");
        prepareModel(principal.accountId(), model);
        return "users";
    }

    @GetMapping("/users/{id}/edit")
    String edit(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id, Model model) {
        long actor = principal.accountId();
        AccountView target = users.getExecutive(actor, id);
        model.addAttribute("account", users.getAccount(actor));
        model.addAttribute("targetAccount", target);
        model.addAttribute("form", new AccountDetailsForm(target));
        return "user-edit";
    }

    @PostMapping("/users/{id}/edit")
    String update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                  @Valid @ModelAttribute("form") AccountDetailsForm form, BindingResult errors, Model model) {
        long actor = principal.accountId();
        if (!errors.hasErrors()) {
            try {
                users.editExecutive(actor, id, form);
                return "redirect:/users?edited=1";
            } catch (IllegalArgumentException e) {
                errors.rejectValue("email", "account.email", e.getMessage());
            } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                errors.reject("account", "No se pudieron guardar los datos. Intenta de nuevo.");
            }
        }
        model.addAttribute("targetAccount", users.getExecutive(actor, id));
        model.addAttribute("account", users.getAccount(actor));
        return "user-edit";
    }

    @PostMapping("/users/{id}/status") String status(@AuthenticationPrincipal AccountPrincipal principal,
                                                       @PathVariable long id,
                                                       @RequestParam boolean active) {
        users.changeActive(principal.accountId(), id, active);
        return "redirect:/users?updated=1";
    }
}
