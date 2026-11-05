package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.service.AgenceService;
import fr.ece.autoloc.service.ParkingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class ParkingController {

    private final AgenceService agences;
    private final ParkingService parking;

    public ParkingController(AgenceService agences, ParkingService parking) {
        this.agences = agences;
        this.parking = parking;
    }

    @GetMapping("/parking")
    String parking(@RequestParam(required = false) Long agence, Model model) {
        List<Agence> toutes = agences.lister();
        Agence affichee = toutes.stream()
                .filter(candidate -> candidate.getIdAgence().equals(agence))
                .findFirst()
                .orElse(toutes.isEmpty() ? null : toutes.get(0));
        model.addAttribute("agences", toutes);
        model.addAttribute("agence", affichee);
        model.addAttribute("places", affichee == null ? List.of() : parking.plan(affichee.getIdAgence(), null));
        return "parking";
    }
}
