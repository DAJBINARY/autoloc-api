package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.service.MaintenanceService;
import fr.ece.autoloc.service.VehiculeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/maintenances")
public class MaintenanceController extends CrudController<Maintenance> {

    private final MaintenanceService service;
    private final VehiculeService vehicules;

    public MaintenanceController(MaintenanceService service, VehiculeService vehicules) {
        super(service, "maintenances", Maintenance::new, Maintenance::setIdMaintenance);
        this.service = service;
        this.vehicules = vehicules;
    }

    @PostMapping("/{id}/cloturer")
    String cloturer(@PathVariable Long id, RedirectAttributes redirection) {
        return action(id, service::cloturer, "Maintenance clôturée.", redirection);
    }

    @Override
    protected void validerMetier(Maintenance objet, Errors erreurs) {
        if (objet.getDateDebut() != null && objet.getDateFin() != null
                && objet.getDateFin().isBefore(objet.getDateDebut())) {
            erreurs.rejectValue("dateFin", "dates", "La date de fin doit être postérieure ou égale à la date de début.");
        }
    }

    @Override
    protected void preparerFormulaire(Maintenance objet, Model model) {
        model.addAttribute("vehicules", vehicules.lister());
    }

    @Override
    protected void preremplir(Maintenance objet, Map<String, String> parametres) {
        idDe(parametres, "vehicule").flatMap(vehicules::trouver).ifPresent(objet::setVehicule);
    }
}
