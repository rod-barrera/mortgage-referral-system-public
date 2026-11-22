package cl.rodrigobarrera.mortgagereferrals.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "status_history", schema = "public")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referral_id", nullable = false)
    private Referral referral;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private UserAccount author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_status_id")
    private Status previousStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_status_id", nullable = false)
    private Status newStatus;

    @Column(name = "changed_at", nullable = false, insertable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime changedAt;

    @Column(name = "comment", nullable = false, columnDefinition = "text")
    private String comment = "";

    protected StatusHistory() {
    }

    public StatusHistory(Referral referral, UserAccount author, Status previousStatus, Status newStatus, String comment) {
        this.referral = referral;
        this.author = author;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public Referral getReferral() {
        return referral;
    }

    public Status getPreviousStatus() {
        return previousStatus;
    }

    public Status getNewStatus() {
        return newStatus;
    }

    public UserAccount getAuthor() {
        return author;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }

    public String getComment() {
        return comment;
    }

}
