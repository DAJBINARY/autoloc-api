package fr.ece.autoloc.integration;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.support.Donnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The JPQL queries and the database constraints, on a real database. Each test is rolled back.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:repositories;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RepositoriesIntegrationTest {

    private static final LocalDate JOUR_1 = LocalDate.of(2026, 6, 1);
    private static final LocalDate JOUR_5 = LocalDate.of(2026, 6, 5);
    private static final LocalDate JOUR_10 = LocalDate.of(2026, 6, 10);
    private static final Pageable PREMIERE_PAGE = PageRequest.of(0, 15);

    @Autowired
    private AgenceRepository agences;
    @Autowired
    private ClientRepository clients;
    @Autowired
    private VehiculeRepository vehicules;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private MaintenanceRepository maintenances;
    @Autowired
    private ContratRepository contrats;
    @Autowired
    private PaiementRepository paiements;

    private Agence agence;
    private Client alice;
    private Vehicule clio;

    @BeforeEach
    void preparer() {
        agence = agences.save(Donnees.agence(null));
        alice = clients.save(Donnees.client(null, "alice@example.com", "P1"));
        clio = vehicules.save(Donnees.vehicule(null, "AA-111-AA", agence, StatutVehicule.DISPONIBLE));
    }

    private Reservation reservation(Client client, Vehicule vehicule, LocalDate debut, LocalDate fin, StatutReservation statut) {
        return reservations.save(Donnees.reservation(null, client, vehicule, debut, fin, statut));
    }

    private Reservation reservationDeClio(StatutReservation statut) {
        return reservation(alice, clio, JOUR_5, JOUR_10, statut);
    }

    private Contrat contrat(Reservation reservation) {
        return contrats.save(new Contrat(null, JOUR_1, new BigDecimal("500.00"), true, reservation, null));
    }

    private Paiement paiement(Contrat contrat, String montant, ModePaiement mode) {
        return paiements.save(new Paiement(null, new BigDecimal(montant), JOUR_1, mode, contrat));
    }

    @ParameterizedTest(name = "du {0} au {1} : {2} chevauchement(s)")
    @CsvSource({
            "2026-06-01,2026-06-04,0",
            "2026-06-01,2026-06-05,1",
            "2026-06-07,2026-06-08,1",
            "2026-06-01,2026-06-20,1",
            "2026-06-10,2026-06-12,1",
            "2026-06-11,2026-06-12,0"
    })
    void detecteLesChevauchementsBornesIncluses(LocalDate debut, LocalDate fin, long attendu) {
        reservationDeClio(StatutReservation.CONFIRMEE);

        assertThat(reservations.compterChevauchements(clio.getIdVehicule(), debut, fin, -1L)).isEqualTo(attendu);
    }

    @Test
    void uneReservationAnnuleeNeBloquePasLesDates() {
        reservationDeClio(StatutReservation.ANNULEE);

        assertThat(reservations.compterChevauchements(clio.getIdVehicule(), JOUR_5, JOUR_10, -1L)).isZero();
    }

    @Test
    void uneReservationNeSeChevauchePasElleMeme() {
        Reservation reservation = reservationDeClio(StatutReservation.CONFIRMEE);

        assertThat(reservations.compterChevauchements(clio.getIdVehicule(), JOUR_5, JOUR_10, reservation.getIdReservation())).isZero();
    }

    @Test
    void lesChevauchementsSontPropresAChaqueVehicule() {
        Vehicule autre = vehicules.save(Donnees.vehicule(null, "BB-222-BB", agence, StatutVehicule.DISPONIBLE));
        reservationDeClio(StatutReservation.CONFIRMEE);

        assertThat(reservations.compterChevauchements(autre.getIdVehicule(), JOUR_5, JOUR_10, -1L)).isZero();
    }

    @Test
    void neTrouveUneReservationQuAvecLEmailDeSonClient() {
        Reservation reservation = reservationDeClio(StatutReservation.CONFIRMEE);
        Long id = reservation.getIdReservation();

        assertThat(reservations.findByIdReservationAndClient_Email(id, "alice@example.com")).contains(reservation);
        assertThat(reservations.findByIdReservationAndClient_Email(id, "pirate@example.com")).isEmpty();
    }

    @Test
    void listeLesReservationsDUnClientDeLaPlusRecenteALaPlusAncienne() {
        Reservation ancienne = reservation(alice, clio, JOUR_1, JOUR_1, StatutReservation.TERMINEE);
        Reservation recente = reservationDeClio(StatutReservation.CONFIRMEE);

        assertThat(reservations.findByClient_EmailOrderByDateDebutDesc("alice@example.com"))
                .containsExactly(recente, ancienne);
    }

    @Test
    void lePlanningIgnoreLesReservationsAnnuleesEtHorsPeriode() {
        Reservation visible = reservationDeClio(StatutReservation.CONFIRMEE);
        reservationDeClio(StatutReservation.ANNULEE);
        reservation(alice, clio, JOUR_1.plusMonths(2), JOUR_1.plusMonths(2), StatutReservation.CONFIRMEE);

        assertThat(reservations.trouverSurPeriode(JOUR_1, JOUR_1.plusDays(29))).containsExactly(visible);
    }

    @Test
    void compteLesLocationsEnCours() {
        Reservation enCours = reservationDeClio(StatutReservation.CONFIRMEE);
        enCours.marquerDepart(JOUR_5);
        Reservation rendue = reservation(alice, clio, JOUR_1, JOUR_1, StatutReservation.TERMINEE);
        rendue.marquerDepart(JOUR_1);
        rendue.marquerRetour(JOUR_1);
        reservation(alice, clio, JOUR_10, JOUR_10, StatutReservation.EN_ATTENTE);
        reservations.flush();

        assertThat(reservations.countByDateDepartIsNotNullAndDateRetourIsNull()).isEqualTo(1);
    }

    @Test
    void neProposeAuContratQueLesReservationsSansContratEtLaReservationCourante() {
        Reservation libre = reservation(alice, clio, JOUR_1, JOUR_1, StatutReservation.EN_ATTENTE);
        Reservation avecContrat = reservationDeClio(StatutReservation.CONFIRMEE);
        contrat(avecContrat);
        reservation(alice, clio, JOUR_10, JOUR_10, StatutReservation.ANNULEE);

        assertThat(reservations.trouverSansContrat(-1L)).containsExactly(libre);
        assertThat(reservations.trouverSansContrat(avecContrat.getIdReservation()))
                .containsExactlyInAnyOrder(libre, avecContrat);
    }

    @Test
    void classeLesVehiculesLesPlusReservesSansCompterLesAnnulations() {
        Vehicule peuReserve = vehicules.save(Donnees.vehicule(null, "BB-222-BB", agence, StatutVehicule.DISPONIBLE));
        reservation(alice, clio, JOUR_1, JOUR_1, StatutReservation.CONFIRMEE);
        reservation(alice, clio, JOUR_5, JOUR_5, StatutReservation.TERMINEE);
        reservation(alice, peuReserve, JOUR_1, JOUR_1, StatutReservation.CONFIRMEE);
        reservation(alice, peuReserve, JOUR_5, JOUR_5, StatutReservation.ANNULEE);
        reservation(alice, peuReserve, JOUR_10, JOUR_10, StatutReservation.ANNULEE);

        List<Object[]> classement = reservations.vehiculesLesPlusReserves(PageRequest.of(0, 5));

        assertThat(classement).hasSize(2);
        assertThat(classement.get(0)).containsExactly(clio, 2L);
        assertThat(classement.get(1)).containsExactly(peuReserve, 1L);
    }

    @Test
    void rechercheUneReservationSansTenirCompteDeLaCasse() {
        reservationDeClio(StatutReservation.CONFIRMEE);

        assertThat(reservations.rechercher("DUR", PREMIERE_PAGE)).hasSize(1);
        assertThat(reservations.rechercher("aa-111", PREMIERE_PAGE)).hasSize(1);
        assertThat(reservations.rechercher("introuvable", PREMIERE_PAGE)).isEmpty();
    }

    @Test
    void unVehiculeReserveNEstPlusDisponibleSurCesDates() {
        reservationDeClio(StatutReservation.CONFIRMEE);

        assertThat(vehicules.trouverDisponibles(JOUR_5, JOUR_5)).isEmpty();
        assertThat(vehicules.trouverDisponibles(JOUR_1, JOUR_1)).containsExactly(clio);
    }

    @Test
    void uneAnnulationRendLeVehiculeDisponible() {
        reservationDeClio(StatutReservation.ANNULEE);

        assertThat(vehicules.trouverDisponibles(JOUR_5, JOUR_10)).containsExactly(clio);
    }

    @Test
    void uneMaintenanceTermineeNeBloqueQueSaPeriode() {
        maintenances.save(new Maintenance(null, JOUR_1, JOUR_5, "Freins", clio));

        assertThat(vehicules.trouverDisponibles(JOUR_5, JOUR_5)).isEmpty();
        assertThat(vehicules.trouverDisponibles(JOUR_5.plusDays(1), JOUR_10)).containsExactly(clio);
        assertThat(maintenances.existeSurPeriode(clio.getIdVehicule(), JOUR_5, JOUR_5)).isTrue();
        assertThat(maintenances.existeSurPeriode(clio.getIdVehicule(), JOUR_10, JOUR_10)).isFalse();
    }

    @Test
    void uneMaintenanceOuverteBloqueToutesLesDatesSuivantes() {
        maintenances.save(new Maintenance(null, JOUR_5, null, "Moteur", clio));

        assertThat(vehicules.trouverDisponibles(JOUR_10.plusYears(1), JOUR_10.plusYears(1))).isEmpty();
        assertThat(vehicules.trouverDisponibles(JOUR_1, JOUR_1)).containsExactly(clio);
        assertThat(maintenances.existsByVehicule_IdVehiculeAndDateFinIsNull(clio.getIdVehicule())).isTrue();
    }

    @Test
    void lesVehiculesDisponiblesSontTriesParMarqueEtModele() {
        Vehicule audi = Donnees.vehicule(null, "BB-222-BB", agence, StatutVehicule.DISPONIBLE);
        audi.setMarque("Audi");
        audi.setModele("A3");
        vehicules.save(audi);

        assertThat(vehicules.trouverDisponibles(JOUR_1, JOUR_1)).containsExactly(audi, clio);
    }

    @Test
    void uneImmatriculationEstUnique() {
        Vehicule doublon = Donnees.vehicule(null, clio.getImmatriculation(), agence, StatutVehicule.DISPONIBLE);

        assertThatThrownBy(() -> vehicules.saveAndFlush(doublon)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unePlaceDeParkingEstUniqueDansUneAgenceMaisPasEntreAgences() {
        clio.setPlaceParking(7);
        vehicules.saveAndFlush(clio);

        Agence lyon = agences.save(new Agence(null, "AutoLoc Lyon", "Lyon", "2 rue", "0400000000"));
        Vehicule ailleurs = Donnees.vehicule(null, "BB-222-BB", lyon, StatutVehicule.DISPONIBLE);
        ailleurs.setPlaceParking(7);
        vehicules.saveAndFlush(ailleurs);

        Vehicule doublon = Donnees.vehicule(null, "CC-333-CC", agence, StatutVehicule.DISPONIBLE);
        doublon.setPlaceParking(7);
        assertThatThrownBy(() -> vehicules.saveAndFlush(doublon)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unEmailClientEstUnique() {
        Client memeEmail = Donnees.client(null, alice.getEmail(), "P2");

        assertThatThrownBy(() -> clients.saveAndFlush(memeEmail)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unPermisClientEstUnique() {
        Client memePermis = Donnees.client(null, "bob@example.com", alice.getNumPermis());

        assertThatThrownBy(() -> clients.saveAndFlush(memePermis)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uneReservationNeCompteQuUnSeulContrat() {
        Reservation reservation = reservationDeClio(StatutReservation.CONFIRMEE);
        contrats.saveAndFlush(new Contrat(null, JOUR_1, BigDecimal.TEN, true, reservation, null));

        Contrat doublon = new Contrat(null, JOUR_1, BigDecimal.TEN, true, reservation, null);

        assertThatThrownBy(() -> contrats.saveAndFlush(doublon)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lesTotauxSontNulsSansPaiement() {
        assertThat(paiements.totalEncaisse()).isEqualByComparingTo("0");
        assertThat(paiements.totalPourContrat(1L, -1L)).isEqualByComparingTo("0");
        assertThat(paiements.totalParMode()).isEmpty();
    }

    @Test
    void cumuleLesPaiementsParContratEtParMode() {
        Contrat contrat = contrat(reservationDeClio(StatutReservation.CONFIRMEE));
        Paiement acompte = paiement(contrat, "100.00", ModePaiement.CARTE);
        paiement(contrat, "50.50", ModePaiement.CARTE);
        paiement(contrat, "25.00", ModePaiement.ESPECES);

        assertThat(paiements.totalEncaisse()).isEqualByComparingTo("175.50");
        assertThat(paiements.totalPourContrat(contrat.getIdContrat(), -1L)).isEqualByComparingTo("175.50");
        assertThat(paiements.totalPourContrat(contrat.getIdContrat(), acompte.getIdPaiement())).isEqualByComparingTo("75.50");
        assertThat(paiements.totalParMode())
                .hasSize(2)
                .anySatisfy(ligne -> {
                    assertThat(ligne[0]).isEqualTo(ModePaiement.CARTE);
                    assertThat((BigDecimal) ligne[1]).isEqualByComparingTo("150.50");
                });
    }

    @Test
    void rechercheUnClientParSonNomDansLesDeuxSens() {
        assertThat(clients.rechercher("alice durand", PREMIERE_PAGE)).hasSize(1);
        assertThat(clients.rechercher("durand alice", PREMIERE_PAGE)).hasSize(1);
        assertThat(clients.rechercher("bob", PREMIERE_PAGE)).isEmpty();
    }
}
