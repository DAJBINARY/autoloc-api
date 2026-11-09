package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.service.ParkingService;
import fr.ece.autoloc.service.ReservationClientService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * Shows the signed-in client where their vehicle is parked.
 */
@Controller
@RequestMapping("/espace")
public class EspaceParkingController {

    private final ReservationClientService reservations;
    private final ParkingService parking;

    public EspaceParkingController(ReservationClientService reservations, ParkingService parking) {
        this.reservations = reservations;
        this.parking = parking;
    }

    @GetMapping("/reservations/{id}/parking")
    String parking(@PathVariable Long id, Principal utilisateur, Model model) {
        // Someone else's reservation answers exactly like one that does not exist.
        Reservation reservation = reservations.trouver(id, utilisateur.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Vehicule vehicule = reservation.getVehicule();
        if (vehicule == null || vehicule.getAgence() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute(Attributs.RESERVATION, reservation);
        model.addAttribute(Attributs.VEHICULE, vehicule);
        model.addAttribute("places", parking.plan(vehicule.getAgence().getIdAgence(), vehicule.getIdVehicule()));
        return "espace/parking";
    }
}
