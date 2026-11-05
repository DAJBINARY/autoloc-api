package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.service.EquipementService;
import fr.ece.autoloc.service.VehiculeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/equipements")
public class EquipementController extends CrudController<Equipement> {

    private final VehiculeService vehicules;

    public EquipementController(EquipementService service, VehiculeService vehicules) {
        super(service, "equipements", Equipement::new, Equipement::setIdEquipement);
        this.vehicules = vehicules;
    }

    @Override
    protected void completerFiche(Equipement objet, Model model) {
        model.addAttribute("vehicules", vehicules.listerParEquipement(objet.getIdEquipement()));
    }
}
