package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.service.CatalogueService;
import fr.ece.autoloc.service.ClientService;
import fr.ece.autoloc.service.CompteService;
import fr.ece.autoloc.service.ContratService;
import fr.ece.autoloc.service.PaiementService;
import fr.ece.autoloc.service.ParkingService;
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
 * The signed-in client's area: book, pay, follow their rentals and find their vehicle in the parking.
 */
@Controller
@RequestMapping("/espace")
public class EspaceClientController {

    private final ReservationClientService reservations;
    private final ReservationService calculs;
    private final CatalogueService catalogue;
    private final VehiculeService vehicules;
    private final ClientService clients;
    private final ContratService contrats;
    private final PaiementService paiements;
    private final ParkingService parking;
    private final CompteService comptes;

    public EspaceClientController(ReservationClientService reservations, ReservationService calculs,
                                  CatalogueService catalogue, VehiculeService vehicules, ClientService clients,
                                  ContratService contrats, PaiementService paiements, ParkingService parking,
                                  CompteService comptes) {
        this.reservations = reservations;
        this.calculs = calculs;
        this.catalogue = catalogue;
        this.vehicules = vehicules;
        this.clients = clients;
        this.contrats = contrats;
        this.paiements = paiements;
        this.parking = parking;
        this.comptes = comptes;
    }

    @GetMapping
    String accueil(Principal utilisateur, Model model) {
        List<Reservation> toutes = reservations.lister(utilisateur.getName());
        LocalDate aujourdhui = LocalDate.now();
        model.addAttribute("client", clientConnecte(utilisateur));
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
        model.addAttribute("vehicule", choisi);
        model.addAttribute("debut", debut);
        model.addAttribute("fin", fin);
        model.addAttribute("aujourdhui", LocalDate.now());
        boolean datesValides = debut != null && fin != null && !fin.isBefore(debut) && !debut.isBefore(LocalDate.now());
        model.addAttribute("datesValides", datesValides);
        if (datesValides) {
            model.addAttribute("libre", catalogue.estLibre(choisi, debut, fin));
            model.addAttribute("jours", ChronoUnit.DAYS.between(debut, fin) + 1);
            model.addAttribute("montant", calculs.estimer(choisi.getTarifJournalier(), debut, fin));
        } else if (debut != null || fin != null) {
            model.addAttribute("erreur", "Choisissez une période valide, à partir d'aujourd'hui.");
        }
        return "espace/reservation-nouvelle";
    }

    @PostMapping("/reservations")
    String reserver(@RequestParam Long vehicule, @RequestParam LocalDate debut, @RequestParam LocalDate fin,
                    Principal utilisateur, RedirectAttributes redirection) {
        try {
            Reservation reservation = reservations.reserver(utilisateur.getName(), vehicule, debut, fin);
            return "redirect:/espace/reservations/" + reservation.getIdReservation() + "/paiement";
        } catch (RegleMetierException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
            redirection.addAttribute("vehicule", vehicule);
            redirection.addAttribute("debut", debut);
            redirection.addAttribute("fin", fin);
            return "redirect:/espace/reservations/nouvelle";
        }
    }

    @GetMapping("/reservations/{id}")
    String reservation(@PathVariable Long id, Principal utilisateur, Model model) {
        Reservation reservation = charger(id, utilisateur);
        model.addAttribute("reservation", reservation);
        ajouterMontants(reservation, model);
        return "espace/reservation";
    }

    @GetMapping("/reservations/{id}/paiement")
    String paiement(@PathVariable Long id, Principal utilisateur, Model model) {
        Reservation reservation = charger(id, utilisateur);
        if (reservation.getStatut() != StatutReservation.EN_ATTENTE) {
            return "redirect:/espace/reservations/" + id;
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
            erreurs.rejectValue("numero", "invalide", "Numéro de carte invalide.");
        }
        if (!form.expirationValide()) {
            erreurs.rejectValue("expiration", "invalide", "Date invalide ou dépassée (format MM/AA).");
        }
        if (!form.cryptogrammeValide()) {
            erreurs.rejectValue("cryptogramme", "invalide", "3 ou 4 chiffres.");
        }
        if (erreurs.hasErrors()) {
            return afficherPaiement(reservation, model);
        }
        try {
            reservations.payerEnLigne(id, utilisateur.getName());
        } catch (RegleMetierException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
            return "redirect:/espace/reservations/" + id;
        }
        return "redirect:/espace/reservations/" + id + "/confirmation";
    }

    @PostMapping("/reservations/{id}/payer-en-agence")
    String payerEnAgence(@PathVariable Long id, Principal utilisateur) {
        charger(id, utilisateur);
        return "redirect:/espace/reservations/" + id + "/confirmation";
    }

    @GetMapping("/reservations/{id}/confirmation")
    String confirmation(@PathVariable Long id, Principal utilisateur, Model model) {
        Reservation reservation = charger(id, utilisateur);
        model.addAttribute("reservation", reservation);
        ajouterMontants(reservation, model);
        return "espace/confirmation";
    }

    @GetMapping("/reservations/{id}/parking")
    String parking(@PathVariable Long id, Principal utilisateur, Model model) {
        Reservation reservation = charger(id, utilisateur);
        Vehicule vehicule = reservation.getVehicule();
        if (vehicule == null || vehicule.getAgence() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("reservation", reservation);
        model.addAttribute("vehicule", vehicule);
        model.addAttribute("places", parking.plan(vehicule.getAgence().getIdAgence(), vehicule.getIdVehicule()));
        return "espace/parking";
    }

    @GetMapping("/reservations/{id}/contrat")
    String contrat(@PathVariable Long id, Principal utilisateur, Model model) {
        Reservation reservation = charger(id, utilisateur);
        Contrat contrat = contrats.trouverParReservation(reservation.getIdReservation())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        BigDecimal paye = paiements.totalPourContrat(contrat.getIdContrat());
        model.addAttribute("objet", contrat);
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
            redirection.addFlashAttribute("succes", "Votre réservation est annulée.");
        } catch (RegleMetierException e) {
            redirection.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/espace/reservations/" + id;
    }

    @GetMapping("/profil")
    String profil(Principal utilisateur, Model model) {
        Client client = clientConnecte(utilisateur);
        ProfilForm form = new ProfilForm();
        form.setPrenom(client.getPrenom());
        form.setNom(client.getNom());
        form.setTelephone(client.getTelephone());
        model.addAttribute("form", form);
        model.addAttribute("client", client);
        return "espace/profil";
    }

    @PostMapping("/profil")
    String enregistrerProfil(@Valid @ModelAttribute("form") ProfilForm form, BindingResult erreurs, Principal utilisateur,
                             Model model, RedirectAttributes redirection) {
        Client client = clientConnecte(utilisateur);
        if (erreurs.hasErrors()) {
            model.addAttribute("client", client);
            return "espace/profil";
        }
        client.setPrenom(form.getPrenom().strip());
        client.setNom(form.getNom().strip());
        client.setTelephone(form.getTelephone().strip());
        clients.enregistrer(client);
        redirection.addFlashAttribute("succes", "Profil enregistré.");
        return "redirect:/espace/profil";
    }

    @PostMapping("/mot-de-passe")
    String changerMotDePasse(@RequestParam String actuel, @RequestParam String nouveau, @RequestParam String confirmation,
                             Principal utilisateur, RedirectAttributes redirection) {
        if (nouveau.length() < 8 || nouveau.length() > 72) {
            redirection.addFlashAttribute("erreur", "Le nouveau mot de passe doit contenir entre 8 et 72 caractères.");
        } else if (!nouveau.equals(confirmation)) {
            redirection.addFlashAttribute("erreur", "Les deux nouveaux mots de passe sont différents.");
        } else if (!comptes.changerMotDePasseClient(utilisateur.getName(), actuel, nouveau)) {
            redirection.addFlashAttribute("erreur", "Le mot de passe actuel est incorrect.");
        } else {
            redirection.addFlashAttribute("succes", "Mot de passe modifié.");
        }
        return "redirect:/espace/profil";
    }

    private String afficherPaiement(Reservation reservation, Model model) {
        model.addAttribute("reservation", reservation);
        model.addAttribute("montant", calculs.montantEstime(reservation));
        model.addAttribute("jours", ChronoUnit.DAYS.between(reservation.getDateDebut(), reservation.getDateFin()) + 1);
        return "espace/paiement";
    }

    private void ajouterMontants(Reservation reservation, Model model) {
        model.addAttribute("jours", ChronoUnit.DAYS.between(reservation.getDateDebut(), reservation.getDateFin()) + 1);
        Contrat contrat = contrats.trouverParReservation(reservation.getIdReservation()).orElse(null);
        model.addAttribute("contrat", contrat);
        model.addAttribute("montant", contrat != null ? contrat.getMontantTotal()
                : reservation.getVehicule() != null ? calculs.montantEstime(reservation) : BigDecimal.ZERO);
        model.addAttribute("paye", contrat != null ? paiements.totalPourContrat(contrat.getIdContrat()) : BigDecimal.ZERO);
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
