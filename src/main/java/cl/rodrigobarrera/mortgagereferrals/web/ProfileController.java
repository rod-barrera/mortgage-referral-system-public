package cl.rodrigobarrera.mortgagereferrals.web;

import cl.rodrigobarrera.mortgagereferrals.config.AccountPrincipal;
import cl.rodrigobarrera.mortgagereferrals.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/profile")
public class ProfileController {
    private final UserAccountService users;

    public ProfileController(UserAccountService users) {
        this.users = users;
    }

    @GetMapping
    String profile(@AuthenticationPrincipal AccountPrincipal principal, Model model) {
        AccountView account = users.getAccount(principal.accountId());
        model.addAttribute("account", account);
        model.addAttribute("detailsForm", new AccountDetailsForm(account));
        model.addAttribute("passwordForm", new PasswordChangeForm());
        return "profile";
    }

    @PostMapping
    String update(@AuthenticationPrincipal AccountPrincipal principal,
                  @Valid @ModelAttribute("detailsForm") AccountDetailsForm form, BindingResult errors,
                  HttpServletRequest request, HttpServletResponse response, Model model) {
        long id = principal.accountId();
        if (!errors.hasErrors()) {
            try {
                if (users.editProfile(id, form)) {
                    return endSession(request, response);
                }
                return "redirect:/profile?updated=1";
            } catch (IllegalArgumentException e) {
                errors.rejectValue("email", "account.email", e.getMessage());
            } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                errors.reject("account", "No se pudieron guardar los datos. Intenta de nuevo.");
            }
        }
        model.addAttribute("account", users.getAccount(id));
        model.addAttribute("passwordForm", new PasswordChangeForm());
        return "profile";
    }

    @PostMapping("/password")
    String changePassword(@AuthenticationPrincipal AccountPrincipal principal,
                          @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult errors,
                          HttpServletRequest request, HttpServletResponse response, Model model) {
        long id = principal.accountId();
        if (!errors.hasErrors() && !form.getNewPassword().equals(form.getConfirmPassword())) {
            errors.rejectValue("confirmPassword", "password.confirm", "Las contraseñas nuevas no coinciden.");
        }
        if (!errors.hasErrors()) {
            try {
                if (users.changePassword(id, form.getCurrentPassword(), form.getNewPassword())) {
                    return endSession(request, response);
                }
                errors.rejectValue("currentPassword", "password.current", "La contraseña actual no coincide.");
            } catch (IllegalArgumentException e) {
                errors.rejectValue("newPassword", "password.length", e.getMessage());
            } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                errors.reject("password", "No se pudo cambiar la contraseña. Intenta de nuevo.");
            }
        }
        form.clear();
        AccountView account = users.getAccount(id);
        model.addAttribute("account", account);
        model.addAttribute("detailsForm", new AccountDetailsForm(account));
        return "profile";
    }

    private String endSession(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return "redirect:/login?credentialsUpdated=1";
    }
}
