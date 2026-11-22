package cl.rodrigobarrera.mortgagereferrals.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserAccountForm extends AccountDetailsForm {
    @NotBlank(message = "Ingresa una contraseña.")
    @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres.")
    private String password;
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
