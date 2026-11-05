package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.service.ClientService;
import fr.ece.autoloc.service.ContratService;
import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.service.VehiculeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/reservations")
public class ReservationController extends CrudController<Reservation> {

    private final ReservationService service;
    private final ClientService clients;
    private final VehiculeService vehicules;
    private final ContratService contrats;

    public ReservationController(ReservationService service, ClientService clients, VehiculeService vehicules,
                                 ContratService contrats) {
        super(service, "reservations", Reservation::new, Reservation::setIdReservation);
        this.service = service;
        this.clients = clients;
        this.vehicules = vehicules;
        this.contrats = contrats;
    }

    @ModelAttribute("statuts")
    StatutReservation[] statuts() {
        return StatutReservation.values();
    }

    @PostMapping("/{id}/confirmer")
    String confirmer(@PathVariable Long id, RedirectAttributes redirection) {
        return action(id, service::confirmer, "Réservation confirmée.", redirection);
    }

    @PostMapping("/{id}/annuler")
    String annuler(@PathVariable Long id, RedirectAttributes redirection) {
        return action(id, service::annuler, "Réservation annulée.", redirection);
    }

    @PostMapping("/{id}/depart")
    String depart(@PathVariable Long id, RedirectAttributes redirection) {
        return action(id, service::enregistrerDepart, "Départ enregistré : le véhicule est loué.", redirection);
    }

    @PostMapping("/{id}/retour")
    String retour(@PathVariable Long id, RedirectAttributes redirection) {
        return action(id, service::enregistrerRetour, "Retour enregistré : la réservation est terminée.", redirection);
    }

    @Override
    protected String motifRefusSuppression(Reservation objet) {
        return objet.isEnCours() ? "Suppression impossible : enregistrez d'abord le retour du véhicule." : null;
    }

    @Override
    protected void validerMetier(Reservation objet, Errors erreurs) {
        if (objet.getIdReservation() != null) {
            Reservation existante = charger(objet.getIdReservation());
            objet.reprendreSuivi(existante);
            if (existante.isEnCours() && objet.getStatut() != existante.getStatut()) {
                erreurs.rejectValue("statut", "enCours", "Location en cours : enregistrez le retour du véhicule.");
            }
            if (existante.isEnCours() && (objet.getVehicule() == null
                    || !objet.getVehicule().getIdVehicule().equals(existante.getVehicule().getIdVehicule()))) {
                erreurs.rejectValue("vehicule", "enCours", "Location en cours : le véhicule ne peut plus changer.");
            }
        }
        if (objet.getDateDebut() == null || objet.getDateFin() == null) {
            return;
        }
        if (objet.getDateFin().isBefore(objet.getDateDebut())) {
            erreurs.rejectValue("dateFin", "dates", "La date de fin doit être postérieure ou égale à la date de début.");
        } else if (objet.getVehicule() != null && objet.getStatut() != StatutReservation.ANNULEE
                && service.vehiculeDejaReserve(objet.getVehicule().getIdVehicule(), objet.getDateDebut(),
                objet.getDateFin(), objet.getIdReservation())) {
            erreurs.rejectValue("vehicule", "chevauchement", "Ce véhicule est déjà réservé sur ces dates.");
        } else if (objet.getVehicule() != null && objet.getStatut() != StatutReservation.ANNULEE
                && service.vehiculeEnMaintenance(objet.getVehicule().getIdVehicule(), objet.getDateDebut(), objet.getDateFin())) {
            erreurs.rejectValue("vehicule", "maintenance", "Ce véhicule est en maintenance sur ces dates.");
        }
    }

    @Override
    protected void preparerFormulaire(Reservation objet, Model model) {
        model.addAttribute("clients", clients.lister());
        model.addAttribute("vehicules", vehicules.lister());
    }

    @Override
    protected void preremplir(Reservation objet, Map<String, String> parametres) {
        idDe(parametres, "client").flatMap(clients::trouver).ifPresent(objet::setClient);
        idDe(parametres, "vehicule").flatMap(vehicules::trouver).ifPresent(objet::setVehicule);
        dateDe(parametres, "debut").ifPresent(objet::setDateDebut);
        dateDe(parametres, "fin").ifPresent(objet::setDateFin);
    }

    @Override
    protected void completerFiche(Reservation objet, Model model) {
        model.addAttribute("contrat", contrats.trouverParReservation(objet.getIdReservation()).orElse(null));
    }
}
