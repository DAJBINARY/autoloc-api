package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.service.PaiementService;
import fr.ece.autoloc.service.StatistiquesService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatistiquesController {

    private final StatistiquesService statistiques;
    private final PaiementService paiements;

    public StatistiquesController(StatistiquesService statistiques, PaiementService paiements) {
        this.statistiques = statistiques;
        this.paiements = paiements;
    }

    @GetMapping("/statistiques")
    String statistiques(Model model) {
        model.addAttribute("totalEncaisse", paiements.totalEncaisse());
        model.addAttribute("parMois", statistiques.encaisseParMois(12));
        model.addAttribute("parMode", statistiques.encaisseParMode());
        model.addAttribute("parStatut", statistiques.reservationsParStatut());
        model.addAttribute("vehicules", statistiques.vehiculesLesPlusReserves(5));
        return "statistiques";
    }
}
