package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.repository.AgenceRepository;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.EmployeRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * Login accounts. Staff sign in with their login and the rights of their role; clients with their e-mail.
 */
@Service
public class CompteService implements UserDetailsService {

    private final EmployeRepository employes;
    private final ClientRepository clients;
    private final AgenceRepository agences;
    private final PasswordEncoder encodeur;

    public CompteService(EmployeRepository employes, ClientRepository clients, AgenceRepository agences,
                         PasswordEncoder encodeur) {
        this.employes = employes;
        this.clients = clients;
        this.agences = agences;
        this.encodeur = encodeur;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifiant) {
        Optional<Employe> employe = employes.findByIdentifiant(identifiant).filter(Employe::aUnCompte);
        if (employe.isPresent()) {
            return User.withUsername(employe.get().getIdentifiant())
                    .password(employe.get().getMotDePasse())
                    .roles(employe.get().getRole().name())
                    .build();
        }
        Client client = clients.findByEmail(normaliserEmail(identifiant))
                .filter(Client::aUnCompte)
                .orElseThrow(() -> new UsernameNotFoundException(identifiant));
        return User.withUsername(client.getEmail()).password(client.getMotDePasse()).roles("CLIENT").build();
    }

    public static String normaliserEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** True when an e-mail or login cannot be taken by a new account, whichever kind of account holds it. */
    @Transactional(readOnly = true)
    public boolean identifiantPris(String identifiant) {
        return employes.findByIdentifiant(identifiant).isPresent()
                || clients.existsByEmail(normaliserEmail(identifiant));
    }

    @Transactional(readOnly = true)
    public boolean permisPris(String numPermis) {
        return clients.existsByNumPermis(numPermis);
    }

    @Transactional
    public void inscrireClient(String nom, String prenom, String email, String telephone, String numPermis, String motDePasse) {
        Client client = new Client(null, nom.strip(), prenom.strip(), normaliserEmail(email), telephone.strip(),
                numPermis.strip(), LocalDate.now());
        client.changerMotDePasse(encodeur.encode(motDePasse));
        clients.save(client);
    }

    @Transactional(readOnly = true)
    public boolean compteExiste() {
        return employes.existsByMotDePasseIsNotNull();
    }

    public String encoder(String motDePasse) {
        return encodeur.encode(motDePasse);
    }

    @Transactional
    public void creerPremierCompte(Employe manager, String motDePasse, Agence agence) {
        if (compteExiste()) {
            throw new RegleMetierException("Un compte existe déjà : connectez-vous.");
        }
        manager.setRole(RoleEmploye.MANAGER);
        manager.setAgence(agence.getIdAgence() == null ? agences.save(agence) : agence);
        manager.changerMotDePasse(encodeur.encode(motDePasse));
        employes.save(manager);
    }

    /** Returns false when the current password is wrong. */
    @Transactional
    public boolean changerMotDePasse(String identifiant, String actuel, String nouveau) {
        Employe employe = employes.findByIdentifiant(identifiant).orElseThrow();
        if (!encodeur.matches(actuel, employe.getMotDePasse())) {
            return false;
        }
        employe.changerMotDePasse(encodeur.encode(nouveau));
        return true;
    }

    /** Returns false when the current password is wrong. */
    @Transactional
    public boolean changerMotDePasseClient(String email, String actuel, String nouveau) {
        Client client = clients.findByEmail(email).orElseThrow();
        if (!encodeur.matches(actuel, client.getMotDePasse())) {
            return false;
        }
        client.changerMotDePasse(encodeur.encode(nouveau));
        return true;
    }
}
