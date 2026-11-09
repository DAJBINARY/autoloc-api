package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.config.Horloge;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.service.CatalogueService;
import fr.ece.autoloc.service.ClientService;
import fr.ece.autoloc.service.ContratService;
import fr.ece.autoloc.service.PaiementService;
import fr.ece.autoloc.service.RegleMetierException;
import fr.ece.autoloc.service.ReservationClientService;
import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.service.VehiculeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The signed-in client's area: book, pay and follow their rentals.
 * The profile and the parking plan have their own controllers under the same path.
 */
@Controller
@RequestMapping("/espace")
public class EspaceClientController {

    private static final String REDIRECTION_RESERVATION = "redirect:/espace/reservations/";
    private static final String CODE_INVALIDE = "invalide";

    private final ReservationClientService reservations;
    private final ReservationService calculs;
    private final CatalogueService catalogue;
    private final VehiculeService vehicules;
    private final ClientService clients;
    private final ContratService contrats;
    private final PaiementService paiements;

    public EspaceClientController(ReservationClientService reservations, ReservationService calculs,
                                  CatalogueService catalogue, VehiculeService vehicules, ClientService clients,
                                  ContratService contrats, PaiementService paiements) {
        this.reservations = reservations;
        this.calculs = calculs;
        this.catalogue = catalogue;
        this.vehicules = vehicules;
        this.clients = clients;
        this.contrats = contrats;
        this.paiements = paiements;
    }

    @GetMapping
    String accueil(Principal utilisateur, Model model) {
        List<Reservation> toutes = reservations.lister(utilisateur.getName());
        LocalDate aujourdhui = LocalDate.now(Horloge.ZONE);
        model.addAttribute(Attributs.CLIENT, clientConnecte(utilisateur));
        model.addAttribute("reservations", toutes);
        model.addAttribute("prochaine", toutes.stream()
                .filter(reservation -> reservation.getStatut() != StatutReservation.ANNULEE
                        && reservation.getStatut() != StatutReservation.TERMINEE
                        && !reservation.getDateFin().isBefore(aujourdhui))
                .reduce((plusTardive, plusTot) -> plusTot)
                .orElse(null));
        return "espace/accueil";
    }

    @GetMapping("/reservations/nouvelle")
    String nouvelleReservation(@RequestParam Long vehicule, @RequestParam(required = false) LocalDate debut,
                               @RequestParam(required = false) LocalDate fin, Model model) {
        Vehicule choisi = vehicules.trouver(vehicule).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDate aujourdhui = LocalDate.now(Horloge.ZONE);
        model.addAttribute(Attributs.VEHICULE, choisi);
        model.addAttribute("debut", debut);
        model.addAttribute("fin", fin);
        model.addAttribute(Attributs.AUJOURDHUI, aujourdhui);
        boolean datesValides = debut != null && fin != null && !fin.isBefore(debut) && !debut.isBefore(aujourdhui);
        model.addAttribute("datesValides", datesValides);
        if (datesValides) {
            model.addAttribute("libre", catalogue.estLibre(choisi, debut, fin));
            model.addAttribute(Attributs.JOURS, ChronoUnit.DAYS.between(debut, fin) + 1);
            model.addAttribute(Attributs.MONTANT, calculs.estimer(choisi.getTarifJournalier(), debut, fin));
        } else if (debut != null || fin != null) {
            model.addAttribute(Attributs.ERREUR, "Choisissez une période valide, à partir d'aujourd'hui.");
        }
        return "espace/reservation-nouvelle";
    }

    @PostMapping("/reservations")
    String reserver(@RequestParam Long vehicule, @RequestParam LocalDate debut, @RequestParam LocalDate fin,
                    Principal utilisateur, RedirectAttributes redirection) {
        try {
            Reservation reservation = reservations.reserver(utilisateur.getName(), vehicule, debut, fin);
            return REDIRECTION_RESERVATION + reservation.getIdReservation() + "/paiement";
        } catch (RegleMetierException e) {
            redirection.addFlashAttribute(Attributs.ERREUR, e.getMessage());
            redirection.addAttribute(Attributs.VEHICULE, vehicule);
            redirection.addAttribute("debut", debut);
            redirection.addAttribute("fin", fin);
            return "redirect:/espace/reservations/nouvelle";
        }
    }

    @GetMapping("/reservations/{id}")
    String reservation(@PathVariable Long id, Principal utilisateur, Model model) {
        Reservation reservation = charger(id, utilisateur);
        model.addAttribute(Attributs.RESERVATION, reservation);
        ajouterMontants(reservation, model);
        return "espace/reservation";
    }

    @GetMapping("/reservations/{id}/paiement")
    String paiement(@PathVariable Long id, Principal utilisateur, Model model) {
        Reservation reservation = charger(id, utilisateur);
        if (reservation.getStatut() != StatutReservation.EN_ATTENTE) {
            return REDIRECTION_RESERVATION + id;
        }
        model.addAttribute("form", new PaiementForm());
        return afficherPaiement(reservation, model);
    }

    @PostMapping("/reservations/{id}/paiement")
    String payer(@PathVariable Long id, @Valid @ModelAttribute("form") PaiementForm form, BindingResult erreurs,
                 Principal utilisateur, Model model, RedirectAttributes redirection) {
        Reservation reservation = charger(id, utilisateur);
        if (form.getTitulaire() == null || form.getTitulaire().isBlank()) {
            erreurs.rejectValue("titulaire", "obligatoire", "Ce champ est obligatoire.");
        }
        if (!form.numeroValide()) {
            erreurs.rejectValue("numero", CODE_INVALIDE, "Numéro de carte invalide.");
        }
        if (!form.expirationValide()) {
            erreurs.rejectValue("expiration", CODE_INVALIDE, "Date invalide ou dépassée (format MM/AA).");
        }
        if (!form.cryptogrammeValide()) {
            erreurs.rejectValue("cryptogramme", CODE_INVALIDE, "3 ou 4 chiffres.");
        }
        if (erreurs.hasErrors()) {
            return afficherPaiement(reservation, model);
        }
        try {
            reservations.payerEnLigne(id, utilisateur.getName());
        } catch (RegleMetierException e) {
            redirection.addFlashAttribute(Attributs.ERREUR, e.getMessage());
            return REDIRECTION_RESERVATION + id;
        }
        return REDIRECTION_RESERVATION + id + "/confirmation";
    }

    @PostMapping("/reservations/{id}/payer-en-agence")
    String payerEnAgence(@PathVariable Long id, Principal utilisateur) {
        charger(id, utilisateur);
        return REDIRECTION_RESERVATION + id + "/confirmation";
    }

    @GetMapping("/reservations/{id}/confirmation")
    String confirmation(@PathVariable Long id, Principal utilisateur, Model model) {
        Reservation reservation = charger(id, utilisateur);
        model.addAttribute(Attributs.RESERVATION, reservation);
        ajouterMontants(reservation, model);
        return "espace/confirmation";
    }

    @GetMapping("/reservations/{id}/contrat")
    String contrat(@PathVariable Long id, Principal utilisateur, Model model) {
        Reservation reservation = charger(id, utilisateur);
        Contrat contrat = contrats.trouverParReservation(reservation.getIdReservation())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        BigDecimal paye = paiements.totalPourContrat(contrat.getIdContrat());
        model.addAttribute(Attributs.OBJET, contrat);
        model.addAttribute("paye", paye);
        model.addAttribute("reste", contrat.getMontantTotal().subtract(paye));
        model.addAttribute("retour", "/espace/reservations/" + id);
        return "contrats/impression";
    }

    @PostMapping("/reservations/{id}/annuler")
    String annuler(@PathVariable Long id, Principal utilisateur, RedirectAttributes redirection) {
        charger(id, utilisateur);
        try {
            reservations.annuler(id, utilisateur.getName());
            redirection.addFlashAttribute(Attributs.SUCCES, "Votre réservation est annulée.");
        } catch (RegleMetierException e) {
            redirection.addFlashAttribute(Attributs.ERREUR, e.getMessage());
        }
        return REDIRECTION_RESERVATION + id;
    }

    private String afficherPaiement(Reservation reservation, Model model) {
        model.addAttribute(Attributs.RESERVATION, reservation);
        model.addAttribute(Attributs.MONTANT, calculs.montantEstime(reservation));
        model.addAttribute(Attributs.JOURS, jours(reservation));
        return "espace/paiement";
    }

    private void ajouterMontants(Reservation reservation, Model model) {
        model.addAttribute(Attributs.JOURS, jours(reservation));
        Contrat contrat = contrats.trouverParReservation(reservation.getIdReservation()).orElse(null);
        model.addAttribute("contrat", contrat);
        model.addAttribute(Attributs.MONTANT, montant(reservation, contrat));
        model.addAttribute("paye", contrat != null ? paiements.totalPourContrat(contrat.getIdContrat()) : BigDecimal.ZERO);
    }

    /** Amount of the signed contract, or the estimate while there is none yet. */
    private BigDecimal montant(Reservation reservation, Contrat contrat) {
        if (contrat != null) {
            return contrat.getMontantTotal();
        }
        return reservation.getVehicule() != null ? calculs.montantEstime(reservation) : BigDecimal.ZERO;
    }

    private static long jours(Reservation reservation) {
        return ChronoUnit.DAYS.between(reservation.getDateDebut(), reservation.getDateFin()) + 1;
    }

    /** Someone else's reservation answers exactly like one that does not exist. */
    private Reservation charger(Long id, Principal utilisateur) {
        return reservations.trouver(id, utilisateur.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Client clientConnecte(Principal utilisateur) {
        return clients.trouverParEmail(utilisateur.getName())
                .orElseThrow(() -> new AccessDeniedException("Compte client introuvable"));
    }
}
