package fr.ece.autoloc.unitaire.service;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import fr.ece.autoloc.service.CompteService;
import fr.ece.autoloc.service.RegleMetierException;
import fr.ece.autoloc.support.Donnees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompteServiceTest {

    private static final String EMAIL = "alice@example.com";

    @Mock
    private EmployeRepository employes;
    @Mock
    private ClientRepository clients;
    @Mock
    private AgenceRepository agences;
    @Mock
    private PasswordEncoder encodeur;
    @InjectMocks
    private CompteService service;

    private final Agence agence = Donnees.agence(1L);

    private Employe employe(RoleEmploye role) {
        Employe employe = new Employe(1L, "Martin", "Sophie", role, agence);
        employe.setIdentifiant("sophie");
        employe.changerMotDePasse("hash");
        return employe;
    }

    private static String droit(UserDetails utilisateur) {
        return utilisateur.getAuthorities().stream().map(GrantedAuthority::getAuthority).findFirst().orElseThrow();
    }

    @Test
    void connecteUnEmployeAvecLeRoleDeSaFonction() {
        when(employes.findByIdentifiant("sophie")).thenReturn(Optional.of(employe(RoleEmploye.MANAGER)));

        UserDetails utilisateur = service.loadUserByUsername("sophie");

        assertThat(utilisateur.getUsername()).isEqualTo("sophie");
        assertThat(utilisateur.getPassword()).isEqualTo("hash");
        assertThat(droit(utilisateur)).isEqualTo("ROLE_MANAGER");
    }

    @Test
    void connecteUnClientParSonEmailQuelleQueSoitSaCasse() {
        Client client = Donnees.client(1L, EMAIL, "P1");
        client.changerMotDePasse("hash");
        when(employes.findByIdentifiant(" Alice@Example.COM ")).thenReturn(Optional.empty());
        when(clients.findByEmail(EMAIL)).thenReturn(Optional.of(client));

        UserDetails utilisateur = service.loadUserByUsername(" Alice@Example.COM ");

        assertThat(utilisateur.getUsername()).isEqualTo(EMAIL);
        assertThat(droit(utilisateur)).isEqualTo("ROLE_CLIENT");
    }

    @Test
    void refuseUnEmployeSansMotDePasse() {
        Employe sansCompte = new Employe(1L, "Martin", "Sophie", RoleEmploye.AGENT, agence);
        when(employes.findByIdentifiant("sophie")).thenReturn(Optional.of(sansCompte));
        when(clients.findByEmail("sophie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("sophie")).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void refuseUnClientCreeParLAgenceSansCompte() {
        when(employes.findByIdentifiant(EMAIL)).thenReturn(Optional.empty());
        when(clients.findByEmail(EMAIL)).thenReturn(Optional.of(Donnees.client(1L, EMAIL, "P1")));

        assertThatThrownBy(() -> service.loadUserByUsername(EMAIL)).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void unIdentifiantEstPrisParUnEmployeOuParUnClient() {
        when(employes.findByIdentifiant("sophie")).thenReturn(Optional.of(employe(RoleEmploye.AGENT)));
        assertThat(service.identifiantPris("sophie")).isTrue();

        when(employes.findByIdentifiant("ALICE@example.com")).thenReturn(Optional.empty());
        when(clients.existsByEmail(EMAIL)).thenReturn(true);
        assertThat(service.identifiantPris("ALICE@example.com")).isTrue();

        when(employes.findByIdentifiant("libre")).thenReturn(Optional.empty());
        when(clients.existsByEmail("libre")).thenReturn(false);
        assertThat(service.identifiantPris("libre")).isFalse();
    }

    @Test
    void inscritUnClientAvecDesDonneesNormaliseesEtUnMotDePasseHache() {
        when(encodeur.encode("motdepasse1")).thenReturn("hash");

        service.inscrireClient(" Durand ", " Alice ", " Alice@Example.COM ", " 0600000000 ", " P1 ", "motdepasse1");

        ArgumentCaptor<Client> client = ArgumentCaptor.forClass(Client.class);
        verify(clients).save(client.capture());
        assertThat(client.getValue().getNom()).isEqualTo("Durand");
        assertThat(client.getValue().getPrenom()).isEqualTo("Alice");
        assertThat(client.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(client.getValue().getTelephone()).isEqualTo("0600000000");
        assertThat(client.getValue().getNumPermis()).isEqualTo("P1");
        assertThat(client.getValue().getMotDePasse()).isEqualTo("hash");
        assertThat(client.getValue().getDateInscription()).isEqualTo(LocalDate.now());
    }

    @Test
    void lePremierCompteEstManagerEtCreeSonAgence() {
        Agence nouvelle = Donnees.agence(null);
        Employe manager = new Employe();
        when(employes.existsByMotDePasseIsNotNull()).thenReturn(false);
        when(agences.save(nouvelle)).thenReturn(agence);
        when(encodeur.encode("motdepasse1")).thenReturn("hash");

        service.creerPremierCompte(manager, "motdepasse1", nouvelle);

        verify(employes).save(manager);
        assertThat(manager.getRole()).isEqualTo(RoleEmploye.MANAGER);
        assertThat(manager.getAgence()).isSameAs(agence);
        assertThat(manager.getMotDePasse()).isEqualTo("hash");
    }

    @Test
    void lePremierCompteReutiliseUneAgenceExistante() {
        when(employes.existsByMotDePasseIsNotNull()).thenReturn(false);
        when(encodeur.encode("motdepasse1")).thenReturn("hash");

        service.creerPremierCompte(new Employe(), "motdepasse1", agence);

        verify(agences, never()).save(any());
    }

    @Test
    void refuseUnSecondPremierCompte() {
        when(employes.existsByMotDePasseIsNotNull()).thenReturn(true);

        assertThatThrownBy(() -> service.creerPremierCompte(new Employe(), "motdepasse1", agence))
                .isInstanceOf(RegleMetierException.class);
        verify(employes, never()).save(any());
    }

    @Test
    void changeLeMotDePasseDUnEmployeAvecLeBonMotDePasseActuel() {
        Employe employe = employe(RoleEmploye.AGENT);
        when(employes.findByIdentifiant("sophie")).thenReturn(Optional.of(employe));
        when(encodeur.matches("actuel", "hash")).thenReturn(true);
        when(encodeur.encode("nouveau")).thenReturn("nouveau-hash");

        assertThat(service.changerMotDePasse("sophie", "actuel", "nouveau")).isTrue();
        assertThat(employe.getMotDePasse()).isEqualTo("nouveau-hash");
    }

    @Test
    void gardeLeMotDePasseDUnEmployeSiLActuelEstFaux() {
        Employe employe = employe(RoleEmploye.AGENT);
        when(employes.findByIdentifiant("sophie")).thenReturn(Optional.of(employe));
        when(encodeur.matches("faux", "hash")).thenReturn(false);

        assertThat(service.changerMotDePasse("sophie", "faux", "nouveau")).isFalse();
        assertThat(employe.getMotDePasse()).isEqualTo("hash");
    }

    @Test
    void changeLeMotDePasseDUnClientSeulementAvecLeBonMotDePasseActuel() {
        Client client = Donnees.client(1L, EMAIL, "P1");
        client.changerMotDePasse("hash");
        when(clients.findByEmail(EMAIL)).thenReturn(Optional.of(client));
        when(encodeur.matches("faux", "hash")).thenReturn(false);
        when(encodeur.matches("actuel", "hash")).thenReturn(true);
        when(encodeur.encode("nouveau")).thenReturn("nouveau-hash");

        assertThat(service.changerMotDePasseClient(EMAIL, "faux", "nouveau")).isFalse();
        assertThat(client.getMotDePasse()).isEqualTo("hash");

        assertThat(service.changerMotDePasseClient(EMAIL, "actuel", "nouveau")).isTrue();
        assertThat(client.getMotDePasse()).isEqualTo("nouveau-hash");
    }
}
