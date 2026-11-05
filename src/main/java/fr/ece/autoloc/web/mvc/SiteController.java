package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.service.AgenceService;
import fr.ece.autoloc.service.CatalogueService;
import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.service.VehiculeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Public pages: home, catalogue and vehicle sheet.
 */
@Controller
public class SiteController {

    private static final int VEHICULES_EN_VITRINE = 3;
    private static final int VEHICULES_PAR_PAGE = 9;

    private final CatalogueService catalogue;
    private final VehiculeService vehicules;
    private final AgenceService agences;
    private final ReservationService reservations;

    public SiteController(CatalogueService catalogue, VehiculeService vehicules, AgenceService agences,
                          ReservationService reservations) {
        this.catalogue = catalogue;
        this.vehicules = vehicules;
        this.agences = agences;
        this.reservations = reservations;
    }

    @GetMapping("/")
    String accueil(Model model) {
        List<Vehicule> disponibles = vehicules.listerParStatut(StatutVehicule.DISPONIBLE);
        model.addAttribute("vitrine", disponibles.stream().limit(VEHICULES_EN_VITRINE)
                .map(vehicule -> new Carte(vehicule, null, 0)).toList());
        model.addAttribute("nombreDisponibles", disponibles.size());
        model.addAttribute("nombreAgences", agences.compter());
        model.addAttribute("categories", CategorieVehicule.values());
        model.addAttribute("agences", agences.lister());
        return "site/accueil";
    }

    @GetMapping("/catalogue")
    String catalogue(@RequestParam(required = false) String q, @RequestParam(required = false) LocalDate debut,
                     @RequestParam(required = false) LocalDate fin, @RequestParam(required = false) CategorieVehicule categorie,
                     @RequestParam(required = false) Long agence, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("q", q == null ? "" : q.strip());
        model.addAttribute("debut", debut);
        model.addAttribute("fin", fin);
        model.addAttribute("categorie", categorie);
        model.addAttribute("agence", agence);
        model.addAttribute("categories", CategorieVehicule.values());
        model.addAttribute("agences", agences.lister());
        model.addAttribute("aujourdhui", LocalDate.now());

        boolean unSeulBout = (debut == null) != (fin == null);
        boolean datesValides = debut != null && fin != null && !fin.isBefore(debut) && !debut.isBefore(LocalDate.now());
        if (unSeulBout) {
            model.addAttribute("erreur", "Indiquez la date de début et la date de fin.");
        } else if (debut != null && !datesValides) {
            model.addAttribute("erreur", debut.isBefore(LocalDate.now())
                    ? "La date de début ne peut pas être passée."
                    : "La date de fin doit être postérieure ou égale à la date de début.");
        }
        LocalDate debutRetenu = datesValides ? debut : null;
        LocalDate finRetenue = datesValides ? fin : null;
        model.addAttribute("datesRetenues", datesValides);

        List<Carte> cartes = catalogue.chercher(q, debutRetenu, finRetenue, categorie, agence).stream()
                .map(vehicule -> carte(vehicule, debutRetenu, finRetenue))
                .toList();
        int pages = Math.max(1, (cartes.size() + VEHICULES_PAR_PAGE - 1) / VEHICULES_PAR_PAGE);
        int courante = Math.min(Math.max(page, 0), pages - 1);
        int depart = courante * VEHICULES_PAR_PAGE;
        model.addAttribute("cartes", cartes.subList(depart, Math.min(depart + VEHICULES_PAR_PAGE, cartes.size())));
        model.addAttribute("total", cartes.size());
        model.addAttribute("page", courante);
        model.addAttribute("pages", pages);
        return "site/catalogue";
    }

    @GetMapping("/catalogue/{id}")
    String vehicule(@PathVariable Long id, @RequestParam(required = false) LocalDate debut,
                    @RequestParam(required = false) LocalDate fin, Model model) {
        Vehicule vehicule = vehicules.trouver(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("vehicule", vehicule);
        model.addAttribute("debut", debut);
        model.addAttribute("fin", fin);
        model.addAttribute("aujourdhui", LocalDate.now());
        boolean datesValides = debut != null && fin != null && !fin.isBefore(debut) && !debut.isBefore(LocalDate.now());
        if (datesValides) {
            boolean libre = catalogue.estLibre(vehicule, debut, fin);
            model.addAttribute("libre", libre);
            model.addAttribute("jours", ChronoUnit.DAYS.between(debut, fin) + 1);
            model.addAttribute("montant", reservations.estimer(vehicule.getTarifJournalier(), debut, fin));
        } else if (debut != null || fin != null) {
            model.addAttribute("erreur", "Choisissez une période valide, à partir d'aujourd'hui.");
        }
        return "site/vehicule";
    }

    private Carte carte(Vehicule vehicule, LocalDate debut, LocalDate fin) {
        if (debut == null) {
            return new Carte(vehicule, null, 0);
        }
        return new Carte(vehicule, reservations.estimer(vehicule.getTarifJournalier(), debut, fin),
                ChronoUnit.DAYS.between(debut, fin) + 1);
    }

    public record Carte(Vehicule vehicule, BigDecimal montant, long jours) {
    }
}
