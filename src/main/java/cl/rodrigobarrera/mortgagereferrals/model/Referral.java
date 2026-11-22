package cl.rodrigobarrera.mortgagereferrals.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "referral", schema = "public")
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "referral_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false, updatable = false)
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "executive_id", nullable = false, updatable = false)
    private UserAccount executive;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supervisor_id", nullable = false, updatable = false)
    private UserAccount supervisor;

    // PostgreSQL asigna el estado inicial y lo sincroniza con el historial.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_status_id", nullable = false, insertable = false, updatable = false)
    private Status currentStatus;

    @Column(name = "summary", nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;

    @Column(name = "property_value_uf", precision = 12, scale = 2)
    private BigDecimal propertyValueUf;

    @Column(name = "down_payment_uf", precision = 12, scale = 2)
    private BigDecimal downPaymentUf;

    public Referral() {
    }

    public Long getId() {
        return id;
    }

    public Lead getLead() {
        return lead;
    }

    public void setLead(Lead lead) {
        this.lead = lead;
    }

    public UserAccount getExecutive() {
        return executive;
    }

    public void setExecutive(UserAccount executive) {
        this.executive = executive;
    }

    public UserAccount getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(UserAccount supervisor) {
        this.supervisor = supervisor;
    }

    public Status getCurrentStatus() {
        return currentStatus;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getPropertyValueUf() {
        return propertyValueUf;
    }

    public void setPropertyValueUf(BigDecimal propertyValueUf) {
        this.propertyValueUf = propertyValueUf;
    }

    public BigDecimal getDownPaymentUf() {
        return downPaymentUf;
    }

    public void setDownPaymentUf(BigDecimal downPaymentUf) {
        this.downPaymentUf = downPaymentUf;
    }

}
