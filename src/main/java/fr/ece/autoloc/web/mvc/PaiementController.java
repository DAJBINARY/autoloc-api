package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.config.Horloge;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.service.ContratService;
import fr.ece.autoloc.service.PaiementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/paiements")
public class PaiementController extends CrudController<Paiement> {

    private final PaiementService service;
    private final ContratService contrats;

    public PaiementController(PaiementService service, ContratService contrats) {
        super(service, "paiements", PaiementController::nouveauAvecDate, Paiement::setIdPaiement);
        this.service = service;
        this.contrats = contrats;
    }

    @ModelAttribute("modes")
    ModePaiement[] modes() {
        return ModePaiement.values();
    }

    private static Paiement nouveauAvecDate() {
        Paiement paiement = new Paiement();
        paiement.setDatePaiement(LocalDate.now(Horloge.ZONE));
        return paiement;
    }

    @Override
    protected void validerMetier(Paiement objet, Errors erreurs) {
        if (objet.getContrat() == null || objet.getMontant() == null) {
            return;
        }
        BigDecimal dejaPaye = service.totalPourContrat(objet.getContrat().getIdContrat(), objet.getIdPaiement());
        if (dejaPaye.add(objet.getMontant()).compareTo(objet.getContrat().getMontantTotal()) > 0) {
            erreurs.rejectValue("montant", "depassement", "Ce paiement dépasse le reste à payer du contrat.");
        }
    }

    @Override
    protected void preparerFormulaire(Paiement objet, Model model) {
        model.addAttribute("contrats", contrats.lister());
    }

    @Override
    protected void preremplir(Paiement objet, Map<String, String> parametres) {
        idDe(parametres, "contrat").flatMap(contrats::trouver).ifPresent(objet::setContrat);
    }
}
