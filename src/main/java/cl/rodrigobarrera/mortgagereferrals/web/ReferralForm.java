package cl.rodrigobarrera.mortgagereferrals.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class ReferralForm {
    private Long leadId;

    @NotBlank(message = "Ingresa el resumen de la consulta.")
    @Size(max = 4000, message = "El resumen admite hasta 4.000 caracteres.")
    private String summary;

    @DecimalMin(value = "0", message = "El valor de la propiedad debe ser igual o mayor a cero.")
    @Digits(integer = 10, fraction = 2, message = "Usa hasta 10 dígitos enteros y 2 decimales.")
    private BigDecimal propertyValueUf;

    @DecimalMin(value = "0", message = "El pie debe ser igual o mayor a cero.")
    @Digits(integer = 10, fraction = 2, message = "Usa hasta 10 dígitos enteros y 2 decimales.")
    private BigDecimal downPaymentUf;

    public Long getLeadId() { return leadId; }
    public void setLeadId(Long leadId) { this.leadId = leadId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public BigDecimal getPropertyValueUf() { return propertyValueUf; }
    public void setPropertyValueUf(BigDecimal propertyValueUf) { this.propertyValueUf = propertyValueUf; }
    public BigDecimal getDownPaymentUf() { return downPaymentUf; }
    public void setDownPaymentUf(BigDecimal downPaymentUf) { this.downPaymentUf = downPaymentUf; }
}
