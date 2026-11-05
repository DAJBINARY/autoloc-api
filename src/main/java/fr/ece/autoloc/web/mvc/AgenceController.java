package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.service.AgenceService;
import fr.ece.autoloc.service.EmployeService;
import fr.ece.autoloc.service.VehiculeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/agences")
public class AgenceController extends CrudController<Agence> {

    private final VehiculeService vehicules;
    private final EmployeService employes;

    public AgenceController(AgenceService service, VehiculeService vehicules, EmployeService employes) {
        super(service, "agences", Agence::new, Agence::setIdAgence);
        this.vehicules = vehicules;
        this.employes = employes;
    }

    @Override
    protected void completerFiche(Agence objet, Model model) {
        model.addAttribute("vehicules", vehicules.listerParAgence(objet.getIdAgence()));
        model.addAttribute("employes", employes.listerParAgence(objet.getIdAgence()));
    }
}
