package fr.ece.autoloc.unitaire.web;

import fr.ece.autoloc.web.mvc.PaiementForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class PaiementFormTest {

    private static final DateTimeFormatter MM_AA = DateTimeFormatter.ofPattern("MM/yy");

    private final PaiementForm form = new PaiementForm();

    @ParameterizedTest
    @ValueSource(strings = {"4242424242424242", "4242 4242 4242 4242", "4242-4242-4242-4242", "5555555555554444"})
    void accepteUnNumeroDeCarteConformeAuControleDeLuhn(String numero) {
        form.setNumero(numero);

        assertThat(form.numeroValide()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234 5678 9012 3456", "4242", "abcd abcd abcd abcd", "42424242424242424242"})
    void refuseUnNumeroDeCarteInvalide(String numero) {
        form.setNumero(numero);

        assertThat(form.numeroValide()).isFalse();
    }

    @Test
    void accepteUneCarteQuiExpireCeMoisCi() {
        form.setExpiration(YearMonth.now().format(MM_AA));

        assertThat(form.expirationValide()).isTrue();
    }

    @Test
    void refuseUneCarteExpireeLeMoisDernier() {
        form.setExpiration(YearMonth.now().minusMonths(1).format(MM_AA));

        assertThat(form.expirationValide()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"13/30", "00/30", "1/30", "12-30", "12/2030"})
    void refuseUneExpirationMalFormee(String expiration) {
        form.setExpiration(expiration);

        assertThat(form.expirationValide()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "1234", " 123 "})
    void accepteUnCryptogrammeDeTroisOuQuatreChiffres(String cryptogramme) {
        form.setCryptogramme(cryptogramme);

        assertThat(form.cryptogrammeValide()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12", "12345", "abc"})
    void refuseUnCryptogrammeInvalide(String cryptogramme) {
        form.setCryptogramme(cryptogramme);

        assertThat(form.cryptogrammeValide()).isFalse();
    }
}
