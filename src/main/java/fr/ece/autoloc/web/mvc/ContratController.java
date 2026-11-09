package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.config.Horloge;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.service.ContratService;
import fr.ece.autoloc.service.EmployeService;
import fr.ece.autoloc.service.PaiementService;
import fr.ece.autoloc.service.ReservationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/contrats")
public class ContratController extends CrudController<Contrat> {

    private final ContratService service;
    private final ReservationService reservations;
    private final EmployeService employes;
    private final PaiementService paiements;

    public ContratController(ContratService service, ReservationService reservations, EmployeService employes,
                             PaiementService paiements) {
        super(service, "contrats", ContratController::nouveauAvecDate, Contrat::setIdContrat);
        this.service = service;
        this.reservations = reservations;
        this.employes = employes;
        this.paiements = paiements;
    }

    @GetMapping("/{id}/imprimer")
    String imprimer(@PathVariable Long id, Model model) {
        Contrat contrat = charger(id);
        model.addAttribute("objet", contrat);
        completerFiche(contrat, model);
        return "contrats/impression";
    }

    private static Contrat nouveauAvecDate() {
        Contrat contrat = new Contrat();
        contrat.setDateSignature(LocalDate.now(Horloge.ZONE));
        return contrat;
    }

    @Override
    protected void validerMetier(Contrat objet, Errors erreurs) {
        if (objet.getEmploye() == null) {
            erreurs.rejectValue("employe", "obligatoire", "Ce champ est obligatoire.");
        }
        if (objet.getReservation() == null) {
            return;
        }
        service.trouverParReservation(objet.getReservation().getIdReservation())
                .filter(existant -> !Objects.equals(existant.getIdContrat(), objet.getIdContrat()))
                .ifPresent(existant -> erreurs.rejectValue("reservation", "doublon",
                        "Cette réservation a déjà un contrat."));
    }

    @Override
    protected void preparerFormulaire(Contrat objet, Model model) {
        Long idCourant = objet.getReservation() == null ? null : objet.getReservation().getIdReservation();
        model.addAttribute("reservations", reservations.listerSansContrat(idCourant));
        model.addAttribute("employes", employes.lister());
    }

    @Override
    protected void preremplir(Contrat objet, Map<String, String> parametres) {
        idDe(parametres, "reservation").flatMap(reservations::trouver).ifPresent(reservation -> {
            objet.setReservation(reservation);
            if (reservation.getVehicule() != null) {
                objet.setMontantTotal(reservations.montantEstime(reservation));
            }
        });
    }

    @Override
    protected void completerFiche(Contrat objet, Model model) {
        BigDecimal paye = paiements.totalPourContrat(objet.getIdContrat());
        model.addAttribute("paiements", paiements.listerParContrat(objet.getIdContrat()));
        model.addAttribute("paye", paye);
        model.addAttribute("reste", objet.getMontantTotal().subtract(paye));
    }
}
