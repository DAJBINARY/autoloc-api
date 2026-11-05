package fr.ece.autoloc.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Back to the page that asked for a login, otherwise to the home screen of the role.
 */
@Component
public class RedirectionApresConnexion extends SavedRequestAwareAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest requete, HttpServletResponse reponse,
                                        Authentication authentification) throws jakarta.servlet.ServletException, IOException {
        boolean client = authentification.getAuthorities().stream()
                .anyMatch(droit -> "ROLE_CLIENT".equals(droit.getAuthority()));
        setDefaultTargetUrl(client ? "/espace" : "/tableau-de-bord");
        super.onAuthenticationSuccess(requete, reponse, authentification);
    }
}
