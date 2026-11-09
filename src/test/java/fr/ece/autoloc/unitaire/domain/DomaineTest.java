package fr.ece.autoloc.unitaire.domain;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.support.Donnees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DomaineTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 3, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 3, 4);

    private final Agence agence = Donnees.agence(1L);
    private final Client client = Donnees.client(1L, "alice@example.com", "P1");
    private final Vehicule vehicule = Donnees.vehicule(1L, "AA-111-AA", agence, StatutVehicule.DISPONIBLE);

    private Reservation reservation() {
        return Donnees.reservation(7L, client, vehicule, DEBUT, FIN, StatutReservation.CONFIRMEE);
    }

    @Test
    void uneReservationEstEnCoursEntreLeDepartEtLeRetour() {
        Reservation reservation = reservation();
        assertThat(reservation.isEnCours()).isFalse();

        reservation.marquerDepart(DEBUT);
        assertThat(reservation.isEnCours()).isTrue();

        reservation.marquerRetour(FIN);
        assertThat(reservation.isEnCours()).isFalse();
    }

    @Test
    void uneReservationReprendLeSuiviDUneAutre() {
        Reservation source = reservation();
        source.marquerDepart(DEBUT);
        source.marquerRetour(FIN);

        Reservation copie = reservation();
        copie.reprendreSuivi(source);

        assertThat(copie.getDateDepart()).isEqualTo(DEBUT);
        assertThat(copie.getDateRetour()).isEqualTo(FIN);
    }

    @Test
    void leLibelleDUneReservationAfficheLeClientEtLesDatesEnFrancais() {
        assertThat(reservation().getLibelle()).isEqualTo("N°7 – Alice Durand – 01/03/2026 au 04/03/2026");
    }

    @Test
    void leLibelleDUneReservationResteLisibleSansClient() {
        Reservation sansClient = Donnees.reservation(7L, null, vehicule, DEBUT, FIN, StatutReservation.EN_ATTENTE);

        assertThat(sansClient.getLibelle()).contains("Client non renseigné");
    }

    @Test
    void leLibelleDUnVehiculeReunitMarqueModeleEtImmatriculation() {
        assertThat(vehicule.getLibelle()).isEqualTo("Renault Clio (AA-111-AA)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AA-111-AA", "ZZ-999-ZZ", "polygenelubricants", "", "a"})
    void laTeinteDUnVehiculeEstToujoursUneDesCinqCouleurs(String immatriculation) {
        Vehicule teinte = Donnees.vehicule(1L, immatriculation, agence, StatutVehicule.DISPONIBLE);

        assertThat(teinte.getTeinte()).isBetween(0, 4);
        assertThat(teinte.getTeinte())
                .isEqualTo(Donnees.vehicule(2L, immatriculation, agence, StatutVehicule.LOUE).getTeinte());
    }

    @Test
    void leLibelleDUnContratAfficheSonClient() {
        Contrat contrat = new Contrat(3L, DEBUT, BigDecimal.TEN, true, reservation(), null);

        assertThat(contrat.getLibelle()).isEqualTo("Contrat n°3 – Alice Durand");
    }

    @Test
    void leLibelleDUnContratResteLisibleSansReservation() {
        Contrat contrat = new Contrat(3L, DEBUT, BigDecimal.TEN, true, null, null);

        assertThat(contrat.getLibelle()).isEqualTo("Contrat n°3 – Client non renseigné");
    }

    @Test
    void unClientNACompteQuAvecUnMotDePasse() {
        Client nouveau = Donnees.client(null, "bob@example.com", "P2");
        assertThat(nouveau.aUnCompte()).isFalse();

        nouveau.changerMotDePasse("hash");

        assertThat(nouveau.aUnCompte()).isTrue();
        assertThat(nouveau.getNomComplet()).isEqualTo("Alice Durand");
    }

    @Test
    void unEmployeNACompteQuAvecUnMotDePasse() {
        Employe employe = new Employe(null, "Martin", "Sophie", RoleEmploye.AGENT, agence);
        assertThat(employe.aUnCompte()).isFalse();

        employe.changerMotDePasse("hash");

        assertThat(employe.aUnCompte()).isTrue();
        assertThat(employe.getNomComplet()).isEqualTo("Sophie Martin");
    }
}
