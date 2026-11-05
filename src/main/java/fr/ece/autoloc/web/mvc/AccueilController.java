package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class AccueilController {

    private final VehiculeService vehicules;
    private final ReservationService reservations;
    private final PaiementService paiements;
    private final List<Section> sections;

    public AccueilController(VehiculeService vehicules, ClientService clients, AgenceService agences,
                             EmployeService employes, ReservationService reservations, ContratService contrats,
                             PaiementService paiements, MaintenanceService maintenances,
                             EquipementService equipements) {
        this.vehicules = vehicules;
        this.reservations = reservations;
        this.paiements = paiements;
        this.sections = List.of(
                new Section("Véhicules", "/vehicules", vehicules),
                new Section("Clients", "/clients", clients),
                new Section("Agences", "/agences", agences),
                new Section("Employés", "/employes", employes),
                new Section("Réservations", "/reservations", reservations),
                new Section("Contrats", "/contrats", contrats),
                new Section("Paiements", "/paiements", paiements),
                new Section("Maintenances", "/maintenances", maintenances),
                new Section("Équipements", "/equipements", equipements));
    }

    @GetMapping("/tableau-de-bord")
    String accueil(Model model) {
        model.addAttribute("vehiculesDisponibles", vehicules.compterParStatut(StatutVehicule.DISPONIBLE));
        model.addAttribute("vehiculesTotal", vehicules.compter());
        model.addAttribute("locationsEnCours", reservations.compterLocationsEnCours());
        model.addAttribute("reservationsEnAttente", reservations.compterParStatut(StatutReservation.EN_ATTENTE));
        model.addAttribute("totalEncaisse", paiements.totalEncaisse());
        model.addAttribute("prochainsDeparts", reservations.prochainsDeparts());
        model.addAttribute("cartes", sections.stream()
                .map(section -> new Carte(section.titre(), section.lien(), section.service().compter()))
                .toList());
        return "accueil";
    }

    private record Section(String titre, String lien, CrudService<?> service) {
    }

    public record Carte(String titre, String lien, long nombre) {
    }
}
