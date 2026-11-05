package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import fr.ece.autoloc.repository.EquipementRepository;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every screen, rendered against the linked demo data of the dev profile. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@WithMockUser(username = "manager", roles = "MANAGER")
class DemoDataIhmTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AgenceRepository agences;
    @Autowired
    private ClientRepository clients;
    @Autowired
    private EmployeRepository employes;
    @Autowired
    private EquipementRepository equipements;
    @Autowired
    private VehiculeRepository vehicules;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private ContratRepository contrats;
    @Autowired
    private PaiementRepository paiements;
    @Autowired
    private MaintenanceRepository maintenances;

    @Test
    void afficheChaqueEcranAvecLesDonneesLiees() throws Exception {
        verifierSection("agences", agences.findAll().stream().map(a -> a.getIdAgence()).toList());
        verifierSection("clients", clients.findAll().stream().map(c -> c.getIdClient()).toList());
        verifierSection("employes", employes.findAll().stream().map(e -> e.getIdEmploye()).toList());
        verifierSection("equipements", equipements.findAll().stream().map(e -> e.getIdEquipement()).toList());
        verifierSection("vehicules", vehicules.findAll().stream().map(v -> v.getIdVehicule()).toList());
        verifierSection("reservations", reservations.findAll().stream().map(r -> r.getIdReservation()).toList());
        verifierSection("contrats", contrats.findAll().stream().map(c -> c.getIdContrat()).toList());
        verifierSection("paiements", paiements.findAll().stream().map(p -> p.getIdPaiement()).toList());
        verifierSection("maintenances", maintenances.findAll().stream().map(m -> m.getIdMaintenance()).toList());
        for (String url : List.of("/", "/catalogue", "/parking", "/disponibilites", "/planning", "/statistiques", "/mon-compte")) {
            mockMvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    void leTableauDeBordResumeLesDonnees() throws Exception {
        mockMvc.perform(get("/tableau-de-bord"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Prochains départs")))
                .andExpect(content().string(containsString("Karim Benali")))
                .andExpect(content().string(containsString("87,50 €")));
    }

    private void verifierSection(String section, List<Long> ids) throws Exception {
        assertThat(ids).as(section).isNotEmpty();
        mockMvc.perform(get("/" + section)).andExpect(status().isOk());
        mockMvc.perform(get("/" + section).param("q", "a")).andExpect(status().isOk());
        for (Long id : ids) {
            mockMvc.perform(get("/" + section + "/" + id)).andExpect(status().isOk());
            mockMvc.perform(get("/" + section + "/" + id + "/modifier")).andExpect(status().isOk());
        }
    }
}
