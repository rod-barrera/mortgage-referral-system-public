package cl.rodrigobarrera.mortgagereferrals.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "follow_up", schema = "public")
public class FollowUp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "follow_up_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referral_id", nullable = false)
    private Referral referral;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private UserAccount author;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;

    protected FollowUp() {
    }

    public FollowUp(Referral referral, UserAccount author, String description) {
        this.referral = referral;
        this.author = author;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Referral getReferral() {
        return referral;
    }

    public UserAccount getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

}
