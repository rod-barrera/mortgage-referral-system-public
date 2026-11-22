package cl.rodrigobarrera.mortgagereferrals.config;

import cl.rodrigobarrera.mortgagereferrals.repository.UserAccountRepository;
import cl.rodrigobarrera.mortgagereferrals.service.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }
    @Bean UserDetailsService userDetailsService(UserAccountRepository users) {
        return email -> {
            var u = users.findByEmail(UserAccountService.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas."));
            return new AccountPrincipal(u);
        };
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService details,
                                                PasswordEncoder encoder, UserAccountRepository users) throws Exception {
        var provider = new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(encoder);
        return http.authenticationProvider(provider)
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico", "/error", "/error/**").permitAll()
                .requestMatchers("/users", "/users/**").hasRole("SUPERVISOR")
                .requestMatchers("/leads", "/leads/**", "/referrals/new", "/referrals/*/edit").hasRole("EXECUTIVE")
                .requestMatchers("/referrals/*/receive", "/referrals/*/follow-ups", "/referrals/*/status").hasRole("SUPERVISOR")
                .anyRequest().authenticated())
            .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/", true).failureUrl("/login?error=1").permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout=1").invalidateHttpSession(true).deleteCookies("JSESSIONID"))
            .csrf(Customizer.withDefaults())
            .exceptionHandling(e -> e.accessDeniedPage("/error/403"))
            .addFilterAfter(new ActiveAccountFilter(users), AnonymousAuthenticationFilter.class)
            .build();
    }
    private static class ActiveAccountFilter extends OncePerRequestFilter {
        private final UserAccountRepository users;
        ActiveAccountFilter(UserAccountRepository users) { this.users = users; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                  FilterChain chain) throws ServletException, IOException {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && !request.getServletPath().startsWith("/css/") && !request.getServletPath().startsWith("/js/")) {
                if (!(auth.getPrincipal() instanceof AccountPrincipal principal)) {
                    new SecurityContextLogoutHandler().logout(request, response, auth);
                    response.sendRedirect(request.getContextPath() + "/login");
                    return;
                }
                var account = users.findById(principal.accountId()).orElse(null);
                String redirect = null;
                if (account == null || !account.isActive()
                    || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_" + account.getRole().getName()))) {
                    redirect = "/login?disabled=1";
                } else if (!principal.hasCurrentCredentials(account)) {
                    redirect = "/login?credentialsUpdated=1";
                }
                if (redirect != null) {
                    new SecurityContextLogoutHandler().logout(request, response, auth);
                    response.sendRedirect(request.getContextPath() + redirect);
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }
}
