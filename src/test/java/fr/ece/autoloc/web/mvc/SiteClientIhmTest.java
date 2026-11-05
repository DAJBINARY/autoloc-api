package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Maintenance;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:site;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class SiteClientIhmTest {

    private static final String EMAIL = "alice@example.com";
    private static final String MOT_DE_PASSE = "motdepasse1";
    private static final LocalDate DEMAIN = LocalDate.now().plusDays(1);

    @Autowired
    private MockMvc mockMvc;
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
    private Vehicule libre;
    private Client alice;

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
        agence = agences.save(new Agence(null, "AutoLoc Paris", "Paris", "1 rue de Rivoli", "0100000000"));
        libre = vehicule("AA-111-AA", "Renault", "Clio", StatutVehicule.DISPONIBLE, 7);
        alice = new Client(null, "Durand", "Alice", EMAIL, "0600000000", "P1", LocalDate.now());
        alice.changerMotDePasse(encodeur.encode(MOT_DE_PASSE));
        alice = clients.save(alice);
    }

    @Test
    void afficheLAccueilPublicAvecSonHeaderEtSon3d() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/images/logo.svg")))
                .andExpect(content().string(containsString("Connexion")))
                .andExpect(content().string(containsString("Créer un compte")))
                .andExpect(content().string(containsString("Voir les véhicules")))
                .andExpect(content().string(containsString("data-voiture3d")))
                .andExpect(content().string(containsString("Renault Clio")));
        mockMvc.perform(get("/js/voiture3d.js")).andExpect(status().isOk());
        mockMvc.perform(get("/vendor/three/three.module.min.js")).andExpect(status().isOk());
        mockMvc.perform(get("/css/site.css")).andExpect(status().isOk());
        mockMvc.perform(get("/fonts/dm-sans-latin-wght-normal.woff2")).andExpect(status().isOk());
    }

    @Test
    void leCatalogueNeMontreQueLesVehiculesDisponibles() throws Exception {
        vehicule("BB-222-BB", "Citroen", "Berlingo", StatutVehicule.MAINTENANCE, null);
        vehicule("CC-333-CC", "Dacia", "Duster", StatutVehicule.LOUE, null);

        mockMvc.perform(get("/catalogue"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Renault Clio")))
                .andExpect(content().string(containsString("1 véhicule disponible")))
                .andExpect(content().string(not(containsString("Berlingo"))))
                .andExpect(content().string(not(containsString("Duster"))));
    }

    @Test
    void leCatalogueFiltreParDatesEtDonneLePrixTotal() throws Exception {
        Vehicule reserve = vehicule("DD-444-DD", "Peugeot", "3008", StatutVehicule.DISPONIBLE, null);
        reservations.save(new Reservation(null, DEMAIN, DEMAIN.plusDays(2), StatutReservation.CONFIRMEE, alice, reserve));
        Vehicule loue = vehicule("EE-555-EE", "Dacia", "Duster", StatutVehicule.LOUE, null);
        Vehicule enTravaux = vehicule("FF-666-FF", "Citroen", "Berlingo", StatutVehicule.DISPONIBLE, null);
        maintenances.save(new Maintenance(null, DEMAIN, null, "Freins", enTravaux));

        mockMvc.perform(get("/catalogue").param("debut", DEMAIN.toString()).param("fin", DEMAIN.plusDays(1).toString()))
                .andExpect(content().string(containsString("Renault Clio")))
                .andExpect(content().string(containsString("Dacia Duster")))
                .andExpect(content().string(containsString("80,00 €")))
                .andExpect(content().string(containsString("pour 2 jours")))
                .andExpect(content().string(not(containsString("Peugeot 3008"))))
                .andExpect(content().string(not(containsString("Citroen Berlingo"))));

        assertThat(loue.getStatut()).isEqualTo(StatutVehicule.LOUE);
        mockMvc.perform(get("/catalogue").param("q", "clio")).andExpect(content().string(containsString("Renault Clio")));
        mockMvc.perform(get("/catalogue").param("q", "introuvable")).andExpect(content().string(containsString("Aucun véhicule ne correspond")));
        mockMvc.perform(get("/catalogue").param("categorie", "UTILITAIRE")).andExpect(content().string(containsString("Aucun véhicule ne correspond")));
        mockMvc.perform(get("/catalogue").param("debut", LocalDate.now().minusDays(3).toString()).param("fin", DEMAIN.toString()))
                .andExpect(content().string(containsString("ne peut pas être passée")));
        mockMvc.perform(get("/catalogue").param("debut", DEMAIN.toString()))
                .andExpect(content().string(containsString("Indiquez la date de début et la date de fin.")));
        mockMvc.perform(get("/catalogue").param("page", "99").param("debut", "").param("fin", "").param("agence", ""))
                .andExpect(status().isOk());
    }

    @Test
    void laFicheVehiculeEstPubliqueEtVerifieLesDates() throws Exception {
        String url = "/catalogue/" + libre.getIdVehicule();

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-categorie=\"CITADINE\"")))
                .andExpect(content().string(not(containsString("Réserver ce véhicule"))));
        mockMvc.perform(get(url).param("debut", DEMAIN.toString()).param("fin", DEMAIN.plusDays(2).toString()))
                .andExpect(content().string(containsString("120,00 €")))
                .andExpect(content().string(containsString("Réserver ce véhicule")));

        reservations.save(new Reservation(null, DEMAIN, DEMAIN, StatutReservation.CONFIRMEE, alice, libre));
        mockMvc.perform(get(url).param("debut", DEMAIN.toString()).param("fin", DEMAIN.plusDays(2).toString()))
                .andExpect(content().string(containsString("pas libre sur ces dates")));
        mockMvc.perform(get("/catalogue/999999")).andExpect(status().isNotFound());
    }

    @Test
    void inscritUnClientPuisLeConnecteAutomatiquement() throws Exception {
        mockMvc.perform(get("/inscription")).andExpect(status().isOk());

        mockMvc.perform(inscription("Nouveau@Example.com", "P2"))
                .andExpect(redirectedUrl("/espace"))
                .andExpect(authenticated().withUsername("nouveau@example.com").withRoles("CLIENT"));

        Client cree = clients.findByEmail("nouveau@example.com").orElseThrow();
        assertThat(encodeur.matches(MOT_DE_PASSE, cree.getMotDePasse())).isTrue();
        assertThat(cree.getDateInscription()).isEqualTo(LocalDate.now());
        mockMvc.perform(formLogin("/connexion").user("identifiant", "nouveau@example.com").password("motDePasse", MOT_DE_PASSE))
                .andExpect(redirectedUrl("/espace"));
    }

    @Test
    void refuseUneInscriptionInvalide() throws Exception {
        mockMvc.perform(inscription(EMAIL, "P9")).andExpect(content().string(containsString("déjà utilisée")));
        mockMvc.perform(inscription("autre@example.com", "P1")).andExpect(content().string(containsString("permis est déjà")));
        mockMvc.perform(inscription("autre@example.com", "P9").param("confirmation", "different1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/inscription").param("email", "pas-un-email").param("motDePasse", "court"))
                .andExpect(content().string(containsString("Adresse e-mail invalide.")))
                .andExpect(content().string(containsString("Entre 8 et 72")));
        assertThat(clients.count()).isEqualTo(1);
    }

    @Test
    void uneInscriptionNePeutPasReprendreUnClientCreeParLAgence() throws Exception {
        clients.save(new Client(null, "Agence", "Sans", "sans@example.com", "0600000000", "P5", LocalDate.now()));

        mockMvc.perform(inscription("sans@example.com", "P6"))
                .andExpect(content().string(containsString("déjà utilisée")));

        assertThat(clients.findByEmail("sans@example.com").orElseThrow().aUnCompte()).isFalse();
    }

    @Test
    void redirigeUnVisiteurVersLaConnexionPourReserver() throws Exception {
        mockMvc.perform(get("/espace/reservations/nouvelle").param("vehicule", libre.getIdVehicule().toString()))
                .andExpect(redirectedUrl("/connexion"));
        mockMvc.perform(get("/espace")).andExpect(redirectedUrl("/connexion"));
    }

    @Test
    void unClientNAccedePasAuBackOffice() throws Exception {
        for (String url : new String[]{"/tableau-de-bord", "/vehicules", "/clients", "/paiements", "/parking", "/planning"}) {
            mockMvc.perform(get(url).with(client())).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/vehicules/" + libre.getIdVehicule() + "/supprimer").with(client())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "chef", roles = "MANAGER")
    void unEmployeNAccedePasALEspaceClient() throws Exception {
        mockMvc.perform(get("/espace")).andExpect(status().isForbidden());
        mockMvc.perform(get("/tableau-de-bord")).andExpect(status().isOk());
    }

    @Test
    void suitLeParcoursCompletDuClient() throws Exception {
        String debut = DEMAIN.toString();
        String fin = DEMAIN.plusDays(2).toString();

        mockMvc.perform(get("/espace").with(client()))
                .andExpect(content().string(containsString("Bonjour Alice")))
                .andExpect(content().string(containsString("Aucune réservation pour le moment")));
        mockMvc.perform(get("/espace/reservations/nouvelle").with(client())
                        .param("vehicule", libre.getIdVehicule().toString()).param("debut", debut).param("fin", fin))
                .andExpect(content().string(containsString("120,00 €")))
                .andExpect(content().string(containsString("Continuer vers le paiement")));

        mockMvc.perform(reservation(libre, debut, fin));
        Reservation creee = reservations.findAll().get(0);
        assertThat(creee.getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
        assertThat(creee.getClient().getIdClient()).isEqualTo(alice.getIdClient());
        String url = "/espace/reservations/" + creee.getIdReservation();

        mockMvc.perform(get(url + "/paiement").with(client()))
                .andExpect(content().string(containsString("Paiement simulé")))
                .andExpect(content().string(containsString("Payer 120,00 €")));

        mockMvc.perform(paiement(url, "1234 5678 9012 3456", "12/30", "123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Numéro de carte invalide.")));
        mockMvc.perform(paiement(url, "4242 4242 4242 4242", "01/20", "123"))
                .andExpect(content().string(containsString("Date invalide ou dépassée")));
        mockMvc.perform(paiement(url, "4242 4242 4242 4242", "12/30", "1"))
                .andExpect(content().string(containsString("3 ou 4 chiffres.")));
        assertThat(paiements.count()).isZero();

        // A price sent by the browser is ignored: the amount only ever comes from the server.
        mockMvc.perform(paiement(url, "4242 4242 4242 4242", "12/30", "123").param("montant", "1"))
                .andExpect(redirectedUrl(url + "/confirmation"));

        Contrat contrat = contrats.findAll().get(0);
        assertThat(contrat.getMontantTotal()).isEqualByComparingTo("120.00");
        assertThat(contrat.getEmploye()).isNull();
        assertThat(paiements.findAll().get(0).getMontant()).isEqualByComparingTo("120.00");
        assertThat(reservations.findAll().get(0).getStatut()).isEqualTo(StatutReservation.CONFIRMEE);

        mockMvc.perform(get(url + "/confirmation").with(client()))
                .andExpect(content().string(containsString("C'est réservé !")))
                .andExpect(content().string(containsString("Voir ma place de parking")));
        mockMvc.perform(get(url + "/parking").with(client()))
                .andExpect(content().string(containsString("Place n°7")))
                .andExpect(content().string(containsString("place-surlignee")));
        mockMvc.perform(get(url + "/contrat").with(client()))
                .andExpect(content().string(containsString("signature en ligne")))
                .andExpect(content().string(containsString("120,00 €")));

        // Paying twice changes nothing.
        mockMvc.perform(paiement(url, "4242 4242 4242 4242", "12/30", "123"))
                .andExpect(flash().attribute("erreur", containsString("plus à payer")));
        assertThat(paiements.count()).isEqualTo(1);
        mockMvc.perform(get(url + "/paiement").with(client())).andExpect(redirectedUrl(url));

        // The catalogue no longer offers the vehicle on these dates, and the online contract opens in the back office.
        mockMvc.perform(get("/catalogue").param("debut", debut).param("fin", fin))
                .andExpect(content().string(not(containsString("Renault Clio"))));
    }

    @Test
    void refuseUneReservationSurUnVehiculeDejaPrisOuDansLePasse() throws Exception {
        reservations.save(new Reservation(null, DEMAIN, DEMAIN.plusDays(1), StatutReservation.CONFIRMEE, alice, libre));

        mockMvc.perform(reservation(libre, DEMAIN.plusDays(1).toString(), DEMAIN.plusDays(2).toString()))
                .andExpect(flash().attribute("erreur", containsString("vient d'être réservé")));
        mockMvc.perform(reservation(libre, LocalDate.now().minusDays(1).toString(), DEMAIN.toString()))
                .andExpect(flash().attribute("erreur", containsString("passée")));
        mockMvc.perform(reservation(libre, DEMAIN.plusDays(5).toString(), DEMAIN.plusDays(4).toString()))
                .andExpect(flash().attribute("erreur", containsString("date de fin")));
        assertThat(reservations.count()).isEqualTo(1);
    }

    @Test
    void unClientNeVoitPasLaReservationDUnAutre() throws Exception {
        Client bob = clients.save(new Client(null, "Martin", "Bob", "bob@example.com", "0600000000", "P2", LocalDate.now()));
        Reservation deBob = reservations.save(new Reservation(null, DEMAIN, DEMAIN, StatutReservation.EN_ATTENTE, bob, libre));
        String url = "/espace/reservations/" + deBob.getIdReservation();

        for (String page : new String[]{"", "/paiement", "/confirmation", "/parking", "/contrat"}) {
            mockMvc.perform(get(url + page).with(client())).andExpect(status().isNotFound());
        }
        mockMvc.perform(post(url + "/annuler").with(client())).andExpect(status().isNotFound());
        mockMvc.perform(paiement(url, "4242 4242 4242 4242", "12/30", "123")).andExpect(status().isNotFound());
        mockMvc.perform(post(url + "/payer-en-agence").with(client())).andExpect(status().isNotFound());

        assertThat(reservations.findById(deBob.getIdReservation()).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
    }

    @Test
    void annuleUneReservationFutureMaisPasCommencee() throws Exception {
        Reservation future = reservations.save(new Reservation(null, DEMAIN, DEMAIN, StatutReservation.EN_ATTENTE, alice, libre));
        Vehicule autre = vehicule("GG-777-GG", "Dacia", "Duster", StatutVehicule.DISPONIBLE, null);
        Reservation commencee = reservations.save(new Reservation(null, LocalDate.now(), DEMAIN, StatutReservation.CONFIRMEE, alice, autre));

        mockMvc.perform(post("/espace/reservations/" + future.getIdReservation() + "/annuler").with(client()))
                .andExpect(flash().attribute("succes", containsString("annulée")));
        mockMvc.perform(post("/espace/reservations/" + commencee.getIdReservation() + "/annuler").with(client()))
                .andExpect(flash().attribute("erreur", containsString("Contactez votre agence")));

        assertThat(reservations.findById(future.getIdReservation()).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(reservations.findById(commencee.getIdReservation()).orElseThrow().getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
    }

    @Test
    void payerALAgenceLaisseLaReservationEnAttente() throws Exception {
        Reservation reservation = reservations.save(new Reservation(null, DEMAIN, DEMAIN, StatutReservation.EN_ATTENTE, alice, libre));
        String url = "/espace/reservations/" + reservation.getIdReservation();

        mockMvc.perform(post(url + "/payer-en-agence").with(client())).andExpect(redirectedUrl(url + "/confirmation"));

        mockMvc.perform(get(url + "/confirmation").with(client()))
                .andExpect(content().string(containsString("Demande enregistrée")));
        assertThat(paiements.count()).isZero();
        mockMvc.perform(get(url + "/contrat").with(client())).andExpect(status().isNotFound());
    }

    @Test
    void modifieSonProfilEtSonMotDePasse() throws Exception {
        mockMvc.perform(post("/espace/profil").with(client())
                        .param("prenom", "Alicia").param("nom", "Durand").param("telephone", "0611111111")
                        .param("email", "pirate@example.com").param("motDePasse", "pirate123"))
                .andExpect(redirectedUrl("/espace/profil"));
        Client modifie = clients.findByEmail(EMAIL).orElseThrow();
        assertThat(modifie.getPrenom()).isEqualTo("Alicia");
        assertThat(encodeur.matches(MOT_DE_PASSE, modifie.getMotDePasse())).isTrue();

        mockMvc.perform(post("/espace/mot-de-passe").with(client())
                        .param("actuel", "mauvais").param("nouveau", "nouveau1234").param("confirmation", "nouveau1234"))
                .andExpect(flash().attribute("erreur", containsString("incorrect")));
        mockMvc.perform(post("/espace/mot-de-passe").with(client())
                        .param("actuel", MOT_DE_PASSE).param("nouveau", "nouveau1234").param("confirmation", "nouveau1234"))
                .andExpect(flash().attribute("succes", containsString("modifié")));
        assertThat(encodeur.matches("nouveau1234", clients.findByEmail(EMAIL).orElseThrow().getMotDePasse())).isTrue();
    }

    @Test
    @WithMockUser(username = "chef", roles = "MANAGER")
    void leBackOfficeConserveLeCompteDuClientEtGereLeParking() throws Exception {
        mockMvc.perform(post("/clients/" + alice.getIdClient())
                        .param("nom", "Durand").param("prenom", "Alice").param("email", EMAIL)
                        .param("telephone", "0622222222").param("numPermis", "P1")
                        .param("dateInscription", LocalDate.now().toString()))
                .andExpect(redirectedUrl("/clients"));
        assertThat(encodeur.matches(MOT_DE_PASSE, clients.findByEmail(EMAIL).orElseThrow().getMotDePasse())).isTrue();

        mockMvc.perform(get("/parking"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/vehicules/" + libre.getIdVehicule())))
                .andExpect(content().string(matchesPattern("(?s).*parking-plan.*")));

        mockMvc.perform(post("/vehicules")
                        .param("immatriculation", "HH-888-HH").param("marque", "Fiat").param("modele", "500")
                        .param("categorie", "CITADINE").param("tarifJournalier", "30").param("statut", "DISPONIBLE")
                        .param("agence", agence.getIdAgence().toString()).param("placeParking", "7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("déjà occupée")));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor client() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(EMAIL).roles("CLIENT");
    }

    private MockHttpServletRequestBuilder inscription(String email, String permis) {
        return post("/inscription")
                .param("prenom", "Nina").param("nom", "Roux").param("email", email)
                .param("telephone", "0600000000").param("numPermis", permis)
                .param("motDePasse", MOT_DE_PASSE).param("confirmation", MOT_DE_PASSE);
    }

    private MockHttpServletRequestBuilder reservation(Vehicule vehicule, String debut, String fin) {
        return post("/espace/reservations").with(client())
                .param("vehicule", vehicule.getIdVehicule().toString()).param("debut", debut).param("fin", fin);
    }

    private MockHttpServletRequestBuilder paiement(String url, String numero, String expiration, String cryptogramme) {
        return post(url + "/paiement").with(client())
                .param("titulaire", "Alice Durand").param("numero", numero)
                .param("expiration", expiration).param("cryptogramme", cryptogramme);
    }

    private Vehicule vehicule(String immatriculation, String marque, String modele, StatutVehicule statut, Integer place) {
        Vehicule vehicule = new Vehicule(null, immatriculation, marque, modele, CategorieVehicule.CITADINE,
                new BigDecimal("40.00"), statut, agence, new HashSet<>());
        vehicule.setPlaceParking(place);
        return vehicules.save(vehicule);
    }

    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url).with(csrf());
    }
}
