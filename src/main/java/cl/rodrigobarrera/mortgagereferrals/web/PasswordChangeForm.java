package cl.rodrigobarrera.mortgagereferrals.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordChangeForm {
    @NotBlank(message = "Ingresa tu contraseña actual.")
    @Size(max = 72, message = "La contraseña actual admite hasta 72 caracteres.")
    private String currentPassword;

    @NotBlank(message = "Ingresa la contraseña nueva.")
    @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres.")
    private String newPassword;

    @NotBlank(message = "Confirma la contraseña nueva.")
    @Size(max = 72, message = "La confirmación admite hasta 72 caracteres.")
    private String confirmPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public void clear() {
        currentPassword = null;
        newPassword = null;
        confirmPassword = null;
    }
}
