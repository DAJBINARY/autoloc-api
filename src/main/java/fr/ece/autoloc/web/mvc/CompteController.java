package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.service.AgenceService;
import fr.ece.autoloc.service.CompteService;
import fr.ece.autoloc.service.RegleMetierException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class CompteController {

    private final CompteService comptes;
    private final AgenceService agences;

    public CompteController(CompteService comptes, AgenceService agences) {
        this.comptes = comptes;
        this.agences = agences;
    }

    @GetMapping("/connexion")
    String connexion(Model model) {
        model.addAttribute("premierCompte", !comptes.compteExiste());
        return "compte/connexion";
    }

    @GetMapping("/premiere-connexion")
    String premiereConnexion(Model model) {
        if (comptes.compteExiste()) {
            return "redirect:/connexion";
        }
        model.addAttribute("form", new PremierCompteForm());
        model.addAttribute("agences", agences.lister());
        return "compte/premiere-connexion";
    }

    @PostMapping("/premiere-connexion")
    String creerPremierCompte(@Valid @ModelAttribute("form") PremierCompteForm form, BindingResult erreurs, Model model,
                              RedirectAttributes redirection) {
        if (comptes.compteExiste()) {
            return "redirect:/connexion";
        }
        Agence agence = agenceDe(form, erreurs);
        if (erreurs.hasErrors()) {
            model.addAttribute("agences", agences.lister());
            return "compte/premiere-connexion";
        }
        Employe manager = new Employe();
        manager.setNom(form.getNom().strip());
        manager.setPrenom(form.getPrenom().strip());
        manager.setIdentifiant(form.getIdentifiant().strip());
        try {
            comptes.creerPremierCompte(manager, form.getMotDePasse(), agence);
        } catch (RegleMetierException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
            return "redirect:/connexion";
        }
        redirection.addFlashAttribute("succes", "Compte créé : vous pouvez vous connecter.");
        return "redirect:/connexion";
    }

    private Agence agenceDe(PremierCompteForm form, BindingResult erreurs) {
        if (form.getIdAgence() != null) {
            Agence agence = agences.trouver(form.getIdAgence()).orElse(null);
            if (agence == null) {
                erreurs.rejectValue("idAgence", "inconnue", "Ce champ est obligatoire.");
            }
            return agence;
        }
        for (String[] champ : new String[][]{{"nomAgence", form.getNomAgence()}, {"villeAgence", form.getVilleAgence()},
                {"adresseAgence", form.getAdresseAgence()}, {"telephoneAgence", form.getTelephoneAgence()}}) {
            if (!StringUtils.hasText(champ[1])) {
                erreurs.rejectValue(champ[0], "obligatoire", "Ce champ est obligatoire.");
            }
        }
        return new Agence(null, form.getNomAgence(), form.getVilleAgence(), form.getAdresseAgence(), form.getTelephoneAgence());
    }

    @GetMapping("/mon-compte")
    String monCompte() {
        return "compte/mon-compte";
    }

    @PostMapping("/mon-compte")
    String changerMotDePasse(@RequestParam String actuel, @RequestParam String nouveau, @RequestParam String confirmation,
                             Principal utilisateur, Model model, RedirectAttributes redirection) {
        if (nouveau.length() < 8 || nouveau.length() > 72) {
            model.addAttribute("erreur", "Le nouveau mot de passe doit contenir entre 8 et 72 caractères.");
        } else if (!nouveau.equals(confirmation)) {
            model.addAttribute("erreur", "Les deux nouveaux mots de passe sont différents.");
        } else if (!comptes.changerMotDePasse(utilisateur.getName(), actuel, nouveau)) {
            model.addAttribute("erreur", "Le mot de passe actuel est incorrect.");
        } else {
            redirection.addFlashAttribute("succes", "Mot de passe modifié.");
            return "redirect:/mon-compte";
        }
        return "compte/mon-compte";
    }
}
