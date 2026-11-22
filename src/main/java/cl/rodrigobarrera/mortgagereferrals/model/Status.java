package cl.rodrigobarrera.mortgagereferrals.model;

import jakarta.persistence.*;

@Entity
@Table(name = "status", schema = "public")
public class Status {

    @Id
    @Column(name = "status_id")
    private Short id;

    @Column(name = "name", nullable = false, unique = true, length = 32)
    private String name;

    @Column(name = "is_final", nullable = false)
    private boolean finalStatus;

    public Status() {
    }

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(boolean finalStatus) {
        this.finalStatus = finalStatus;
    }

    public boolean isFinal() {
        return finalStatus;
    }

}
