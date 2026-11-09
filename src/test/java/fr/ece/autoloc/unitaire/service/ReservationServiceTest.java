package fr.ece.autoloc.unitaire.service;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.service.RegleMetierException;
import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.support.Donnees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Long ID = 10L;
    private static final LocalDate DEBUT = LocalDate.of(2026, 3, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 3, 3);

    @Mock
    private ReservationRepository repository;
    @Mock
    private MaintenanceRepository maintenances;
    @InjectMocks
    private ReservationService service;

    private final Agence agence = Donnees.agence(1L);
    private final Client client = Donnees.client(1L, "alice@example.com", "P1");
    private final Vehicule vehicule = Donnees.vehicule(2L, "AA-111-AA", agence, StatutVehicule.DISPONIBLE);

    private Reservation existante(StatutReservation statut) {
        Reservation reservation = Donnees.reservation(ID, client, vehicule, DEBUT, FIN, statut);
        when(repository.findById(ID)).thenReturn(Optional.of(reservation));
        return reservation;
    }

    @Test
    void confirmeUneReservationEnAttente() {
        Reservation reservation = existante(StatutReservation.EN_ATTENTE);

        service.confirmer(ID);

        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
    }

    @ParameterizedTest
    @EnumSource(value = StatutReservation.class, names = "EN_ATTENTE", mode = EnumSource.Mode.EXCLUDE)
    void neConfirmeQuUneReservationEnAttente(StatutReservation statut) {
        Reservation reservation = existante(statut);

        assertThatThrownBy(() -> service.confirmer(ID))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("en attente");
        assertThat(reservation.getStatut()).isEqualTo(statut);
    }

    @Test
    void neConfirmePasUneReservationSansVehicule() {
        Reservation reservation = existante(StatutReservation.EN_ATTENTE);
        reservation.setVehicule(null);

        assertThatThrownBy(() -> service.confirmer(ID))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Renseignez d'abord");
    }

    @Test
    void signaleUneReservationInconnue() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmer(ID)).isInstanceOf(NoSuchElementException.class);
    }

    @ParameterizedTest
    @EnumSource(value = StatutReservation.class, names = {"EN_ATTENTE", "CONFIRMEE"})
    void annuleUneReservationActive(StatutReservation statut) {
        Reservation reservation = existante(statut);

        service.annuler(ID);

        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.ANNULEE);
    }

    @ParameterizedTest
    @EnumSource(value = StatutReservation.class, names = {"ANNULEE", "TERMINEE"})
    void nAnnulePasUneReservationDejaFinie(StatutReservation statut) {
        existante(statut);

        assertThatThrownBy(() -> service.annuler(ID)).isInstanceOf(RegleMetierException.class);
    }

    @Test
    void nAnnulePasUneReservationDontLeVehiculeEstParti() {
        Reservation reservation = existante(StatutReservation.CONFIRMEE);
        reservation.marquerDepart(DEBUT);

        assertThatThrownBy(() -> service.annuler(ID))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("déjà parti");
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
    }

    @Test
    void leDepartLoueLeVehiculeEtDateLaReservation() {
        Reservation reservation = existante(StatutReservation.CONFIRMEE);

        service.enregistrerDepart(ID);

        assertThat(reservation.getDateDepart()).isEqualTo(LocalDate.now());
        assertThat(reservation.isEnCours()).isTrue();
        assertThat(vehicule.getStatut()).isEqualTo(StatutVehicule.LOUE);
    }

    @Test
    void refuseLeDepartDUneReservationNonConfirmee() {
        Reservation reservation = existante(StatutReservation.EN_ATTENTE);

        assertThatThrownBy(() -> service.enregistrerDepart(ID)).isInstanceOf(RegleMetierException.class);
        assertThat(reservation.getDateDepart()).isNull();
        assertThat(vehicule.getStatut()).isEqualTo(StatutVehicule.DISPONIBLE);
    }

    @Test
    void refuseUnDeuxiemeDepart() {
        Reservation reservation = existante(StatutReservation.CONFIRMEE);
        reservation.marquerDepart(DEBUT);

        assertThatThrownBy(() -> service.enregistrerDepart(ID)).isInstanceOf(RegleMetierException.class);
        assertThat(reservation.getDateDepart()).isEqualTo(DEBUT);
    }

    @ParameterizedTest
    @EnumSource(value = StatutVehicule.class, names = {"LOUE", "MAINTENANCE"})
    void refuseLeDepartDUnVehiculeIndisponible(StatutVehicule statut) {
        existante(StatutReservation.CONFIRMEE);
        vehicule.setStatut(statut);

        assertThatThrownBy(() -> service.enregistrerDepart(ID))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("pas disponible");
        assertThat(vehicule.getStatut()).isEqualTo(statut);
    }

    @Test
    void leRetourTermineLaReservationEtLibereLeVehicule() {
        Reservation reservation = existante(StatutReservation.CONFIRMEE);
        reservation.marquerDepart(DEBUT);
        vehicule.setStatut(StatutVehicule.LOUE);
        when(maintenances.existsByVehicule_IdVehiculeAndDateFinIsNull(vehicule.getIdVehicule())).thenReturn(false);

        service.enregistrerRetour(ID);

        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.TERMINEE);
        assertThat(reservation.getDateRetour()).isEqualTo(LocalDate.now());
        assertThat(vehicule.getStatut()).isEqualTo(StatutVehicule.DISPONIBLE);
    }

    @Test
    void leRetourMetLeVehiculeEnMaintenanceSiDesTravauxSontOuverts() {
        Reservation reservation = existante(StatutReservation.CONFIRMEE);
        reservation.marquerDepart(DEBUT);
        vehicule.setStatut(StatutVehicule.LOUE);
        when(maintenances.existsByVehicule_IdVehiculeAndDateFinIsNull(vehicule.getIdVehicule())).thenReturn(true);

        service.enregistrerRetour(ID);

        assertThat(vehicule.getStatut()).isEqualTo(StatutVehicule.MAINTENANCE);
    }

    @Test
    void refuseLeRetourSansDepart() {
        existante(StatutReservation.CONFIRMEE);

        assertThatThrownBy(() -> service.enregistrerRetour(ID))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Aucun départ");
    }

    @ParameterizedTest
    @CsvSource({"2026-03-01,2026-03-01,40.00", "2026-03-01,2026-03-03,120.00", "2026-02-28,2026-03-01,80.00"})
    void facturePremierEtDernierJour(LocalDate debut, LocalDate fin, BigDecimal attendu) {
        assertThat(service.estimer(new BigDecimal("40.00"), debut, fin)).isEqualByComparingTo(attendu);
    }

    @Test
    void estimeLeMontantDUneReservationAvecLeTarifDeSonVehicule() {
        Reservation reservation = Donnees.reservation(ID, client, vehicule, DEBUT, FIN, StatutReservation.EN_ATTENTE);

        assertThat(service.montantEstime(reservation)).isEqualByComparingTo("120.00");
    }

    @Test
    void chercheLesChevauchementsSansExclusionPourUneNouvelleReservation() {
        when(repository.compterChevauchements(2L, DEBUT, FIN, -1L)).thenReturn(1L);

        assertThat(service.vehiculeDejaReserve(2L, DEBUT, FIN, null)).isTrue();
    }

    @Test
    void neComptePasLaReservationModifieeDansLesChevauchements() {
        when(repository.compterChevauchements(2L, DEBUT, FIN, ID)).thenReturn(0L);

        assertThat(service.vehiculeDejaReserve(2L, DEBUT, FIN, ID)).isFalse();
    }
}
