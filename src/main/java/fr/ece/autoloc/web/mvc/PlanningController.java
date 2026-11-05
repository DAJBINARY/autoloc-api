package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.service.PlanningService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@Controller
public class PlanningController {

    private final PlanningService planning;

    public PlanningController(PlanningService planning) {
        this.planning = planning;
    }

    @GetMapping("/planning")
    String planning(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mois, Model model) {
        YearMonth affiche = mois == null ? YearMonth.now() : mois;
        List<Integer> jours = IntStream.rangeClosed(1, affiche.lengthOfMonth()).boxed().toList();
        LocalDate aujourdhui = LocalDate.now();
        model.addAttribute("libelleMois", affiche.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)));
        model.addAttribute("moisPrecedent", affiche.minusMonths(1).toString());
        model.addAttribute("moisSuivant", affiche.plusMonths(1).toString());
        model.addAttribute("jours", jours);
        model.addAttribute("jourCourant", YearMonth.from(aujourdhui).equals(affiche) ? aujourdhui.getDayOfMonth() : 0);
        model.addAttribute("lignes", planning.construire(affiche));
        return "planning";
    }
}
