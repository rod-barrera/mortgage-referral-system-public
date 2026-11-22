package cl.rodrigobarrera.mortgagereferrals.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class StatusChangeForm {
    @NotNull(message = "Recarga el caso para consultar su estado vigente.")
    private Short expectedStatusId;

    @NotNull(message = "Selecciona el nuevo estado.")
    private Short nextStatusId;

    @Size(max = 4000, message = "La observación admite hasta 4.000 caracteres.")
    private String comment;

    public Short getExpectedStatusId() { return expectedStatusId; }
    public void setExpectedStatusId(Short expectedStatusId) { this.expectedStatusId = expectedStatusId; }
    public Short getNextStatusId() { return nextStatusId; }
    public void setNextStatusId(Short nextStatusId) { this.nextStatusId = nextStatusId; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
