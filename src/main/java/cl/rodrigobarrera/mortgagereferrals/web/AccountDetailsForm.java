package cl.rodrigobarrera.mortgagereferrals.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AccountDetailsForm {
    @NotBlank(message = "Ingresa el nombre.")
    @Size(max = 120, message = "El nombre admite hasta 120 caracteres.")
    private String name;

    @NotBlank(message = "Ingresa el correo.")
    @Email(message = "Ingresa un correo válido.")
    @Size(max = 254, message = "El correo admite hasta 254 caracteres.")
    private String email;

    public AccountDetailsForm() {}

    public AccountDetailsForm(AccountView account) {
        name = account.name();
        email = account.email();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
