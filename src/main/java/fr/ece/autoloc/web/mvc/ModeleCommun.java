package fr.ece.autoloc.web.mvc;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Who is signed in, for the header and for hiding the actions their role does not allow.
 */
@ControllerAdvice
public class ModeleCommun {

    /** Thymeleaf no longer exposes the request: the layout reads the current path from here. */
    @ModelAttribute("chemin")
    String chemin(HttpServletRequest requete) {
        return requete.getRequestURI();
    }

    @ModelAttribute("utilisateur")
    String utilisateur() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        return authentification == null || authentification instanceof AnonymousAuthenticationToken
                ? null : authentification.getName();
    }

    @ModelAttribute("estClient")
    boolean estClient() {
        return aLeDroit("ROLE_CLIENT");
    }

    @ModelAttribute("estPersonnel")
    boolean estPersonnel() {
        return aLeDroit("ROLE_AGENT") || aLeDroit("ROLE_MANAGER");
    }

    @ModelAttribute("estManager")
    boolean estManager() {
        return aLeDroit("ROLE_MANAGER");
    }

    private static boolean aLeDroit(String droitRecherche) {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        return authentification != null && authentification.getAuthorities().stream()
                .anyMatch(droit -> droitRecherche.equals(droit.getAuthority()));
    }
}
