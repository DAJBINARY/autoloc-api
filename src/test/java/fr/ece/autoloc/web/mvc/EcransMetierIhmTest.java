package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:metier;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithMockUser(username = "chef", roles = "MANAGER")
class EcransMetierIhmTest {

    private static final LocalDate AUJOURDHUI = LocalDate.now();

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

    private Agence agence;
    private Client client;
    private Vehicule vehicule;

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
        agence = agences.save(new Agence(null, "Paris", "Paris", "1 rue", "0100000000"));
        client = clients.save(new Client(null, "Durand", "Alice", "alice@example.com", "0600000000", "P1", AUJOURDHUI));
        vehicule = vehicules.save(vehicule("AA-111-AA", CategorieVehicule.SUV));
    }

    @Test
    void suitUneReservationDeLaConfirmationAuRetour() throws Exception {
        String url = "/reservations/" + reservation(StatutReservation.EN_ATTENTE, 0, 2).getIdReservation();

        mockMvc.perform(get(url)).andExpect(content().string(containsString("Confirmer")));
        mockMvc.perform(post(url + "/depart")).andExpect(flash().attributeExists("erreur"));

        mockMvc.perform(post(url + "/confirmer")).andExpect(redirectedUrl(url));
        mockMvc.perform(get(url)).andExpect(content().string(containsString("Enregistrer le départ")));

        mockMvc.perform(post(url + "/depart")).andExpect(flash().attribute("succes", containsString("Départ")));
        assertThat(statutDuVehicule()).isEqualTo(StatutVehicule.LOUE);
        mockMvc.perform(get("/tableau-de-bord")).andExpect(content().string(containsString("Locations en cours")));
        assertThat(reservations.countByDateDepartIsNotNullAndDateRetourIsNull()).isEqualTo(1);

        mockMvc.perform(post(url + "/annuler")).andExpect(flash().attribute("erreur", containsString("déjà parti")));
        mockMvc.perform(post(url + "/supprimer")).andExpect(flash().attribute("erreur", containsString("retour")));

        mockMvc.perform(post(url + "/retour")).andExpect(flash().attribute("succes", containsString("Retour")));
        assertThat(statutDuVehicule()).isEqualTo(StatutVehicule.DISPONIBLE);
        Reservation finie = reservations.findById(Long.valueOf(url.substring(url.lastIndexOf('/') + 1))).orElseThrow();
        assertThat(finie.getStatut()).isEqualTo(StatutReservation.TERMINEE);
        assertThat(finie.getDateRetour()).isEqualTo(AUJOURDHUI);

        mockMvc.perform(post(url + "/retour")).andExpect(flash().attributeExists("erreur"));
        mockMvc.perform(post("/reservations/999999/confirmer")).andExpect(status().isNotFound());
    }

    @Test
    void conserveLeSuiviQuandOnModifieUneLocationEnCours() throws Exception {
        Reservation reservation = reservation(StatutReservation.CONFIRMEE, 0, 2);
        String url = "/reservations/" + reservation.getIdReservation();
        mockMvc.perform(post(url + "/depart"));

        mockMvc.perform(formulaireReservation(url, "2").param("dateDepart", "2000-01-01"))
                .andExpect(redirectedUrl("/reservations"));
        assertThat(reservations.findById(reservation.getIdReservation()).orElseThrow().getDateDepart()).isEqualTo(AUJOURDHUI);

        mockMvc.perform(post(url)
                        .param("client", client.getIdClient().toString())
                        .param("vehicule", vehicule.getIdVehicule().toString())
                        .param("dateDebut", AUJOURDHUI.toString())
                        .param("dateFin", AUJOURDHUI.toString())
                        .param("statut", "ANNULEE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Location en cours")));
    }

    @Test
    void refuseLeDepartDUnVehiculeEnMaintenance() throws Exception {
        Reservation reservation = reservation(StatutReservation.CONFIRMEE, 0, 2);
        mockMvc.perform(post("/maintenances")
                        .param("vehicule", vehicule.getIdVehicule().toString())
                        .param("dateDebut", AUJOURDHUI.toString())
                        .param("description", "Freins"))
                .andExpect(redirectedUrl("/maintenances"));

        mockMvc.perform(post("/reservations/" + reservation.getIdReservation() + "/depart"))
                .andExpect(flash().attribute("erreur", containsString("pas disponible")));
    }

    @Test
    void refuseDeReserverUnVehiculeEnMaintenanceSurCesDates() throws Exception {
        maintenances.save(new Maintenance(null, AUJOURDHUI.plusDays(5), AUJOURDHUI.plusDays(6), "Freins", vehicule));

        mockMvc.perform(post("/reservations")
                        .param("client", client.getIdClient().toString())
                        .param("vehicule", vehicule.getIdVehicule().toString())
                        .param("dateDebut", AUJOURDHUI.plusDays(6).toString())
                        .param("dateFin", AUJOURDHUI.plusDays(8).toString())
                        .param("statut", "EN_ATTENTE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("en maintenance sur ces dates")));
        assertThat(reservations.count()).isZero();

        mockMvc.perform(post("/reservations")
                        .param("client", client.getIdClient().toString())
                        .param("vehicule", vehicule.getIdVehicule().toString())
                        .param("dateDebut", AUJOURDHUI.plusDays(7).toString())
                        .param("dateFin", AUJOURDHUI.plusDays(8).toString())
                        .param("statut", "EN_ATTENTE"))
                .andExpect(redirectedUrl("/reservations"));
    }

    @Test
    void metLeVehiculeEnMaintenanceJusquALaCloture() throws Exception {
        mockMvc.perform(post("/maintenances")
                        .param("vehicule", vehicule.getIdVehicule().toString())
                        .param("dateDebut", AUJOURDHUI.toString())
                        .param("description", "Freins"))
                .andExpect(redirectedUrl("/maintenances"));
        assertThat(statutDuVehicule()).isEqualTo(StatutVehicule.MAINTENANCE);

        Long id = maintenances.findAll().get(0).getIdMaintenance();
        mockMvc.perform(get("/maintenances/" + id)).andExpect(content().string(containsString("Clôturer")));
        mockMvc.perform(post("/maintenances/" + id + "/cloturer"))
                .andExpect(flash().attribute("succes", "Maintenance clôturée."));

        assertThat(statutDuVehicule()).isEqualTo(StatutVehicule.DISPONIBLE);
        assertThat(maintenances.findById(id).orElseThrow().getDateFin()).isEqualTo(AUJOURDHUI);
        mockMvc.perform(post("/maintenances/" + id + "/cloturer")).andExpect(flash().attributeExists("erreur"));
        mockMvc.perform(get("/maintenances/" + id)).andExpect(content().string(not(containsString("Clôturer"))));
    }

    @Test
    void laisseUnVehiculeLoueEnLocationPuisLeMetEnMaintenanceASonRetour() throws Exception {
        Reservation reservation = reservation(StatutReservation.CONFIRMEE, 0, 2);
        String url = "/reservations/" + reservation.getIdReservation();
        mockMvc.perform(post(url + "/depart"));

        mockMvc.perform(post("/maintenances")
                        .param("vehicule", vehicule.getIdVehicule().toString())
                        .param("dateDebut", AUJOURDHUI.toString())
                        .param("description", "Pneus"))
                .andExpect(redirectedUrl("/maintenances"));
        assertThat(statutDuVehicule()).isEqualTo(StatutVehicule.LOUE);

        mockMvc.perform(post(url + "/retour"));
        assertThat(statutDuVehicule()).isEqualTo(StatutVehicule.MAINTENANCE);
    }

    @Test
    void rendSonStatutAuVehiculeQuandOnSupprimeSaMaintenance() throws Exception {
        mockMvc.perform(post("/maintenances")
                .param("vehicule", vehicule.getIdVehicule().toString())
                .param("dateDebut", AUJOURDHUI.toString())
                .param("description", "Freins"));
        Long id = maintenances.findAll().get(0).getIdMaintenance();

        mockMvc.perform(post("/maintenances/" + id + "/supprimer")).andExpect(redirectedUrl("/maintenances"));

        assertThat(statutDuVehicule()).isEqualTo(StatutVehicule.DISPONIBLE);
    }

    @Test
    void chercheLesVehiculesLibresEtPrepareLaReservation() throws Exception {
        Vehicule utilitaire = vehicules.save(vehicule("UU-222-UU", CategorieVehicule.UTILITAIRE));
        Vehicule enTravaux = vehicules.save(vehicule("TT-333-TT", CategorieVehicule.SUV));
        reservation(StatutReservation.CONFIRMEE, 10, 12);
        maintenances.save(new Maintenance(null, AUJOURDHUI, null, "Longue", enTravaux));
        String debut = AUJOURDHUI.plusDays(11).toString();
        String fin = AUJOURDHUI.plusDays(13).toString();

        mockMvc.perform(get("/disponibilites"))
                .andExpect(content().string(containsString("Choisissez des dates")));
        mockMvc.perform(get("/disponibilites").param("debut", debut).param("fin", fin))
                .andExpect(content().string(containsString("UU-222-UU")))
                .andExpect(content().string(containsString("210,00 €")))
                .andExpect(content().string(not(containsString("AA-111-AA"))))
                .andExpect(content().string(not(containsString("TT-333-TT"))));
        mockMvc.perform(get("/disponibilites").param("debut", debut).param("fin", fin).param("categorie", "SUV"))
                .andExpect(content().string(containsString("Aucun véhicule libre")));
        mockMvc.perform(get("/disponibilites").param("debut", fin).param("fin", debut))
                .andExpect(content().string(containsString("La date de fin doit")));

        mockMvc.perform(get("/reservations/nouveau")
                        .param("vehicule", utilitaire.getIdVehicule().toString()).param("debut", debut).param("fin", fin))
                .andExpect(content().string(containsString("value=\"" + debut + "\"")))
                .andExpect(content().string(containsString("value=\"" + fin + "\"")));
    }

    @Test
    void afficheLePlanningDuMois() throws Exception {
        Reservation reservation = reservation(StatutReservation.CONFIRMEE, 0, 1);
        maintenances.save(new Maintenance(null, AUJOURDHUI, AUJOURDHUI, "Vidange", vehicules.save(vehicule("VV-444-VV", CategorieVehicule.SUV))));

        mockMvc.perform(get("/planning"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AA-111-AA")))
                .andExpect(content().string(containsString("/reservations/" + reservation.getIdReservation())))
                .andExpect(content().string(containsString("case-maintenance")))
                .andExpect(content().string(containsString("Vidange")));

        String autreMois = YearMonth.from(AUJOURDHUI).plusMonths(3).toString();
        mockMvc.perform(get("/planning").param("mois", autreMois))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/reservations/" + reservation.getIdReservation()))));
    }

    @Test
    void imprimeUnContrat() throws Exception {
        Contrat contrat = contrat();

        mockMvc.perform(get("/contrats/" + contrat.getIdContrat()))
                .andExpect(content().string(containsString("/imprimer")));
        mockMvc.perform(get("/contrats/" + contrat.getIdContrat() + "/imprimer"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Contrat de location n°" + contrat.getIdContrat())))
                .andExpect(content().string(containsString("Alice Durand")))
                .andExpect(content().string(containsString("Permis : P1")))
                .andExpect(content().string(containsString("100,00 €")))
                .andExpect(content().string(containsString("40,00 €")));
        mockMvc.perform(get("/contrats/999999/imprimer")).andExpect(status().isNotFound());
    }

    @Test
    void afficheLesStatistiques() throws Exception {
        contrat();
        reservation(StatutReservation.ANNULEE, 30, 31);

        mockMvc.perform(get("/statistiques"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Encaissements des 12 derniers mois")))
                .andExpect(content().string(containsString("60,00 €")))
                .andExpect(content().string(containsString("Carte")))
                .andExpect(content().string(containsString("Annulée")))
                .andExpect(content().string(containsString("height: 100%")))
                .andExpect(content().string(containsString("Peugeot 3008 (AA-111-AA)")));
    }

    @Test
    void afficheLesStatistiquesSansDonnees() throws Exception {
        mockMvc.perform(get("/statistiques"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aucun paiement pour le moment.")));
    }

    private Contrat contrat() {
        Employe employe = employes.save(new Employe(null, "Martin", "Sophie", RoleEmploye.AGENT, agence));
        Contrat contrat = contrats.save(new Contrat(null, AUJOURDHUI, new BigDecimal("100.00"), true,
                reservation(StatutReservation.CONFIRMEE, 0, 2), employe));
        paiements.save(new Paiement(null, new BigDecimal("60.00"), AUJOURDHUI, ModePaiement.CARTE, contrat));
        return contrat;
    }

    private Reservation reservation(StatutReservation statut, int debut, int fin) {
        return reservations.save(new Reservation(null, AUJOURDHUI.plusDays(debut), AUJOURDHUI.plusDays(fin), statut, client, vehicule));
    }

    private MockHttpServletRequestBuilder formulaireReservation(String url, String joursEnPlus) {
        return post(url)
                .param("client", client.getIdClient().toString())
                .param("vehicule", vehicule.getIdVehicule().toString())
                .param("dateDebut", AUJOURDHUI.toString())
                .param("dateFin", AUJOURDHUI.plusDays(Long.parseLong(joursEnPlus)).toString())
                .param("statut", "CONFIRMEE");
    }

    private StatutVehicule statutDuVehicule() {
        return vehicules.findById(vehicule.getIdVehicule()).orElseThrow().getStatut();
    }

    private Vehicule vehicule(String immatriculation, CategorieVehicule categorie) {
        return new Vehicule(immatriculation, "Peugeot", "3008", categorie, new BigDecimal("70.00"),
                StatutVehicule.DISPONIBLE, agence);
    }

    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url).with(csrf());
    }
}
