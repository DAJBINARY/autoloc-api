package fr.ece.autoloc.unitaire.service;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.ParkingService;
import fr.ece.autoloc.service.ParkingService.Place;
import fr.ece.autoloc.service.PlanningService;
import fr.ece.autoloc.service.PlanningService.Case;
import fr.ece.autoloc.service.PlanningService.Ligne;
import fr.ece.autoloc.service.VehiculeService;
import fr.ece.autoloc.support.Donnees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The agency parking plan and the monthly planning grid.
 */
@ExtendWith(MockitoExtension.class)
class PlanificationServicesTest {

    private static final YearMonth FEVRIER = YearMonth.of(2026, 2);
    private static final Long ID_AGENCE = 1L;

    @Mock
    private VehiculeService vehiculeService;
    @Mock
    private VehiculeRepository vehicules;
    @Mock
    private ReservationRepository reservations;
    @Mock
    private MaintenanceRepository maintenances;

    private final Agence agence = Donnees.agence(ID_AGENCE);
    private final Client client = Donnees.client(1L, "alice@example.com", "P1");
    private final Vehicule clio = Donnees.vehicule(1L, "AA-111-AA", agence, StatutVehicule.DISPONIBLE);
    private final Vehicule autre = Donnees.vehicule(2L, "BB-222-BB", agence, StatutVehicule.DISPONIBLE);

    private Vehicule garePlace(Long id, int place) {
        Vehicule vehicule = Donnees.vehicule(id, "PL-" + id, agence, StatutVehicule.DISPONIBLE);
        vehicule.setPlaceParking(place);
        return vehicule;
    }

    private List<Place> plan(Long surligne, Vehicule... garees) {
        when(vehiculeService.listerParAgence(ID_AGENCE)).thenReturn(List.of(garees));
        return new ParkingService(vehiculeService).plan(ID_AGENCE, surligne);
    }

    private List<Ligne> planning(List<Vehicule> flotte, List<Reservation> reservees, List<Maintenance> travaux) {
        when(vehicules.findAll(any(Sort.class))).thenReturn(flotte);
        when(reservations.trouverSurPeriode(FEVRIER.atDay(1), FEVRIER.atEndOfMonth())).thenReturn(reservees);
        when(maintenances.trouverSurPeriode(FEVRIER.atDay(1), FEVRIER.atEndOfMonth())).thenReturn(travaux);
        return new PlanningService(vehicules, reservations, maintenances).construire(FEVRIER);
    }

    private static Case jour(Ligne ligne, int jour) {
        return ligne.cases().get(jour - 1);
    }

    @Test
    void unParkingVideCompteDouzePlacesLibres() {
        List<Place> places = plan(null);

        assertThat(places).hasSize(12);
        assertThat(places).extracting(Place::numero).startsWith(1, 2).endsWith(12);
        assertThat(places).allMatch(place -> place.vehicule() == null && !place.surlignee());
    }

    @ParameterizedTest
    @CsvSource({"12,12", "13,18", "18,18", "19,24"})
    void leParkingSEtendParRangeesEntieresDeSixPlaces(int derniere, int attendu) {
        assertThat(plan(null, garePlace(1L, derniere))).hasSize(attendu);
    }

    @Test
    void placeLesVehiculesEtSurligneCeluiDuClient() {
        Vehicule premier = garePlace(1L, 3);
        Vehicule second = garePlace(2L, 7);

        List<Place> places = plan(2L, premier, second);

        assertThat(places.get(2).vehicule()).isSameAs(premier);
        assertThat(places.get(2).surlignee()).isFalse();
        assertThat(places.get(6).vehicule()).isSameAs(second);
        assertThat(places.get(6).surlignee()).isTrue();
    }

    @Test
    void ignoreUnVehiculeSansPlace() {
        assertThat(plan(null, clio)).allMatch(place -> place.vehicule() == null);
    }

    @Test
    void lePlanningComporteUneCaseParJourEtUneLigneParVehicule() {
        List<Ligne> lignes = planning(List.of(clio, autre), List.of(), List.of());

        assertThat(lignes).hasSize(2);
        assertThat(lignes).allSatisfy(ligne -> assertThat(ligne.cases()).hasSize(28));
        assertThat(jour(lignes.get(0), 1)).isEqualTo(new Case("libre", "", "Libre", null));
    }

    @Test
    void uneReservationOccupeSesJoursBornesIncluses() {
        Reservation reservation = Donnees.reservation(10L, client, clio,
                LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 12), StatutReservation.CONFIRMEE);

        Ligne ligne = planning(List.of(clio), List.of(reservation), List.of()).get(0);

        assertThat(jour(ligne, 9).type()).isEqualTo("libre");
        assertThat(jour(ligne, 10).type()).isEqualTo("confirmee");
        assertThat(jour(ligne, 10).code()).isEqualTo("C");
        assertThat(jour(ligne, 10).lien()).isEqualTo("/reservations/10");
        assertThat(jour(ligne, 12).type()).isEqualTo("confirmee");
        assertThat(jour(ligne, 13).type()).isEqualTo("libre");
    }

    @Test
    void uneReservationNeConcerneQueSonVehicule() {
        Reservation reservation = Donnees.reservation(10L, client, clio,
                LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 12), StatutReservation.CONFIRMEE);

        Ligne ligneAutre = planning(List.of(clio, autre), List.of(reservation), List.of()).get(1);

        assertThat(jour(ligneAutre, 10).type()).isEqualTo("libre");
    }

    @Test
    void laMaintenancePasseAvantLaReservationLeMemeJour() {
        Reservation reservation = Donnees.reservation(10L, client, clio,
                LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 12), StatutReservation.CONFIRMEE);
        Maintenance maintenance = new Maintenance(5L, LocalDate.of(2026, 2, 11), LocalDate.of(2026, 2, 11), "Freins", clio);

        Ligne ligne = planning(List.of(clio), List.of(reservation), List.of(maintenance)).get(0);

        assertThat(jour(ligne, 10).type()).isEqualTo("confirmee");
        assertThat(jour(ligne, 11)).isEqualTo(new Case("maintenance", "M", "Maintenance : Freins", "/maintenances/5"));
        assertThat(jour(ligne, 12).type()).isEqualTo("confirmee");
    }

    @Test
    void uneMaintenanceOuverteOccupeLaFinDuMois() {
        Maintenance ouverte = new Maintenance(5L, LocalDate.of(2026, 2, 20), null, "Moteur", clio);

        Ligne ligne = planning(List.of(clio), List.of(), List.of(ouverte)).get(0);

        assertThat(jour(ligne, 19).type()).isEqualTo("libre");
        assertThat(jour(ligne, 28).type()).isEqualTo("maintenance");
    }
}
