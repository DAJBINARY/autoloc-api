package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.service.CrudService;
import fr.ece.autoloc.service.RegleMetierException;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * List, detail, create, edit and delete screens shared by every entity.
 * Views are resolved from the section name: {@code <section>/liste}, {@code <section>/fiche}
 * and {@code <section>/formulaire}.
 */
public abstract class CrudController<T> {

    private static final String REDIRECTION = "redirect:/";

    private final CrudService<T> service;
    private final String section;
    private final Supplier<T> fabrique;
    private final BiConsumer<T, Long> definirId;

    protected CrudController(CrudService<T> service, String section, Supplier<T> fabrique,
                             BiConsumer<T, Long> definirId) {
        this.service = service;
        this.section = section;
        this.fabrique = fabrique;
        this.definirId = definirId;
    }

    /** Cross-field rules, for the entities that have some. */
    protected void validerMetier(T objet, Errors erreurs) {
    }

    /** Choices offered by the form, such as the lists of linked entities. */
    protected void preparerFormulaire(T objet, Model model) {
    }

    /** Pre-selects values from the query string, e.g. {@code /reservations/nouveau?client=3}. */
    protected void preremplir(T objet, Map<String, String> parametres) {
    }

    /** Refuses the deletion of this element by throwing a {@link RegleMetierException} with the reason. */
    protected void verifierSuppression(T objet) {
    }

    /** Linked data shown on the detail screen. */
    protected void completerFiche(T objet, Model model) {
    }

    /** Runs a workflow action and comes back to the detail screen with its outcome. */
    protected String action(Long id, LongConsumer traitement, String succes, RedirectAttributes redirection) {
        charger(id);
        try {
            traitement.accept(id);
            redirection.addFlashAttribute(Attributs.SUCCES, succes);
        } catch (RegleMetierException e) {
            redirection.addFlashAttribute(Attributs.ERREUR, e.getMessage());
        }
        return REDIRECTION + section + "/" + id;
    }

    protected static Optional<Long> idDe(Map<String, String> parametres, String nom) {
        try {
            return Optional.ofNullable(parametres.get(nom)).map(Long::valueOf);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    protected static Optional<LocalDate> dateDe(Map<String, String> parametres, String nom) {
        try {
            return Optional.ofNullable(parametres.get(nom)).map(LocalDate::parse);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    @InitBinder
    void configurerBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
        // Linked entities are chosen by id: a form must never be able to edit them through "client.nom=...".
        binder.setDisallowedFields("*.*");
    }

    @ModelAttribute("section")
    String section() {
        return section;
    }

    @GetMapping
    String lister(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, Model model) {
        String recherche = q == null ? "" : q.trim();
        model.addAttribute("q", recherche);
        model.addAttribute("objets", service.rechercher(recherche, page));
        return section + "/liste";
    }

    @GetMapping("/nouveau")
    String nouveau(@RequestParam Map<String, String> parametres, Model model) {
        T objet = fabrique.get();
        preremplir(objet, parametres);
        model.addAttribute(Attributs.OBJET, objet);
        return formulaire(objet, model, null);
    }

    @PostMapping
    String creer(@Valid @ModelAttribute("objet") T objet, BindingResult erreurs, Model model,
                 RedirectAttributes redirection) {
        return enregistrer(objet, null, erreurs, model, redirection);
    }

    @GetMapping("/{id}")
    String fiche(@PathVariable Long id, Model model) {
        T objet = charger(id);
        model.addAttribute(Attributs.OBJET, objet);
        completerFiche(objet, model);
        return section + "/fiche";
    }

    @GetMapping("/{id}/modifier")
    String modifier(@PathVariable Long id, Model model) {
        T objet = charger(id);
        model.addAttribute(Attributs.OBJET, objet);
        return formulaire(objet, model, id);
    }

    @PostMapping("/{id}")
    String mettreAJour(@PathVariable Long id, @Valid @ModelAttribute("objet") T objet, BindingResult erreurs,
                       Model model, RedirectAttributes redirection) {
        charger(id);
        return enregistrer(objet, id, erreurs, model, redirection);
    }

    @PostMapping("/{id}/supprimer")
    String supprimer(@PathVariable Long id, RedirectAttributes redirection) {
        try {
            verifierSuppression(charger(id));
            service.supprimer(id);
            redirection.addFlashAttribute(Attributs.SUCCES, "Suppression effectuée.");
        } catch (RegleMetierException e) {
            redirection.addFlashAttribute(Attributs.ERREUR, e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirection.addFlashAttribute(Attributs.ERREUR, "Suppression impossible : cet élément est encore utilisé.");
        }
        return REDIRECTION + section;
    }

    private String enregistrer(T objet, Long id, BindingResult erreurs, Model model, RedirectAttributes redirection) {
        // The id always comes from the URL, never from the submitted form.
        definirId.accept(objet, id);
        validerMetier(objet, erreurs);
        if (erreurs.hasErrors()) {
            return formulaire(objet, model, id);
        }
        try {
            service.enregistrer(objet);
        } catch (DataIntegrityViolationException e) {
            erreurs.reject("unicite", "Enregistrement impossible : une valeur unique est déjà utilisée.");
            return formulaire(objet, model, id);
        }
        redirection.addFlashAttribute(Attributs.SUCCES, "Enregistrement effectué.");
        return REDIRECTION + section;
    }

    private String formulaire(T objet, Model model, Long id) {
        preparerFormulaire(objet, model);
        model.addAttribute("action", id == null ? "/" + section : "/" + section + "/" + id);
        model.addAttribute("modification", id != null);
        return section + "/formulaire";
    }

    protected T charger(Long id) {
        return service.trouver(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
