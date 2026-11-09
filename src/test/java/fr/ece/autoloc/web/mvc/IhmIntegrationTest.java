package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import fr.ece.autoloc.repository.EquipementRepository;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static fr.ece.autoloc.domain.CategorieVehicule.SUV;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ihm;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithMockUser(username = "chef", roles = "MANAGER")
class IhmIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
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
    private EquipementRepository equipements;
    @Autowired
    private AgenceRepository agences;

    private Agence agence;

    @BeforeEach
    void viderLaBase() {
        paiements.deleteAll();
        contrats.deleteAll();
        reservations.deleteAll();
        maintenances.deleteAll();
        vehicules.deleteAll();
        employes.deleteAll();
        clients.deleteAll();
        equipements.deleteAll();
        agences.deleteAll();
        agence = agences.save(new Agence(null, "Paris Centre", "Paris", "1 rue de Rivoli", "0100000000"));
    }

    @Test
    void afficheLeTableauDeBord() throws Exception {
        mockMvc.perform(get("/tableau-de-bord"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tableau de bord")))
                .andExpect(content().string(containsString("Aucun départ à venir.")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vehicules", "clients", "employes", "reservations", "contrats",
            "paiements", "maintenances", "equipements"})
    void afficheLaListeEtLeFormulaireDeChaqueSection(String section) throws Exception {
        mockMvc.perform(get("/" + section))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aucun élément pour le moment.")));
        mockMvc.perform(get("/" + section + "/nouveau"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enregistrer")));
    }

    @Test
    void creeUnVehiculeAvecSonAgenceEtSesEquipements() throws Exception {
        Equipement gps = equipements.save(new Equipement(null, "GPS"));
        Equipement siege = equipements.save(new Equipement(null, "Siège enfant"));

        mockMvc.perform(post("/vehicules")
                        .param("immatriculation", "  ZZ-999-ZZ ")
                        .param("marque", "Renault")
                        .param("modele", "Megane")
                        .param("categorie", "BERLINE")
                        .param("tarifJournalier", "49.90")
                        .param("statut", "DISPONIBLE")
                        .param("agence", agence.getIdAgence().toString())
                        .param("equipements", gps.getIdEquipement().toString(), siege.getIdEquipement().toString()))
                .andExpect(redirectedUrl("/vehicules"))
                .andExpect(flash().attribute("succes", "Enregistrement effectué."));

        mockMvc.perform(get("/vehicules"))
                .andExpect(content().string(containsString("ZZ-999-ZZ")))
                .andExpect(content().string(containsString("49,90")))
                .andExpect(content().string(containsString("Berline")))
                .andExpect(content().string(containsString("Paris Centre")));

        Long id = vehicules.findAll().get(0).getIdVehicule();
        mockMvc.perform(get("/vehicules/" + id))
                .andExpect(content().string(containsString("Siège enfant")));
        mockMvc.perform(get("/vehicules/" + id + "/modifier"))
                .andExpect(content().string(matchesPattern(
                        "(?s).*name=\"agence\".*value=\"" + agence.getIdAgence() + "\"[^>]*selected.*")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*name=\"equipements\"[^>]*value=\"" + gps.getIdEquipement() + "\"[^>]*checked.*")));
    }

    @Test
    void refuseUnVehiculeInvalideEnFrancais() throws Exception {
        mockMvc.perform(post("/vehicules").param("tarifJournalier", "pas-un-nombre").param("agence", "pas-un-id"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ce champ est obligatoire.")))
                .andExpect(content().string(containsString("Saisissez un nombre valide.")))
                .andExpect(content().string(containsString("Valeur invalide.")));

        assertThat(vehicules.count()).isZero();
    }

    @Test
    void refuseUnLienObligatoireManquantOuInconnu() throws Exception {
        mockMvc.perform(post("/maintenances")
                        .param("dateDebut", "2026-12-01")
                        .param("description", "Vidange")
                        .param("vehicule", "999999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ce champ est obligatoire.")));

        assertThat(maintenances.count()).isZero();
    }

    @Test
    void refuseUneImmatriculationDejaUtilisee() throws Exception {
        vehicules.save(vehicule("AA-111-AA"));

        mockMvc.perform(post("/vehicules")
                        .param("immatriculation", "AA-111-AA")
                        .param("marque", "Renault")
                        .param("modele", "Clio")
                        .param("categorie", "CITADINE")
                        .param("tarifJournalier", "30")
                        .param("statut", "DISPONIBLE")
                        .param("agence", agence.getIdAgence().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("une valeur unique")))
                .andExpect(content().string(containsString("Paris Centre")));

        assertThat(vehicules.count()).isEqualTo(1);
    }

    @Test
    void modifieLeVehiculeDeLUrlMemeSiLeFormulaireEnvoieUnAutreId() throws Exception {
        Vehicule cible = vehicules.save(vehicule("AA-111-AA"));
        Vehicule autre = vehicules.save(vehicule("BB-222-BB"));

        mockMvc.perform(get("/vehicules/" + cible.getIdVehicule() + "/modifier"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AA-111-AA")))
                .andExpect(content().string(containsString("selected")));

        mockMvc.perform(post("/vehicules/" + cible.getIdVehicule())
                        .param("idVehicule", autre.getIdVehicule().toString())
                        .param("immatriculation", "AA-111-AA")
                        .param("marque", "Dacia")
                        .param("modele", "Duster")
                        .param("categorie", "SUV")
                        .param("tarifJournalier", "60")
                        .param("statut", "LOUE")
                        .param("agence", agence.getIdAgence().toString()))
                .andExpect(redirectedUrl("/vehicules"));

        assertThat(vehicules.findById(cible.getIdVehicule()).orElseThrow().getMarque()).isEqualTo("Dacia");
        assertThat(vehicules.findById(autre.getIdVehicule()).orElseThrow().getMarque()).isEqualTo("Peugeot");
    }

    @Test
    void supprimeUnVehicule() throws Exception {
        Vehicule vehicule = vehicules.save(vehicule("AA-111-AA"));

        mockMvc.perform(post("/vehicules/" + vehicule.getIdVehicule() + "/supprimer"))
                .andExpect(redirectedUrl("/vehicules"));

        assertThat(vehicules.count()).isZero();
    }

    @Test
    void refuseDeSupprimerUnElementEncoreUtilise() throws Exception {
        Vehicule vehicule = vehicules.save(vehicule("AA-111-AA"));

        mockMvc.perform(post("/agences/" + agence.getIdAgence() + "/supprimer"))
                .andExpect(redirectedUrl("/agences"))
                .andExpect(flash().attribute("erreur", "Suppression impossible : cet élément est encore utilisé."));

        assertThat(agences.existsById(agence.getIdAgence())).isTrue();
        assertThat(vehicules.existsById(vehicule.getIdVehicule())).isTrue();
    }

    @Test
    void refuseUneReservationDontLaFinPrecedeLeDebut() throws Exception {
        mockMvc.perform(post("/reservations")
                        .param("dateDebut", "2026-12-10")
                        .param("dateFin", "2026-12-01")
                        .param("statut", "EN_ATTENTE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La date de fin doit")));

        assertThat(reservations.count()).isZero();
    }

    @Test
    void conserveLesDatesAuFormatIsoEtLesAfficheEnFrancais() throws Exception {
        Client client = clients.save(client("alice@example.com", "P1"));
        Vehicule vehicule = vehicules.save(vehicule("AA-111-AA"));

        mockMvc.perform(reservation(client, vehicule, "2026-12-01", "2026-12-10", "CONFIRMEE"))
                .andExpect(redirectedUrl("/reservations"));

        Long id = reservations.findAll().get(0).getIdReservation();

        mockMvc.perform(get("/reservations"))
                .andExpect(content().string(containsString("01/12/2026")))
                .andExpect(content().string(containsString("Alice Durand")))
                .andExpect(content().string(containsString("Peugeot 3008 (AA-111-AA)")));
        mockMvc.perform(get("/reservations/" + id + "/modifier"))
                .andExpect(content().string(containsString("value=\"2026-12-01\"")));
    }

    @Test
    void refuseDeReserverUnVehiculeDejaPrisSaufSiLaReservationEstAnnulee() throws Exception {
        Client client = clients.save(client("alice@example.com", "P1"));
        Vehicule vehicule = vehicules.save(vehicule("AA-111-AA"));
        mockMvc.perform(reservation(client, vehicule, "2026-12-01", "2026-12-10", "CONFIRMEE"))
                .andExpect(redirectedUrl("/reservations"));
        Long premiere = reservations.findAll().get(0).getIdReservation();

        mockMvc.perform(reservation(client, vehicule, "2026-12-10", "2026-12-15", "EN_ATTENTE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("déjà réservé")));
        assertThat(reservations.count()).isEqualTo(1);

        // A reservation does not conflict with itself, and the days around it stay free.
        mockMvc.perform(post("/reservations/" + premiere)
                        .param("client", client.getIdClient().toString())
                        .param("vehicule", vehicule.getIdVehicule().toString())
                        .param("dateDebut", "2026-12-02")
                        .param("dateFin", "2026-12-09")
                        .param("statut", "CONFIRMEE"))
                .andExpect(redirectedUrl("/reservations"));
        mockMvc.perform(reservation(client, vehicule, "2026-12-10", "2026-12-15", "EN_ATTENTE"))
                .andExpect(redirectedUrl("/reservations"));
        mockMvc.perform(reservation(client, vehicule, "2026-12-02", "2026-12-03", "ANNULEE"))
                .andExpect(redirectedUrl("/reservations"));
    }

    @Test
    void neModifiePasUneEntiteLieeDepuisUnFormulaire() throws Exception {
        Client client = clients.save(client("alice@example.com", "P1"));
        Vehicule vehicule = vehicules.save(vehicule("AA-111-AA"));

        mockMvc.perform(reservation(client, vehicule, "2026-12-01", "2026-12-10", "CONFIRMEE")
                        .param("client.nom", "Pirate"))
                .andExpect(redirectedUrl("/reservations"));

        assertThat(clients.findById(client.getIdClient()).orElseThrow().getNom()).isEqualTo("Durand");
    }

    @Test
    void suitLeParcoursDeLaReservationAuPaiement() throws Exception {
        Client client = clients.save(client("alice@example.com", "P1"));
        Vehicule vehicule = vehicules.save(vehicule("AA-111-AA"));
        Employe employe = employes.save(new Employe(null, "Martin", "Sophie", RoleEmploye.AGENT, agence));

        mockMvc.perform(get("/reservations/nouveau").param("client", client.getIdClient().toString())
                        .param("vehicule", "pas-un-id"))
                .andExpect(content().string(matchesPattern(
                        "(?s).*value=\"" + client.getIdClient() + "\"[^>]*selected.*")));
        mockMvc.perform(reservation(client, vehicule, "2026-12-01", "2026-12-03", "CONFIRMEE"))
                .andExpect(redirectedUrl("/reservations"));
        Reservation resa = reservations.findAll().get(0);
        String idResa = resa.getIdReservation().toString();

        mockMvc.perform(get("/reservations/" + idResa))
                .andExpect(content().string(containsString("Établir le contrat")));

        // 3 days charged at 70 euros
        mockMvc.perform(get("/contrats/nouveau").param("reservation", idResa))
                .andExpect(content().string(containsString("value=\"210.00\"")));

        mockMvc.perform(post("/contrats")
                        .param("reservation", idResa)
                        .param("employe", employe.getIdEmploye().toString())
                        .param("dateSignature", "2026-11-30")
                        .param("montantTotal", "210")
                        .param("valide", "true"))
                .andExpect(redirectedUrl("/contrats"));
        Contrat contrat = contrats.findAll().get(0);
        String idContrat = contrat.getIdContrat().toString();

        mockMvc.perform(post("/contrats")
                        .param("reservation", idResa)
                        .param("employe", employe.getIdEmploye().toString())
                        .param("dateSignature", "2026-11-30")
                        .param("montantTotal", "210"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("déjà un contrat")));
        assertThat(contrats.count()).isEqualTo(1);

        mockMvc.perform(paiement(idContrat, "150")).andExpect(redirectedUrl("/paiements"));
        mockMvc.perform(paiement(idContrat, "60.01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dépasse le reste à payer")));
        assertThat(paiements.count()).isEqualTo(1);

        Long idPaiement = paiements.findAll().get(0).getIdPaiement();
        mockMvc.perform(post("/paiements/" + idPaiement)
                        .param("contrat", idContrat)
                        .param("montant", "210")
                        .param("datePaiement", "2026-12-01")
                        .param("modePaiement", "CARTE"))
                .andExpect(redirectedUrl("/paiements"));
        mockMvc.perform(get("/contrats/" + idContrat))
                .andExpect(content().string(containsString("Reste à payer")))
                .andExpect(content().string(containsString("0,00 €")));
        mockMvc.perform(get("/reservations/" + idResa))
                .andExpect(content().string(not(containsString("Établir le contrat"))));
        mockMvc.perform(get("/contrats/nouveau"))
                .andExpect(content().string(containsString("Aucune réservation sans contrat.")));
    }

    @Test
    void afficheEtCorrigeUneLigneCreeeAvantLesAssociations() throws Exception {
        jdbc.update("insert into reservation (date_debut, date_fin, statut) values (?, ?, ?)",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), "EN_ATTENTE");
        jdbc.update("insert into contrat (date_signature, montant_total, valide) values (?, ?, ?)",
                LocalDate.of(2026, 1, 1), new BigDecimal("10.00"), true);
        jdbc.update("insert into paiement (montant, date_paiement, mode_paiement) values (?, ?, ?)",
                new BigDecimal("5.00"), LocalDate.of(2026, 1, 1), "CARTE");
        Long idResa = reservations.findAll().get(0).getIdReservation();
        Long idContrat = contrats.findAll().get(0).getIdContrat();
        Long idPaiement = paiements.findAll().get(0).getIdPaiement();

        for (String url : new String[]{"/", "/tableau-de-bord", "/reservations", "/contrats", "/paiements",
                "/reservations/" + idResa, "/reservations/" + idResa + "/modifier",
                "/contrats/" + idContrat, "/contrats/" + idContrat + "/modifier",
                "/paiements/" + idPaiement, "/paiements/" + idPaiement + "/modifier",
                "/contrats/nouveau", "/paiements/nouveau"}) {
            mockMvc.perform(get(url)).andExpect(status().isOk());
        }

        mockMvc.perform(post("/reservations/" + idResa)
                        .param("dateDebut", "2026-01-01")
                        .param("dateFin", "2026-01-02")
                        .param("statut", "EN_ATTENTE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ce champ est obligatoire.")));
    }

    @Test
    void rechercheEtPagineLesListes() throws Exception {
        for (int i = 10; i < 30; i++) {
            vehicules.save(vehicule("AA-0" + i + "-AA"));
        }
        vehicules.save(new Vehicule("ZZ-000-ZZ", "Dacia", "Spring", SUV, new BigDecimal("40.00"),
                StatutVehicule.DISPONIBLE, agence));

        mockMvc.perform(get("/vehicules"))
                .andExpect(content().string(containsString("Page 1 sur 2")))
                .andExpect(content().string(containsString("Dacia")))
                .andExpect(content().string(containsString("page=1")));
        mockMvc.perform(get("/vehicules").param("page", "1"))
                .andExpect(content().string(containsString("Page 2 sur 2")));
        mockMvc.perform(get("/vehicules").param("q", "  daCIA "))
                .andExpect(content().string(containsString("ZZ-000-ZZ")))
                .andExpect(content().string(not(containsString("AA-010-AA"))));
        mockMvc.perform(get("/vehicules").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AA-")));
        mockMvc.perform(get("/vehicules").param("q", "introuvable"))
                .andExpect(content().string(containsString("Aucun résultat pour cette recherche.")));
        mockMvc.perform(get("/vehicules").param("page", "-3")).andExpect(status().isOk());
    }

    @Test
    void repondIntrouvablePourUnIdInconnu() throws Exception {
        mockMvc.perform(get("/vehicules/999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/vehicules/999999/modifier")).andExpect(status().isNotFound());
        mockMvc.perform(post("/vehicules/999999/supprimer")).andExpect(status().isNotFound());
    }

    @Test
    void nAffichePasDeFormulaireDeSuppressionEnGet() throws Exception {
        Vehicule vehicule = vehicules.save(vehicule("AA-111-AA"));

        mockMvc.perform(get("/vehicules/" + vehicule.getIdVehicule() + "/supprimer"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/vehicules"))
                .andExpect(content().string(not(containsString("Aucun"))));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reservation(
            Client client, Vehicule vehicule, String debut, String fin, String statut) {
        return post("/reservations")
                .param("client", client.getIdClient().toString())
                .param("vehicule", vehicule.getIdVehicule().toString())
                .param("dateDebut", debut)
                .param("dateFin", fin)
                .param("statut", statut);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder paiement(
            String idContrat, String montant) {
        return post("/paiements")
                .param("contrat", idContrat)
                .param("montant", montant)
                .param("datePaiement", "2026-12-01")
                .param("modePaiement", "CARTE");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).with(csrf());
    }

    private static Client client(String email, String permis) {
        return new Client(null, "Durand", "Alice", email, "0600000000", permis, LocalDate.of(2026, 1, 1));
    }

    private Vehicule vehicule(String immatriculation) {
        return new Vehicule(immatriculation, "Peugeot", "3008", SUV,
                new BigDecimal("70.00"), StatutVehicule.DISPONIBLE, agence);
    }
}
