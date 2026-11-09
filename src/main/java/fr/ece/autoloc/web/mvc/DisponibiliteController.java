package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.service.AgenceService;
import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.service.VehiculeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Which vehicles can be rented on given dates, with the price of the rental.
 */
@Controller
public class DisponibiliteController {

    private final VehiculeService vehicules;
    private final ReservationService reservations;
    private final AgenceService agences;

    public DisponibiliteController(VehiculeService vehicules, ReservationService reservations, AgenceService agences) {
        this.vehicules = vehicules;
        this.reservations = reservations;
        this.agences = agences;
    }

    @GetMapping("/disponibilites")
    String disponibilites(@RequestParam(required = false) LocalDate debut, @RequestParam(required = false) LocalDate fin,
                          @RequestParam(required = false) CategorieVehicule categorie,
                          @RequestParam(required = false) Long agence, Model model) {
        model.addAttribute("debut", debut);
        model.addAttribute("fin", fin);
        model.addAttribute("categorie", categorie);
        model.addAttribute("agence", agence);
        model.addAttribute("categories", CategorieVehicule.values());
        model.addAttribute("agences", agences.lister());
        if (debut != null && fin != null) {
            if (fin.isBefore(debut)) {
                model.addAttribute("erreur", "La date de fin doit être postérieure ou égale à la date de début.");
            } else {
                model.addAttribute("resultats", vehicules.listerDisponibles(debut, fin).stream()
                        .filter(vehicule -> categorie == null || vehicule.getCategorie() == categorie)
                        .filter(vehicule -> agence == null
                                || (vehicule.getAgence() != null && agence.equals(vehicule.getAgence().getIdAgence())))
                        .map(vehicule -> new Resultat(vehicule, reservations.estimer(vehicule.getTarifJournalier(), debut, fin)))
                        .toList());
            }
        }
        return "disponibilites";
    }

    public record Resultat(Vehicule vehicule, BigDecimal montant) {
    }
}
