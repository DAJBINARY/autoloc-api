package fr.ece.autoloc.fonctionnel;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One person's browser: it keeps its session cookie and reads the anti-forgery token out of the
 * pages, like a real browser does. Redirects are not followed, so that a test can check them.
 */
final class Navigateur {

    private static final Pattern JETON_CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    private final String adresse;
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    Navigateur(int port) {
        this("http://localhost:" + port);
    }

    Navigateur(String adresse) {
        this.adresse = adresse;
    }

    HttpResponse<String> ouvrir(String chemin) throws IOException, InterruptedException {
        return envoyer(HttpRequest.newBuilder(URI.create(adresse + chemin)).GET().build());
    }

    /** Submits a form: the page that displays the form gives the token, then the form goes to the action. */
    HttpResponse<String> soumettre(String page, String action, String... champs) throws IOException, InterruptedException {
        return poster(action, new StringBuilder("_csrf=").append(encoder(jeton(page))), champs);
    }

    /** What a forged form on another site would send: the session cookie goes along, but no token. */
    HttpResponse<String> soumettreSansJeton(String action, String... champs) throws IOException, InterruptedException {
        return poster(action, new StringBuilder("_"), champs);
    }

    private HttpResponse<String> poster(String action, StringBuilder corps, String... champs) throws IOException, InterruptedException {
        for (int i = 0; i < champs.length; i += 2) {
            corps.append('&').append(encoder(champs[i])).append('=').append(encoder(champs[i + 1]));
        }
        return envoyer(HttpRequest.newBuilder(URI.create(adresse + action))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corps.toString()))
                .build());
    }

    HttpResponse<String> seConnecter(String identifiant, String motDePasse) throws IOException, InterruptedException {
        return soumettre("/connexion", "/connexion", "identifiant", identifiant, "motDePasse", motDePasse);
    }

    /** Path a response redirects to, or null. */
    static String redirection(HttpResponse<String> reponse) {
        return reponse.headers().firstValue("Location")
                .map(cible -> {
                    URI uri = URI.create(cible);
                    return uri.getQuery() == null ? uri.getPath() : uri.getPath() + "?" + uri.getQuery();
                })
                .orElse(null);
    }

    private String jeton(String page) throws IOException, InterruptedException {
        HttpResponse<String> reponse = ouvrir(page);
        Matcher jeton = JETON_CSRF.matcher(reponse.body());
        if (!jeton.find()) {
            throw new IllegalStateException("Aucun formulaire sur " + page + " (HTTP " + reponse.statusCode() + ")");
        }
        return jeton.group(1);
    }

    private HttpResponse<String> envoyer(HttpRequest requete) throws IOException, InterruptedException {
        return client.send(requete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String encoder(String valeur) {
        return URLEncoder.encode(valeur, StandardCharsets.UTF_8);
    }
}
