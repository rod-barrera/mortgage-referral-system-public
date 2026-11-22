package cl.rodrigobarrera.mortgagereferrals.model;

import jakarta.persistence.*;

@Entity
@Table(name = "role", schema = "public")
public class Role {

    @Id
    @Column(name = "role_id")
    private Short id;

    @Column(name = "name", nullable = false, unique = true, length = 20)
    private String name;

    public Role() {
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

}
