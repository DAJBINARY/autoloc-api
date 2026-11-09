package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.service.ClientService;
import fr.ece.autoloc.service.CompteService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * The signed-in client's profile and password.
 */
@Controller
@RequestMapping("/espace")
public class EspaceProfilController {

    private static final String VUE_PROFIL = "espace/profil";
    private static final String REDIRECTION_PROFIL = "redirect:/espace/profil";

    private final ClientService clients;
    private final CompteService comptes;

    public EspaceProfilController(ClientService clients, CompteService comptes) {
        this.clients = clients;
        this.comptes = comptes;
    }

    @GetMapping("/profil")
    String profil(Principal utilisateur, Model model) {
        Client client = clientConnecte(utilisateur);
        ProfilForm form = new ProfilForm();
        form.setPrenom(client.getPrenom());
        form.setNom(client.getNom());
        form.setTelephone(client.getTelephone());
        model.addAttribute("form", form);
        model.addAttribute(Attributs.CLIENT, client);
        return VUE_PROFIL;
    }

    @PostMapping("/profil")
    String enregistrerProfil(@Valid @ModelAttribute("form") ProfilForm form, BindingResult erreurs, Principal utilisateur,
                             Model model, RedirectAttributes redirection) {
        Client client = clientConnecte(utilisateur);
        if (erreurs.hasErrors()) {
            model.addAttribute(Attributs.CLIENT, client);
            return VUE_PROFIL;
        }
        client.setPrenom(form.getPrenom().strip());
        client.setNom(form.getNom().strip());
        client.setTelephone(form.getTelephone().strip());
        clients.enregistrer(client);
        redirection.addFlashAttribute(Attributs.SUCCES, "Profil enregistré.");
        return REDIRECTION_PROFIL;
    }

    @PostMapping("/mot-de-passe")
    String changerMotDePasse(@RequestParam String actuel, @RequestParam String nouveau, @RequestParam String confirmation,
                             Principal utilisateur, RedirectAttributes redirection) {
        if (nouveau.length() < 8 || nouveau.length() > 72) {
            redirection.addFlashAttribute(Attributs.ERREUR, "Le nouveau mot de passe doit contenir entre 8 et 72 caractères.");
        } else if (!nouveau.equals(confirmation)) {
            redirection.addFlashAttribute(Attributs.ERREUR, "Les deux nouveaux mots de passe sont différents.");
        } else if (!comptes.changerMotDePasseClient(utilisateur.getName(), actuel, nouveau)) {
            redirection.addFlashAttribute(Attributs.ERREUR, "Le mot de passe actuel est incorrect.");
        } else {
            redirection.addFlashAttribute(Attributs.SUCCES, "Mot de passe modifié.");
        }
        return REDIRECTION_PROFIL;
    }

    private Client clientConnecte(Principal utilisateur) {
        return clients.trouverParEmail(utilisateur.getName())
                .orElseThrow(() -> new AccessDeniedException("Compte client introuvable"));
    }
}
