package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.service.CompteService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class InscriptionController {

    private static final String VUE_INSCRIPTION = "site/inscription";
    private static final String CODE_DOUBLON = "doublon";

    private final CompteService comptes;
    private final HttpSessionRequestCache pageDemandee = new HttpSessionRequestCache();

    public InscriptionController(CompteService comptes) {
        this.comptes = comptes;
    }

    @GetMapping("/inscription")
    String formulaire(Model model) {
        model.addAttribute("form", new InscriptionForm());
        return VUE_INSCRIPTION;
    }

    @PostMapping("/inscription")
    String inscrire(@Valid @ModelAttribute("form") InscriptionForm form, BindingResult erreurs,
                    HttpServletRequest requete, HttpServletResponse reponse) {
        if (form.getMotDePasse() != null && !form.getMotDePasse().equals(form.getConfirmation())) {
            erreurs.rejectValue("confirmation", "different", "Les deux mots de passe sont différents.");
        }
        if (form.getEmail() != null && comptes.identifiantPris(form.getEmail())) {
            erreurs.rejectValue("email", CODE_DOUBLON, "Cette adresse e-mail est déjà utilisée.");
        }
        if (form.getNumPermis() != null && comptes.permisPris(form.getNumPermis().strip())) {
            erreurs.rejectValue("numPermis", CODE_DOUBLON, "Ce numéro de permis est déjà enregistré.");
        }
        if (erreurs.hasErrors()) {
            return VUE_INSCRIPTION;
        }
        try {
            comptes.inscrireClient(form.getNom(), form.getPrenom(), form.getEmail(), form.getTelephone(),
                    form.getNumPermis(), form.getMotDePasse());
        } catch (DataIntegrityViolationException e) {
            erreurs.reject(CODE_DOUBLON, "Ce compte existe déjà.");
            return VUE_INSCRIPTION;
        }
        try {
            requete.login(CompteService.normaliserEmail(form.getEmail()), form.getMotDePasse());
        } catch (ServletException e) {
            return "redirect:/connexion";
        }
        SavedRequest demandee = pageDemandee.getRequest(requete, reponse);
        if (demandee != null) {
            pageDemandee.removeRequest(requete, reponse);
            return "redirect:" + demandee.getRedirectUrl();
        }
        return "redirect:/espace";
    }
}
