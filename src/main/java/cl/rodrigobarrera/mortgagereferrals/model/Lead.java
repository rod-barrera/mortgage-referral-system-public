package cl.rodrigobarrera.mortgagereferrals.model;

import jakarta.persistence.*;

@Entity
@Table(name = "lead", schema = "public")
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lead_id")
    private Long id;

    @Column(name = "rut", nullable = false, unique = true, length = 10)
    private String rut;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    public Lead() {
    }

    public Long getId() {
        return id;
    }

    public String getRut() {
        return rut;
    }

    public void setRut(String rut) {
        this.rut = rut;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

}
