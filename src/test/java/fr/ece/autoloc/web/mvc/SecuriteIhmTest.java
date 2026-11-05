package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.EmployeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securite;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class SecuriteIhmTest {

    private static final String MOT_DE_PASSE = "motdepasse1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeRepository employes;
    @Autowired
    private AgenceRepository agences;
    @Autowired
    private PasswordEncoder encodeur;

    private Agence agence;

    @BeforeEach
    void viderLaBase() {
        employes.deleteAll();
        agences.deleteAll();
        agence = agences.save(new Agence(null, "Paris", "Paris", "1 rue", "0100000000"));
    }

    @Test
    void redirigeVersLaConnexionSansCompteConnecte() throws Exception {
        mockMvc.perform(get("/vehicules")).andExpect(redirectedUrl("/connexion"));
        mockMvc.perform(get("/connexion")).andExpect(status().isOk());
        mockMvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void refuseUnFormulaireSansJetonCsrf() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/vehicules")).andExpect(status().isForbidden());
    }

    @Test
    void connecteUnEmployeAvecSonMotDePasse() throws Exception {
        compte("sophie", RoleEmploye.AGENT);

        mockMvc.perform(formLogin("/connexion").user("identifiant", "sophie").password("motDePasse", MOT_DE_PASSE))
                .andExpect(authenticated().withUsername("sophie").withRoles("AGENT"));
        mockMvc.perform(formLogin("/connexion").user("identifiant", "sophie").password("motDePasse", "mauvais"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/connexion?error"));
    }

    @Test
    void refuseLaConnexionDUnEmployeSansCompte() throws Exception {
        employes.save(new Employe(null, "Sans", "Compte", RoleEmploye.AGENT, agence));

        mockMvc.perform(formLogin("/connexion").user("identifiant", "Sans").password("motDePasse", MOT_DE_PASSE))
                .andExpect(unauthenticated());
    }

    @Test
    void creeLePremierCompteUneSeuleFois() throws Exception {
        mockMvc.perform(get("/connexion"))
                .andExpect(content().string(containsString("Créer le premier compte")));
        mockMvc.perform(get("/premiere-connexion")).andExpect(status().isOk());

        mockMvc.perform(post("/premiere-connexion")
                        .param("prenom", "Ada").param("nom", "Lovelace")
                        .param("identifiant", "ada").param("motDePasse", "court"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Entre 8 et 72")));

        mockMvc.perform(post("/premiere-connexion")
                        .param("prenom", "Ada").param("nom", "Lovelace")
                        .param("identifiant", "ada").param("motDePasse", MOT_DE_PASSE)
                        .param("idAgence", agence.getIdAgence().toString()))
                .andExpect(redirectedUrl("/connexion"));

        assertThat(employes.findByIdentifiant("ada").orElseThrow().getRole()).isEqualTo(RoleEmploye.MANAGER);
        mockMvc.perform(formLogin("/connexion").user("identifiant", "ada").password("motDePasse", MOT_DE_PASSE))
                .andExpect(authenticated().withRoles("MANAGER"));

        mockMvc.perform(get("/premiere-connexion")).andExpect(redirectedUrl("/connexion"));
        mockMvc.perform(post("/premiere-connexion")
                        .param("prenom", "Pirate").param("nom", "Pirate")
                        .param("identifiant", "pirate").param("motDePasse", MOT_DE_PASSE)
                        .param("idAgence", agence.getIdAgence().toString()))
                .andExpect(redirectedUrl("/connexion"));
        assertThat(employes.findByIdentifiant("pirate")).isEmpty();
    }

    @Test
    void creeLaPremiereAgenceAvecLePremierCompte() throws Exception {
        agences.deleteAll();

        mockMvc.perform(post("/premiere-connexion")
                        .param("prenom", "Ada").param("nom", "Lovelace")
                        .param("identifiant", "ada").param("motDePasse", MOT_DE_PASSE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ce champ est obligatoire.")));

        mockMvc.perform(post("/premiere-connexion")
                        .param("prenom", "Ada").param("nom", "Lovelace")
                        .param("identifiant", "ada").param("motDePasse", MOT_DE_PASSE)
                        .param("nomAgence", "Lyon").param("villeAgence", "Lyon")
                        .param("adresseAgence", "2 rue").param("telephoneAgence", "0400000000"))
                .andExpect(redirectedUrl("/connexion"));

        assertThat(agences.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "agent", roles = "AGENT")
    void limiteLesDroitsDUnAgent() throws Exception {
        Employe employe = compte("autre", RoleEmploye.AGENT);

        mockMvc.perform(get("/statistiques")).andExpect(status().isForbidden());
        mockMvc.perform(get("/agences/nouveau")).andExpect(status().isForbidden());
        mockMvc.perform(get("/employes/nouveau")).andExpect(status().isForbidden());
        mockMvc.perform(post("/agences").param("nom", "X")).andExpect(status().isForbidden());
        mockMvc.perform(post("/employes/" + employe.getIdEmploye() + "/supprimer")).andExpect(status().isForbidden());
        mockMvc.perform(post("/vehicules/1/supprimer")).andExpect(status().isForbidden());

        mockMvc.perform(get("/vehicules/nouveau")).andExpect(status().isOk());
        mockMvc.perform(get("/employes/" + employe.getIdEmploye()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/modifier"))));
        mockMvc.perform(get("/agences"))
                .andExpect(content().string(not(containsString("/supprimer"))))
                .andExpect(content().string(not(containsString("/agences/nouveau"))))
                .andExpect(content().string(not(containsString("/statistiques"))));
    }

    @Test
    @WithMockUser(username = "chef", roles = "MANAGER")
    void creeEtModifieLeCompteDUnEmployeSansPerdreSonMotDePasse() throws Exception {
        mockMvc.perform(employe("/employes", "sophie", "AGENT").param("nouveauMotDePasse", MOT_DE_PASSE))
                .andExpect(redirectedUrl("/employes"));
        Employe cree = employes.findByIdentifiant("sophie").orElseThrow();
        String hash = cree.getMotDePasse();
        assertThat(encodeur.matches(MOT_DE_PASSE, hash)).isTrue();

        mockMvc.perform(get("/employes/" + cree.getIdEmploye() + "/modifier"))
                .andExpect(content().string(not(containsString(hash))));

        mockMvc.perform(employe("/employes/" + cree.getIdEmploye(), "sophie", "MANAGER").param("motDePasse", "pirate"))
                .andExpect(redirectedUrl("/employes"));
        assertThat(employes.findByIdentifiant("sophie").orElseThrow().getMotDePasse()).isEqualTo(hash);

        mockMvc.perform(employe("/employes/" + cree.getIdEmploye(), "sophie", "MANAGER")
                        .param("nouveauMotDePasse", "unautre123"))
                .andExpect(redirectedUrl("/employes"));
        assertThat(encodeur.matches("unautre123", employes.findByIdentifiant("sophie").orElseThrow().getMotDePasse())).isTrue();
    }

    @Test
    @WithMockUser(username = "chef", roles = "MANAGER")
    void refuseUnCompteIncomplet() throws Exception {
        compte("pris", RoleEmploye.AGENT);

        mockMvc.perform(employe("/employes", "nouveau", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choisissez un mot de passe")));
        mockMvc.perform(employe("/employes", "pris", "AGENT").param("nouveauMotDePasse", MOT_DE_PASSE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("déjà utilisé")));
        mockMvc.perform(employe("/employes", null, "AGENT").param("nouveauMotDePasse", MOT_DE_PASSE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choisissez un identifiant")));
        mockMvc.perform(employe("/employes", "court", "AGENT").param("nouveauMotDePasse", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Entre 8 et 72")));
        mockMvc.perform(employe("/employes", null, "AGENT")).andExpect(redirectedUrl("/employes"));
    }

    @Test
    @WithMockUser(username = "chef", roles = "MANAGER")
    void unManagerNePeutNiSeRetrograderNiSeSupprimer() throws Exception {
        Employe moi = compte("chef", RoleEmploye.MANAGER);

        mockMvc.perform(employe("/employes/" + moi.getIdEmploye(), "chef", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("votre propre rôle")));
        mockMvc.perform(post("/employes/" + moi.getIdEmploye() + "/supprimer"))
                .andExpect(flash().attribute("erreur", "Vous ne pouvez pas supprimer votre propre compte."));

        assertThat(employes.existsById(moi.getIdEmploye())).isTrue();
        assertThat(employes.findByIdentifiant("chef").orElseThrow().getRole()).isEqualTo(RoleEmploye.MANAGER);
    }

    @Test
    @WithMockUser(username = "chef", roles = "MANAGER")
    void changeSonMotDePasse() throws Exception {
        compte("chef", RoleEmploye.MANAGER);

        mockMvc.perform(motDePasse("mauvais", "nouveau1234", "nouveau1234"))
                .andExpect(content().string(containsString("actuel est incorrect")));
        mockMvc.perform(motDePasse(MOT_DE_PASSE, "nouveau1234", "different1"))
                .andExpect(content().string(containsString("différents")));
        mockMvc.perform(motDePasse(MOT_DE_PASSE, "court", "court"))
                .andExpect(content().string(containsString("entre 8 et 72")));
        assertThat(encodeur.matches(MOT_DE_PASSE, employes.findByIdentifiant("chef").orElseThrow().getMotDePasse())).isTrue();

        mockMvc.perform(motDePasse(MOT_DE_PASSE, "nouveau1234", "nouveau1234"))
                .andExpect(redirectedUrl("/mon-compte"));
        assertThat(encodeur.matches("nouveau1234", employes.findByIdentifiant("chef").orElseThrow().getMotDePasse())).isTrue();
    }

    private Employe compte(String identifiant, RoleEmploye role) {
        Employe employe = new Employe(null, "Martin", "Sophie", role, agence);
        employe.setIdentifiant(identifiant);
        employe.changerMotDePasse(encodeur.encode(MOT_DE_PASSE));
        return employes.save(employe);
    }

    private MockHttpServletRequestBuilder employe(String url, String identifiant, String role) {
        MockHttpServletRequestBuilder requete = post(url)
                .param("nom", "Martin").param("prenom", "Sophie").param("role", role)
                .param("agence", agence.getIdAgence().toString());
        return identifiant == null ? requete : requete.param("identifiant", identifiant);
    }

    private static MockHttpServletRequestBuilder motDePasse(String actuel, String nouveau, String confirmation) {
        return post("/mon-compte").param("actuel", actuel).param("nouveau", nouveau).param("confirmation", confirmation);
    }

    private static MockHttpServletRequestBuilder post(String url) {
        return MockMvcRequestBuilders.post(url).with(csrf());
    }
}
