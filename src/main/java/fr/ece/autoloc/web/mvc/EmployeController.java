package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.domain.RoleEmploye;
import fr.ece.autoloc.service.AgenceService;
import fr.ece.autoloc.service.CompteService;
import fr.ece.autoloc.service.ContratService;
import fr.ece.autoloc.service.EmployeService;
import fr.ece.autoloc.service.RegleMetierException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Objects;

@Controller
@RequestMapping("/employes")
public class EmployeController extends CrudController<Employe> {

    private final EmployeService service;
    private final AgenceService agences;
    private final ContratService contrats;
    private final CompteService comptes;

    public EmployeController(EmployeService service, AgenceService agences, ContratService contrats,
                             CompteService comptes) {
        super(service, "employes", Employe::new, Employe::setIdEmploye);
        this.service = service;
        this.agences = agences;
        this.contrats = contrats;
        this.comptes = comptes;
    }

    @ModelAttribute("roles")
    RoleEmploye[] roles() {
        return RoleEmploye.values();
    }

    @Override
    protected void validerMetier(Employe objet, Errors erreurs) {
        Employe existant = objet.getIdEmploye() == null ? null : charger(objet.getIdEmploye());
        // A manager always stays able to sign in with their rights, so at least one manager is always left.
        if (existant != null && estUtilisateurConnecte(existant)
                && (objet.getRole() != existant.getRole() || !Objects.equals(objet.getIdentifiant(), existant.getIdentifiant()))) {
            erreurs.rejectValue("role", "soiMeme", "Vous ne pouvez pas changer votre propre rôle ni votre identifiant.");
        }
        boolean identifiantChange = existant == null || !Objects.equals(objet.getIdentifiant(), existant.getIdentifiant());
        if (objet.getIdentifiant() != null && identifiantChange && comptes.identifiantPris(objet.getIdentifiant())) {
            erreurs.rejectValue("identifiant", "doublon", "Cet identifiant est déjà utilisé.");
        }
        if (objet.getIdentifiant() == null) {
            if (objet.getNouveauMotDePasse() != null) {
                erreurs.rejectValue("identifiant", "manquant", "Choisissez un identifiant pour ce mot de passe.");
            }
            return;
        }
        if (objet.getNouveauMotDePasse() != null) {
            objet.changerMotDePasse(comptes.encoder(objet.getNouveauMotDePasse()));
        } else if (existant != null && existant.aUnCompte()) {
            objet.changerMotDePasse(existant.getMotDePasse());
        } else {
            erreurs.rejectValue("nouveauMotDePasse", "manquant", "Choisissez un mot de passe pour ce compte.");
        }
    }

    @Override
    protected void verifierSuppression(Employe objet) {
        if (estUtilisateurConnecte(objet)) {
            throw new RegleMetierException("Vous ne pouvez pas supprimer votre propre compte.");
        }
    }

    @Override
    protected void preparerFormulaire(Employe objet, Model model) {
        model.addAttribute("agences", agences.lister());
    }

    @Override
    protected void completerFiche(Employe objet, Model model) {
        model.addAttribute("contrats", contrats.listerParEmploye(objet.getIdEmploye()));
    }

    private static boolean estUtilisateurConnecte(Employe employe) {
        return employe.getIdentifiant() != null
                && employe.getIdentifiant().equals(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
