package fr.ece.autoloc.unitaire.domain;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.support.Donnees;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationDomaineTest {

    private static final Validator VALIDATEUR = Validation.buildDefaultValidatorFactory().getValidator();

    private final Agence agence = Donnees.agence(1L);

    private Vehicule vehicule() {
        return Donnees.vehicule(null, "AA-111-AA", agence, StatutVehicule.DISPONIBLE);
    }

    private static <T> List<String> champsInvalides(T objet) {
        return VALIDATEUR.validate(objet).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .toList();
    }

    @Test
    void accepteLesObjetsComplets() {
        assertThat(champsInvalides(vehicule())).isEmpty();
        assertThat(champsInvalides(Donnees.client(null, "alice@example.com", "P1"))).isEmpty();
        assertThat(champsInvalides(agence)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123456789012345678901"})
    void refuseUneImmatriculationVideOuTropLongue(String immatriculation) {
        Vehicule vehicule = vehicule();
        vehicule.setImmatriculation(immatriculation);

        assertThat(champsInvalides(vehicule)).containsExactly("immatriculation");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "10.123", "123456789"})
    void refuseUnTarifNulNegatifOuTropPrecis(String tarif) {
        Vehicule vehicule = vehicule();
        vehicule.setTarifJournalier(new BigDecimal(tarif));

        assertThat(champsInvalides(vehicule)).containsExactly("tarifJournalier");
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,true", "500,true", "501,false"})
    void bornesLaPlaceDeParking(int place, boolean valide) {
        Vehicule vehicule = vehicule();
        vehicule.setPlaceParking(place);

        assertThat(champsInvalides(vehicule).isEmpty()).isEqualTo(valide);
    }

    @Test
    void unVehiculeDoitAvoirUneAgence() {
        Vehicule vehicule = vehicule();
        vehicule.setAgence(null);

        assertThat(champsInvalides(vehicule)).containsExactly("agence");
    }

    @ParameterizedTest
    @ValueSource(strings = {"pas-un-email", "@example.com", ""})
    void refuseUnEmailInvalide(String email) {
        Client client = Donnees.client(null, email, "P1");

        assertThat(champsInvalides(client)).contains("email");
    }

    @ParameterizedTest
    @CsvSource({"court,false", "12345678,true"})
    void leNouveauMotDePasseDUnEmployeFaitAuMoinsHuitCaracteres(String motDePasse, boolean valide) {
        Employe employe = new Employe(null, "Martin", "Sophie", RoleEmploye.AGENT, agence);
        employe.setNouveauMotDePasse(motDePasse);

        assertThat(champsInvalides(employe).isEmpty()).isEqualTo(valide);
    }

    @Test
    void uneMaintenanceDoitDecrireLesTravauxEnMoinsDe500Caracteres() {
        Maintenance vide = new Maintenance(null, LocalDate.now(), null, " ", vehicule());
        Maintenance longue = new Maintenance(null, LocalDate.now(), null, "x".repeat(501), vehicule());
        Maintenance juste = new Maintenance(null, LocalDate.now(), null, "x".repeat(500), vehicule());

        assertThat(champsInvalides(vide)).containsExactly("description");
        assertThat(champsInvalides(longue)).containsExactly("description");
        assertThat(champsInvalides(juste)).isEmpty();
    }

    @Test
    void unPaiementExigeUnMontantPositifUnModeEtUnContrat() {
        Paiement paiement = new Paiement(null, BigDecimal.ZERO, LocalDate.now(), null, null);

        assertThat(champsInvalides(paiement)).containsExactlyInAnyOrder("montant", "modePaiement", "contrat");
    }

    @Test
    void lesMessagesDErreurSontEnFrancais() {
        Vehicule vehicule = vehicule();
        vehicule.setMarque("");

        assertThat(VALIDATEUR.validate(vehicule)).extracting(ConstraintViolation::getMessage)
                .containsExactly("Ce champ est obligatoire.");
    }
}
