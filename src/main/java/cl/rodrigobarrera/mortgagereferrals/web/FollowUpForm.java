package cl.rodrigobarrera.mortgagereferrals.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FollowUpForm {
    @NotBlank(message = "Describe la gestión realizada.")
    @Size(max = 4000, message = "La descripción admite hasta 4.000 caracteres.")
    private String description;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
