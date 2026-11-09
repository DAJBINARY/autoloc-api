package fr.ece.autoloc.fonctionnel;

import fr.ece.autoloc.domain.Reservation;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.ece.autoloc.fonctionnel.Navigateur.redirection;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application seen from outside: a real server, real HTTP requests, real sessions and CSRF tokens,
 * and each person with their own browser. Everything is created through the screens; the repositories
 * only read the result.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:fonctionnel;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ParcoursLocationFonctionnelTest {

    private static final String IDENTIFIANT_MANAGER = "ada";
    private static final String MOT_DE_PASSE = "motdepasse1";
    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final String MODELE = "Roadster Fonctionnel";
    private static final String PAGE_MANAGER = "/vehicules/nouveau";
    private static final String DEBUT = LocalDate.now().plusDays(1).toString();
    private static final String FIN = LocalDate.now().plusDays(3).toString();
    private static final Pattern ID_RESERVATION = Pattern.compile("/espace/reservations/(\\d+)/paiement");

    @Value("${local.server.port}")
    private int port;
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

    private Navigateur manager;
    private Navigateur visiteur;
    private Long idVehicule;

    /** Every scenario starts on an empty application: the first manager creates the agency and a vehicle. */
    @BeforeEach
    void ouvrirLAgence() throws Exception {
        paiements.deleteAll();
        contrats.deleteAll();
        reservations.deleteAll();
        maintenances.deleteAll();
        vehicules.deleteAll();
        employes.deleteAll();
        clients.deleteAll();
        agences.deleteAll();

        manager = new Navigateur(port);
        visiteur = new Navigateur(port);

        HttpResponse<String> compte = manager.soumettre("/premiere-connexion", "/premiere-connexion",
                "prenom", "Ada", "nom", "Lovelace", "identifiant", IDENTIFIANT_MANAGER, "motDePasse", MOT_DE_PASSE,
                "nomAgence", "AutoLoc Paris", "villeAgence", "Paris",
                "adresseAgence", "1 rue de Rivoli", "telephoneAgence", "0100000000");
        assertThat(redirection(compte)).isEqualTo("/connexion");
        assertThat(redirection(manager.seConnecter(IDENTIFIANT_MANAGER, MOT_DE_PASSE))).isEqualTo("/tableau-de-bord");

        HttpResponse<String> vehicule = manager.soumettre(PAGE_MANAGER, "/vehicules",
                "immatriculation", "FN-123-FN", "marque", "Testla", "modele", MODELE, "categorie", "BERLINE",
                "tarifJournalier", "50.00", "statut", "DISPONIBLE",
                "agence", agences.findAll().get(0).getIdAgence().toString());
        assertThat(redirection(vehicule)).isEqualTo("/vehicules");
        idVehicule = vehicules.findAll().get(0).getIdVehicule();
    }

    private Navigateur inscrire(String email, String numPermis) throws IOException, InterruptedException {
        Navigateur client = new Navigateur(port);
        HttpResponse<String> reponse = client.soumettre("/inscription", "/inscription",
                "prenom", "Alice", "nom", "Durand", "email", email, "telephone", "0600000000", "numPermis", numPermis,
                "motDePasse", MOT_DE_PASSE, "confirmation", MOT_DE_PASSE);
        assertThat(redirection(reponse)).isEqualTo("/espace");
        return client;
    }

    private String pageDeReservation() {
        return "/espace/reservations/nouvelle?vehicule=" + idVehicule + "&debut=" + DEBUT + "&fin=" + FIN;
    }

    private HttpResponse<String> demanderReservation(Navigateur client) throws IOException, InterruptedException {
        return client.soumettre(pageDeReservation(), "/espace/reservations",
                "vehicule", idVehicule.toString(), "debut", DEBUT, "fin", FIN);
    }

    private Long reserver(Navigateur client) throws IOException, InterruptedException {
        Matcher id = ID_RESERVATION.matcher(String.valueOf(redirection(demanderReservation(client))));
        assertThat(id.matches()).as("redirection vers le paiement").isTrue();
        return Long.valueOf(id.group(1));
    }

    private HttpResponse<String> payer(Navigateur client, Long id, String numeroDeCarte) throws IOException, InterruptedException {
        String expiration = YearMonth.now().plusYears(1).format(DateTimeFormatter.ofPattern("MM/yy"));
        String paiement = "/espace/reservations/" + id + "/paiement";
        return client.soumettre(paiement, paiement, "titulaire", "Alice Durand", "numero", numeroDeCarte,
                "expiration", expiration, "cryptogramme", "123");
    }

    private HttpResponse<String> actionManager(String action) throws IOException, InterruptedException {
        return manager.soumettre(PAGE_MANAGER, action);
    }

    private Vehicule vehicule() {
        return vehicules.findById(idVehicule).orElseThrow();
    }

    private Reservation reservation(Long id) {
        return reservations.findById(id).orElseThrow();
    }

    private String catalogue() throws IOException, InterruptedException {
        return visiteur.ouvrir("/catalogue").body();
    }

    @Test
    void uneLocationVaDeLInscriptionAuRetourDuVehicule() throws Exception {
        assertThat(catalogue()).contains(MODELE);

        Navigateur alice = inscrire(ALICE, "P1");
        String espace = alice.ouvrir("/espace").body();
        assertThat(espace).contains("Bonjour Alice").contains("Aucune réservation pour le moment");

        String devis = alice.ouvrir(pageDeReservation()).body();
        assertThat(devis).contains("150,00 €").contains("Continuer vers le paiement");

        Long id = reserver(alice);
        assertThat(reservation(id).getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);

        String urlReservation = "/espace/reservations/" + id;
        HttpResponse<String> carteRefusee = payer(alice, id, "1234 5678 9012 3456");
        assertThat(carteRefusee.statusCode()).isEqualTo(200);
        assertThat(carteRefusee.body()).contains("Numéro de carte invalide.");
        assertThat(paiements.count()).isZero();

        assertThat(redirection(payer(alice, id, "4242 4242 4242 4242"))).isEqualTo(urlReservation + "/confirmation");
        assertThat(alice.ouvrir(urlReservation + "/confirmation").body()).contains("C'est réservé !");
        assertThat(reservation(id).getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(paiements.findAll().get(0).getMontant()).isEqualByComparingTo("150.00");

        assertThat(redirection(actionManager("/reservations/" + id + "/depart"))).isNotNull();
        assertThat(vehicule().getStatut()).isEqualTo(StatutVehicule.LOUE);
        assertThat(reservation(id).isEnCours()).isTrue();
        assertThat(catalogue()).doesNotContain(MODELE);

        assertThat(redirection(actionManager("/reservations/" + id + "/retour"))).isNotNull();
        assertThat(vehicule().getStatut()).isEqualTo(StatutVehicule.DISPONIBLE);
        assertThat(reservation(id).getStatut()).isEqualTo(StatutReservation.TERMINEE);
        assertThat(catalogue()).contains(MODELE);

        assertThat(manager.ouvrir("/statistiques").body()).contains("150,00");
    }

    @Test
    void unDeuxiemeClientNePeutPasReserverLesMemesDatesJusquALAnnulation() throws Exception {
        Navigateur alice = inscrire(ALICE, "P1");
        Navigateur bob = inscrire(BOB, "P2");
        Long id = reserver(alice);

        HttpResponse<String> refus = demanderReservation(bob);
        assertThat(redirection(refus)).startsWith("/espace/reservations/nouvelle");
        assertThat(reservations.count()).isEqualTo(1);

        String urlReservation = "/espace/reservations/" + id;
        assertThat(redirection(alice.soumettre(urlReservation, urlReservation + "/annuler"))).isNotNull();
        assertThat(reservation(id).getStatut()).isEqualTo(StatutReservation.ANNULEE);

        assertThat(reserver(bob)).isNotEqualTo(id);
    }

    @Test
    void uneMaintenanceRetireLeVehiculeDuCatalogueJusquASaCloture() throws Exception {
        HttpResponse<String> ouverture = manager.soumettre(PAGE_MANAGER, "/maintenances",
                "vehicule", idVehicule.toString(), "dateDebut", LocalDate.now().toString(), "description", "Freins");
        assertThat(redirection(ouverture)).isEqualTo("/maintenances");
        assertThat(vehicule().getStatut()).isEqualTo(StatutVehicule.MAINTENANCE);
        assertThat(catalogue()).doesNotContain(MODELE);

        Navigateur alice = inscrire(ALICE, "P1");
        assertThat(redirection(demanderReservation(alice))).startsWith("/espace/reservations/nouvelle");
        assertThat(reservations.count()).isZero();

        Long idMaintenance = maintenances.findAll().get(0).getIdMaintenance();
        actionManager("/maintenances/" + idMaintenance + "/cloturer");

        assertThat(vehicule().getStatut()).isEqualTo(StatutVehicule.DISPONIBLE);
        assertThat(catalogue()).contains(MODELE);
    }

    @Test
    void chacunNAccedeQuASonEspace() throws Exception {
        assertThat(redirection(visiteur.ouvrir("/espace"))).isEqualTo("/connexion");
        assertThat(redirection(visiteur.ouvrir("/vehicules"))).isEqualTo("/connexion");
        assertThat(visiteur.ouvrir("/catalogue").statusCode()).isEqualTo(200);

        Navigateur alice = inscrire(ALICE, "P1");
        Navigateur bob = inscrire(BOB, "P2");
        assertThat(alice.ouvrir("/vehicules").statusCode()).isEqualTo(403);
        assertThat(alice.ouvrir("/statistiques").statusCode()).isEqualTo(403);
        assertThat(manager.ouvrir("/espace").statusCode()).isEqualTo(403);

        Long id = reserver(alice);
        assertThat(alice.ouvrir("/espace/reservations/" + id).statusCode()).isEqualTo(200);
        assertThat(bob.ouvrir("/espace/reservations/" + id).statusCode()).isEqualTo(404);
    }

    @Test
    void laConnexionExigeLeBonMotDePasse() throws Exception {
        inscrire(ALICE, "P1");

        Navigateur autrePoste = new Navigateur(port);
        assertThat(redirection(autrePoste.seConnecter(ALICE, "mauvais-mot-de-passe"))).isEqualTo("/connexion?error");
        assertThat(redirection(autrePoste.ouvrir("/espace"))).isEqualTo("/connexion");

        // Back to the page asked for before signing in; the e-mail is matched whatever its case.
        assertThat(redirection(autrePoste.seConnecter(ALICE.toUpperCase(), MOT_DE_PASSE))).isEqualTo("/espace?continue");
        assertThat(autrePoste.ouvrir("/espace").statusCode()).isEqualTo(200);
    }

    @Test
    void unFormulaireSansJetonCsrfEstRefuse() throws Exception {
        Navigateur alice = inscrire(ALICE, "P1");

        HttpResponse<String> forge = alice.soumettreSansJeton("/espace/reservations",
                "vehicule", idVehicule.toString(), "debut", DEBUT, "fin", FIN);

        assertThat(forge.statusCode()).isEqualTo(403);
        assertThat(reservations.count()).isZero();
    }

    @Test
    void lesMetriquesEtLaSanteSontExposeesPourLaSupervision() throws Exception {
        assertThat(visiteur.ouvrir("/actuator/health").body()).contains("UP");
        assertThat(visiteur.ouvrir("/catalogue").statusCode()).isEqualTo(200);

        HttpResponse<String> metriques = visiteur.ouvrir("/actuator/prometheus");

        assertThat(metriques.statusCode()).isEqualTo(200);
        assertThat(metriques.body()).contains("jvm_memory_used_bytes").contains("hikaricp_connections");
        assertThat(metriques.body()).contains("http_server_requests_seconds_bucket").contains("uri=\"/catalogue\"");
        assertThat(redirection(visiteur.ouvrir("/actuator/env"))).isEqualTo("/connexion");
    }
}
