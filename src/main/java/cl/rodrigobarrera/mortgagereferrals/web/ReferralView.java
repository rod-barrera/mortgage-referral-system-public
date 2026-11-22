package cl.rodrigobarrera.mortgagereferrals.web;

import java.time.OffsetDateTime;

public record ReferralView(Long id, String leadName, String executiveName, String statusName,
                           OffsetDateTime createdAt) {}
