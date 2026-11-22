package cl.rodrigobarrera.mortgagereferrals.web;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.util.WebUtils;

public final class LeadSelection {
    private static final String ATTRIBUTE = LeadSelection.class.getName();
    private static final int MAX_SELECTIONS = 16;

    private LeadSelection() {
    }

    public static void grant(HttpSession session, Long leadId) {
        synchronized (WebUtils.getSessionMutex(session)) {
            List<Long> recent = new ArrayList<>();
            recent.add(leadId);
            if (session.getAttribute(ATTRIBUTE) instanceof List<?> previous) {
                for (Object item : previous) {
                    if (recent.size() == MAX_SELECTIONS) {
                        break;
                    }
                    if (item instanceof Long id && !leadId.equals(id)) {
                        recent.add(id);
                    }
                }
            }
            session.setAttribute(ATTRIBUTE, List.copyOf(recent));
        }
    }

    public static boolean allows(HttpSession session, Long leadId) {
        return session != null && leadId != null
            && session.getAttribute(ATTRIBUTE) instanceof List<?> selected && selected.contains(leadId);
    }
}
