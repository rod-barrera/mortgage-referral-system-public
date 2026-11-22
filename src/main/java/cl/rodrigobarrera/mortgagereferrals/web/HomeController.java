package cl.rodrigobarrera.mortgagereferrals.web;

import cl.rodrigobarrera.mortgagereferrals.config.AccountPrincipal;
import cl.rodrigobarrera.mortgagereferrals.service.InboxService;
import cl.rodrigobarrera.mortgagereferrals.service.UserAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class HomeController {
    private final UserAccountService users;
    private final InboxService inbox;
    public HomeController(UserAccountService users, InboxService inbox) { this.users = users; this.inbox = inbox; }
    @GetMapping("/login") String login() { return "login"; }
    @GetMapping("/") String home(@AuthenticationPrincipal AccountPrincipal principal, Model model,
        @RequestParam(name = "state", defaultValue = "0") short state,
        @RequestParam(name = "executive", defaultValue = "0") long executive,
        @RequestParam(name = "q", defaultValue = "") String query,
        @RequestParam(name = "page", defaultValue = "0") int page) {
        AccountView account = users.getAccount(principal.accountId());
        String search = query.strip();
        var result = inbox.getInbox(account, state, executive, search, page);
        model.addAttribute("account", account);
        model.addAttribute("isSupervisor", "SUPERVISOR".equals(account.role()));
        model.addAttribute("referrals", result.cases().getContent());
        model.addAttribute("totalReferrals", result.cases().getTotalElements());
        model.addAttribute("states", result.states());
        model.addAttribute("executives", result.executives());
        model.addAttribute("selectedState", state);
        model.addAttribute("selectedExecutive", executive);
        model.addAttribute("query", search);
        model.addAttribute("searchError", result.searchError());
        model.addAttribute("page", result.cases());
        model.addAttribute("filtered", state != 0 || (executive != 0 && "SUPERVISOR".equals(account.role()))
            || !search.isEmpty());
        return "home";
    }
    @RequestMapping("/error/403") @ResponseStatus(HttpStatus.FORBIDDEN)
    String forbidden() { return "error/403"; }
}
