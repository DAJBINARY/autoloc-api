package fr.ece.autoloc.integration;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.CatalogueService;
import fr.ece.autoloc.service.CompteService;
import fr.ece.autoloc.service.MaintenanceService;
import fr.ece.autoloc.service.PaiementService;
import fr.ece.autoloc.service.PlanningService;
import fr.ece.autoloc.service.RegleMetierException;
import fr.ece.autoloc.service.ReservationClientService;
import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.service.StatistiquesService;
import fr.ece.autoloc.service.VehiculeService;
import fr.ece.autoloc.support.Donnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Services, transactions and database working together: changes must really be committed, and the rules
 * must hold when several requests run at once. These tests are deliberately not wrapped in a transaction.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:services;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ServicesIntegrationTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final String MOT_DE_PASSE = "motdepasse1";
    private static final LocalDate DEMAIN = LocalDate.now().plusDays(1);
    private static final LocalDate DANS_3_JOURS = DEMAIN.plusDays(2);

    @Autowired
    private ReservationClientService reservationsClient;
    @Autowired
    private ReservationService reservationsAgence;
    @Autowired
    private MaintenanceService maintenanceService;
    @Autowired
    private CatalogueService catalogue;
    @Autowired
    private CompteService comptes;
    @Autowired
    private PaiementService paiementService;
    @Autowired
    private PlanningService planning;
    @Autowired
    private StatistiquesService statistiques;
    @Autowired
    private VehiculeService vehiculeService;
    @Autowired
    private PaiementRepository paiements;
    @Autowired
    private ContratRepository contrats;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private MaintenanceRepository maintenances;
    @Autowired
    private VehiculeRepository vehicules;
    @Autowired
    private EmployeRepository employes;
    @Autowired
    private ClientRepository clients;
    @Autowired
    private AgenceRepository agences;
    @Autowired
    private PasswordEncoder encodeur;

    private Agence agence;
    private Vehicule clio;

    @BeforeEach
    void preparer() {
        paiements.deleteAll();
        contrats.deleteAll();
        reservations.deleteAll();
        maintenances.deleteAll();
        vehicules.deleteAll();
        employes.deleteAll();
        clients.deleteAll();
        agences.deleteAll();
        agence = agences.save(Donnees.agence(null));
        clio = vehicules.save(Donnees.vehicule(null, "AA-111-AA", agence, StatutVehicule.DISPONIBLE));
        clients.save(Donnees.client(null, ALICE, "P1"));
        clients.save(Donnees.client(null, BOB, "P2"));
    }

    private Long reserver(String email) {
        return reservationsClient.reserver(email, clio.getIdVehicule(), DEMAIN, DANS_3_JOURS).getIdReservation();
    }

    private StatutReservation statutReservation(Long id) {
        return reservations.findById(id).orElseThrow().getStatut();
    }

    private StatutVehicule statutClio() {
        return vehicules.findById(clio.getIdVehicule()).orElseThrow().getStatut();
    }

    private Long maintenanceOuverte() {
        return maintenanceService.enregistrer(new Maintenance(null, LocalDate.now(), null, "Freins", clio)).getIdMaintenance();
    }

    @Test
    void lePaiementEnLigneEnregistreContratPaiementEtConfirmation() {
        Long id = reserver(ALICE);
        assertThat(statutReservation(id)).isEqualTo(StatutReservation.EN_ATTENTE);

        reservationsClient.payerEnLigne(id, ALICE);

        assertThat(statutReservation(id)).isEqualTo(StatutReservation.CONFIRMEE);
        Contrat contrat = contrats.findByReservation_IdReservation(id).orElseThrow();
        assertThat(contrat.getMontantTotal()).isEqualByComparingTo("120.00");
        assertThat(contrat.getEmploye()).isNull();
        List<Paiement> regles = paiementService.listerParContrat(contrat.getIdContrat());
        assertThat(regles).hasSize(1);
        assertThat(regles.get(0).getModePaiement()).isEqualTo(ModePaiement.CARTE);
        assertThat(paiementService.totalPourContrat(contrat.getIdContrat())).isEqualByComparingTo("120.00");
        assertThat(paiementService.totalEncaisse()).isEqualByComparingTo("120.00");
    }

    @Test
    void unDeuxiemePaiementNEncaissePasDeuxFois() {
        Long id = reserver(ALICE);
        reservationsClient.payerEnLigne(id, ALICE);

        assertThatThrownBy(() -> reservationsClient.payerEnLigne(id, ALICE)).isInstanceOf(RegleMetierException.class);

        assertThat(paiements.count()).isEqualTo(1);
        assertThat(contrats.count()).isEqualTo(1);
    }

    @Test
    void unClientNeTouchePasALaReservationDUnAutre() {
        Long id = reserver(ALICE);

        assertThatThrownBy(() -> reservationsClient.payerEnLigne(id, BOB)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> reservationsClient.annuler(id, BOB)).isInstanceOf(NoSuchElementException.class);

        assertThat(statutReservation(id)).isEqualTo(StatutReservation.EN_ATTENTE);
        assertThat(paiements.count()).isZero();
    }

    @Test
    void unVehiculeReserveNeSeReservePasDeuxFoisEtSeLibereALAnnulation() {
        Long id = reserver(ALICE);

        assertThatThrownBy(() -> reserver(BOB))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("vient d'être réservé");
        assertThat(catalogue.chercher(null, DEMAIN, DANS_3_JOURS, null, null)).isEmpty();

        reservationsClient.annuler(id, ALICE);

        assertThat(catalogue.chercher(null, DEMAIN, DANS_3_JOURS, null, null))
                .extracting(Vehicule::getIdVehicule).containsExactly(clio.getIdVehicule());
        assertThat(reserver(BOB)).isNotNull();
    }

    @Test
    void desReservationsSimultaneesNeLaissentPasserQuUnSeulClient() throws Exception {
        int nombre = 8;
        ExecutorService threads = Executors.newFixedThreadPool(nombre);
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<Boolean>> resultats = new ArrayList<>();
        Callable<Boolean> tentative = () -> {
            depart.await();
            try {
                reserver(ALICE);
                return true;
            } catch (RegleMetierException e) {
                return false;
            }
        };
        try {
            for (int i = 0; i < nombre; i++) {
                resultats.add(threads.submit(tentative));
            }
            depart.countDown();
            int reussites = 0;
            for (Future<Boolean> resultat : resultats) {
                reussites += resultat.get() ? 1 : 0;
            }
            assertThat(reussites).isEqualTo(1);
        } finally {
            threads.shutdownNow();
        }
        assertThat(reservations.count()).isEqualTo(1);
    }

    @Test
    void leCycleDepartRetourEstBienEnregistreEnBase() {
        Long id = reserver(ALICE);
        reservationsClient.payerEnLigne(id, ALICE);

        reservationsAgence.enregistrerDepart(id);

        assertThat(statutClio()).isEqualTo(StatutVehicule.LOUE);
        assertThat(reservations.findById(id).orElseThrow().isEnCours()).isTrue();
        assertThat(reservationsAgence.compterLocationsEnCours()).isEqualTo(1);
        assertThat(catalogue.chercher(null, null, null, null, null)).isEmpty();

        reservationsAgence.enregistrerRetour(id);

        assertThat(statutClio()).isEqualTo(StatutVehicule.DISPONIBLE);
        assertThat(statutReservation(id)).isEqualTo(StatutReservation.TERMINEE);
        assertThat(reservationsAgence.compterLocationsEnCours()).isZero();
        assertThat(catalogue.chercher(null, null, null, null, null)).hasSize(1);
    }

    @Test
    void uneRegleRefuseeNeChangeRienEnBase() {
        Long id = reserver(ALICE);

        assertThatThrownBy(() -> reservationsAgence.enregistrerDepart(id)).isInstanceOf(RegleMetierException.class);

        assertThat(statutClio()).isEqualTo(StatutVehicule.DISPONIBLE);
        assertThat(reservations.findById(id).orElseThrow().getDateDepart()).isNull();
    }

    @Test
    void laMaintenanceImmobiliseLeVehiculeJusquASaCloture() {
        Long id = maintenanceOuverte();

        assertThat(statutClio()).isEqualTo(StatutVehicule.MAINTENANCE);
        assertThat(catalogue.chercher(null, DEMAIN, DANS_3_JOURS, null, null)).isEmpty();
        assertThatThrownBy(() -> reserver(ALICE)).isInstanceOf(RegleMetierException.class);

        maintenanceService.cloturer(id);

        assertThat(statutClio()).isEqualTo(StatutVehicule.DISPONIBLE);
        assertThat(maintenances.findById(id).orElseThrow().getDateFin()).isEqualTo(LocalDate.now());
        assertThat(reserver(ALICE)).isNotNull();
    }

    @Test
    void unVehiculeLoueNePasseEnMaintenanceQuASonRetour() {
        Long id = reserver(ALICE);
        reservationsClient.payerEnLigne(id, ALICE);
        reservationsAgence.enregistrerDepart(id);

        maintenanceOuverte();
        assertThat(statutClio()).isEqualTo(StatutVehicule.LOUE);

        reservationsAgence.enregistrerRetour(id);
        assertThat(statutClio()).isEqualTo(StatutVehicule.MAINTENANCE);
    }

    @Test
    void laSuppressionDUneMaintenanceRendLeVehiculeDisponible() {
        Long id = maintenanceOuverte();

        maintenanceService.supprimer(id);

        assertThat(maintenances.existsById(id)).isFalse();
        assertThat(statutClio()).isEqualTo(StatutVehicule.DISPONIBLE);
    }

    @Test
    void laBaseRefuseDeSupprimerUnVehiculeEncoreReserve() {
        reserver(ALICE);

        assertThatThrownBy(() -> vehiculeService.supprimer(clio.getIdVehicule()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(vehicules.existsById(clio.getIdVehicule())).isTrue();
    }

    @Test
    void uneInscriptionPermetDeSeConnecterAvecUnMotDePasseHache() {
        comptes.inscrireClient("Martin", "Carole", " Carole@Example.com ", "0600000000", "P3", MOT_DE_PASSE);

        Client carole = clients.findByEmail("carole@example.com").orElseThrow();
        assertThat(carole.getMotDePasse()).isNotEqualTo(MOT_DE_PASSE);
        assertThat(encodeur.matches(MOT_DE_PASSE, carole.getMotDePasse())).isTrue();

        UserDetails compte = comptes.loadUserByUsername("CAROLE@example.com");
        assertThat(compte.getUsername()).isEqualTo("carole@example.com");
        assertThat(compte.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_CLIENT");
    }

    @Test
    void lePremierCompteCreeSonAgenceEtNePeutEtreCreeQuUneFois() {
        long agencesAvant = agences.count();
        Employe manager = new Employe();
        manager.setNom("Lovelace");
        manager.setPrenom("Ada");
        manager.setIdentifiant("ada");
        assertThat(comptes.compteExiste()).isFalse();

        comptes.creerPremierCompte(manager, MOT_DE_PASSE, new Agence(null, "Lyon", "Lyon", "2 rue", "0400000000"));

        assertThat(comptes.compteExiste()).isTrue();
        assertThat(agences.count()).isEqualTo(agencesAvant + 1);
        assertThat(employes.findByIdentifiant("ada").orElseThrow().getRole()).isEqualTo(RoleEmploye.MANAGER);
        assertThat(comptes.changerMotDePasse("ada", "faux", "nouveau1234")).isFalse();
        assertThat(comptes.changerMotDePasse("ada", MOT_DE_PASSE, "nouveau1234")).isTrue();
        assertThat(encodeur.matches("nouveau1234", employes.findByIdentifiant("ada").orElseThrow().getMotDePasse())).isTrue();

        assertThatThrownBy(() -> comptes.creerPremierCompte(new Employe(), MOT_DE_PASSE, agence))
                .isInstanceOf(RegleMetierException.class);
    }

    @Test
    void lePlanningEtLesStatistiquesLisentLesDonneesLiees() {
        Long id = reserver(ALICE);
        reservationsClient.payerEnLigne(id, ALICE);

        YearMonth mois = YearMonth.from(DEMAIN);
        PlanningService.Case jour = planning.construire(mois).get(0).cases().get(DEMAIN.getDayOfMonth() - 1);
        assertThat(jour.type()).isEqualTo("confirmee");
        assertThat(jour.titre()).contains("Alice Durand");

        assertThat(statistiques.encaisseParMode()).singleElement()
                .satisfies(total -> assertThat(total.libelle()).isEqualTo("Carte"));
        assertThat(statistiques.reservationsParStatut()).containsExactly(new StatistiquesService.Nombre("Confirmée", 1));
        assertThat(statistiques.vehiculesLesPlusReserves(5))
                .containsExactly(new StatistiquesService.Nombre("Renault Clio (AA-111-AA)", 1));
        assertThat(statistiques.encaisseParMois(1).get(0).pourcentage()).isEqualTo(100);
    }
}
