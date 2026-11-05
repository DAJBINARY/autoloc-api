package fr.ece.autoloc.web.mvc;

import lombok.Getter;
import lombok.Setter;

/**
 * Simulated card form. Its values are checked for shape, then dropped: they are never stored or logged.
 */
@Getter
@Setter
public class PaiementForm {

    private String titulaire;
    private String numero;
    private String expiration;
    private String cryptogramme;

    /** Luhn check, the one every real card number passes. */
    public boolean numeroValide() {
        String chiffres = numero == null ? "" : numero.replaceAll("[\\s-]", "");
        if (!chiffres.matches("\\d{13,19}")) {
            return false;
        }
        int somme = 0;
        for (int i = 0; i < chiffres.length(); i++) {
            int chiffre = chiffres.charAt(chiffres.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                chiffre = chiffre * 2 > 9 ? chiffre * 2 - 9 : chiffre * 2;
            }
            somme += chiffre;
        }
        return somme % 10 == 0;
    }

    public boolean expirationValide() {
        if (expiration == null || !expiration.strip().matches("(0[1-9]|1[0-2])/\\d{2}")) {
            return false;
        }
        String[] morceaux = expiration.strip().split("/");
        return !java.time.YearMonth.of(2000 + Integer.parseInt(morceaux[1]), Integer.parseInt(morceaux[0]))
                .isBefore(java.time.YearMonth.now());
    }

    public boolean cryptogrammeValide() {
        return cryptogramme != null && cryptogramme.strip().matches("\\d{3,4}");
    }
}
