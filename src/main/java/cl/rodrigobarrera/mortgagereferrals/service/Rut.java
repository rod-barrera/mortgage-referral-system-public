package cl.rodrigobarrera.mortgagereferrals.service;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Rut {
    private static final Pattern FORMAT = Pattern.compile(
        "(?:[1-9][0-9]{0,7}-?|[1-9][0-9]{0,2}(?:\\.[0-9]{3}){1,2}-)[0-9K]");
    private static final String INVALID_RUT = "Ingresa un RUT válido con su dígito verificador.";

    private Rut() {
    }

    public static String normalize(String input) {
        String value = input == null ? "" : input.strip().toUpperCase(Locale.ROOT);
        if (value.length() > 12 || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(INVALID_RUT);
        }

        String digits = value.replace(".", "").replace("-", "");
        String body = digits.substring(0, digits.length() - 1);
        char verifier = digits.charAt(digits.length() - 1);
        int sum = 0;
        int weight = 2;
        for (int index = body.length() - 1; index >= 0; index--) {
            sum += (body.charAt(index) - '0') * weight;
            weight = weight == 7 ? 2 : weight + 1;
        }
        int checksum = 11 - sum % 11;
        char expected = switch (checksum) {
            case 11 -> '0';
            case 10 -> 'K';
            default -> (char) ('0' + checksum);
        };
        if (verifier != expected) {
            throw new IllegalArgumentException(INVALID_RUT);
        }
        return body + "-" + verifier;
    }
}
