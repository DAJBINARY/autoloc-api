package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.service.AgenceService;
import fr.ece.autoloc.service.EquipementService;
import fr.ece.autoloc.service.MaintenanceService;
import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.service.VehiculeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/vehicules")
public class VehiculeController extends CrudController<Vehicule> {

    private final VehiculeService service;
    private final AgenceService agences;
    private final EquipementService equipements;
    private final ReservationService reservations;
    private final MaintenanceService maintenances;

    public VehiculeController(VehiculeService service, AgenceService agences, EquipementService equipements,
                              ReservationService reservations, MaintenanceService maintenances) {
        super(service, "vehicules", Vehicule::new, Vehicule::setIdVehicule);
        this.service = service;
        this.agences = agences;
        this.equipements = equipements;
        this.reservations = reservations;
        this.maintenances = maintenances;
    }

    @ModelAttribute("categories")
    CategorieVehicule[] categories() {
        return CategorieVehicule.values();
    }

    @ModelAttribute("statuts")
    StatutVehicule[] statuts() {
        return StatutVehicule.values();
    }

    @Override
    protected void validerMetier(Vehicule objet, Errors erreurs) {
        if (objet.getPlaceParking() != null && objet.getAgence() != null
                && service.placeOccupee(objet.getAgence().getIdAgence(), objet.getPlaceParking(), objet.getIdVehicule())) {
            erreurs.rejectValue("placeParking", "occupee", "Cette place est déjà occupée dans cette agence.");
        }
    }

    @Override
    protected void preparerFormulaire(Vehicule objet, Model model) {
        model.addAttribute("agences", agences.lister());
        model.addAttribute("equipementsDisponibles", equipements.lister());
    }

    @Override
    protected void completerFiche(Vehicule objet, Model model) {
        model.addAttribute("reservations", reservations.listerParVehicule(objet.getIdVehicule()));
        model.addAttribute("maintenances", maintenances.listerParVehicule(objet.getIdVehicule()));
    }
}
