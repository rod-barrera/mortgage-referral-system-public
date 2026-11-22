package cl.rodrigobarrera.mortgagereferrals.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ReferralDetailView(
    Long id,
    String leadName,
    String rut,
    String leadPhone,
    String leadContactEmail,
    String executiveName,
    String supervisorName,
    String summary,
    BigDecimal propertyValueUf,
    BigDecimal downPaymentUf,
    OffsetDateTime createdAt,
    Short currentStatusId,
    String statusName,
    boolean finalState,
    boolean editable,
    boolean canReceive,
    boolean canManage,
    List<Transition> transitions,
    List<Activity> activity
) {
    public record Transition(Short id, String name, boolean finalState) {}

    public record Activity(OffsetDateTime date, String author, String type, String detail) {}
}
