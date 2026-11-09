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

    /** Cards of the dashboard, in display order. */
    private static final List<Libelle> LIBELLES = List.of(
            new Libelle("Véhicules", "/vehicules", VehiculeService.class),
            new Libelle("Clients", "/clients", ClientService.class),
            new Libelle("Agences", "/agences", AgenceService.class),
            new Libelle("Employés", "/employes", EmployeService.class),
            new Libelle("Réservations", "/reservations", ReservationService.class),
            new Libelle("Contrats", "/contrats", ContratService.class),
            new Libelle("Paiements", "/paiements", PaiementService.class),
            new Libelle("Maintenances", "/maintenances", MaintenanceService.class),
            new Libelle("Équipements", "/equipements", EquipementService.class));

    /** Every CRUD service is injected as a list, so the constructor does not grow with each entity. */
    public AccueilController(VehiculeService vehicules, ReservationService reservations, PaiementService paiements,
                             List<CrudService<?>> services) {
        this.vehicules = vehicules;
        this.reservations = reservations;
        this.paiements = paiements;
        this.sections = LIBELLES.stream()
                .map(libelle -> new Section(libelle.titre(), libelle.lien(), services.stream()
                        .filter(libelle.type()::isInstance)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Service absent : " + libelle.type()))))
                .toList();
    }

    @GetMapping("/tableau-de-bord")
    String accueil(Model model) {
        model.addAttribute("vehiculesDisponibles", vehicules.compterParStatut(StatutVehicule.DISPONIBLE));
        model.addAttribute("vehiculesLoues", vehicules.compterParStatut(StatutVehicule.LOUE));
        model.addAttribute("vehiculesEnMaintenance", vehicules.compterParStatut(StatutVehicule.MAINTENANCE));
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

    private record Libelle(String titre, String lien, Class<?> type) {
    }

    private record Section(String titre, String lien, CrudService<?> service) {
    }

    public record Carte(String titre, String lien, long nombre) {
    }
}
