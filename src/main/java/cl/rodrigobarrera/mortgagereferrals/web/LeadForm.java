package cl.rodrigobarrera.mortgagereferrals.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LeadForm {
    @NotBlank(message = "Ingresa el nombre.")
    @Size(max = 120, message = "El nombre admite hasta 120 caracteres.")
    private String name;

    @NotBlank(message = "Ingresa el RUT.")
    @Size(max = 12, message = "Revisa el formato del RUT.")
    private String rut;

    @Size(max = 30, message = "El teléfono admite hasta 30 caracteres.")
    private String phone;

    @Email(message = "Ingresa un correo válido.")
    @Size(max = 254, message = "El correo admite hasta 254 caracteres.")
    private String contactEmail;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.strip();
    }

    public String getRut() {
        return rut;
    }

    public void setRut(String rut) {
        this.rut = rut == null ? null : rut.strip();
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = optionalContact(phone);
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = optionalContact(contactEmail);
    }

    public boolean hasContact() {
        return phone != null || contactEmail != null;
    }

    private static String optionalContact(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
