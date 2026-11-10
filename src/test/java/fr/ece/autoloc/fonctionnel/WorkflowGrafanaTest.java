package fr.ece.autoloc.fonctionnel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static fr.ece.autoloc.fonctionnel.Navigateur.redirection;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the running application on its real database so that Grafana shows real activity.
 *
 * <p>Each round browses every screen, then really works like the staff and the clients do: it creates,
 * edits, confirms, signs, pays, rents out, sends to maintenance, returns and cancels, using the agencies,
 * vehicles, clients and employees already there. Existing rows are only read or used as links: what the
 * workflow creates carries a marker, is checked before being deleted, and is deleted at the end of the
 * round; the vehicles it rents get their status back. A fingerprint of the lists taken before and after
 * proves that everything is as it was.
 *
 * <p>Off by default, as it needs the application and its monitoring to be running:
 * {@code .\mvnw.cmd test -Dworkflow=true -Dtest=WorkflowGrafanaTest}, with a manager account in
 * WORKFLOW_IDENTIFIANT and WORKFLOW_MOT_DE_PASSE. Options: {@code -Dworkflow.minutes=3},
 * {@code -Dworkflow.url=http://localhost:8080}.
 */
@EnabledIfSystemProperty(named = "workflow", matches = "true")
class WorkflowGrafanaTest {

    private static final String URL = System.getProperty("workflow.url", "http://localhost:8080");
    private static final String PROMETHEUS = System.getProperty("workflow.prometheus", "http://localhost:19090");
    private static final Duration DUREE = Duration.ofMinutes(Integer.getInteger("workflow.minutes", 3));
    private static final long PAUSE_MILLISECONDES = 100;

    private static final int PAGES_MAXIMUM = 600;
    private static final int PAGES_PAR_ECRAN = 100;
    private static final int PAGES_PAR_ECRAN_AVEC_FILTRES = 5;

    private static final List<String> SECTIONS = List.of("agences", "clients", "contrats", "employes", "equipements",
            "maintenances", "paiements", "reservations", "vehicules");
    private static final List<String> FORMULAIRES_REFUSES = List.of("clients", "contrats", "equipements",
            "maintenances", "paiements", "reservations", "vehicules");
    private static final List<String> JAMAIS_OUVERTS = List.of("/css", "/js", "/images", "/fonts", "/vendor",
            "/favicon", "/actuator", "/deconnexion", "/premiere-connexion");

    private static final String PAGE_EMPLOYE = "/mon-compte";
    private static final String PAGE_CLIENT = "/espace/profil";
    private static final String MOT_DE_PASSE_CLIENT = "workflow-1234";
    private static final String ENREGISTRE = "Enregistrement effectué.";
    private static final String SUPPRIME = "Suppression effectuée.";

    private static final Pattern LIEN = Pattern.compile("href=\"(/[^\"#]*)\"");
    private static final Pattern JETON = Pattern.compile("(name=\"_csrf\"\\s+value=\")[^\"]+");
    private static final Pattern NOMBRE = Pattern.compile("\\d+");
    private static final Pattern MONTANT_TOTAL = Pattern.compile(
            "<input[^>]*name=\"montantTotal\"[^>]*value=\"([0-9.]+)\"|<input[^>]*value=\"([0-9.]+)\"[^>]*name=\"montantTotal\"");
    private static final DateTimeFormatter FRANCAIS = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final LocalDate AUJOURDHUI = LocalDate.now();
    private static final String DEMAIN = AUJOURDHUI.plusDays(1).toString();
    private static final String DANS_3_JOURS = AUJOURDHUI.plusDays(3).toString();
    private static final String HIER = AUJOURDHUI.minusDays(1).toString();

    private final Set<String> erreursServeur = new TreeSet<>();
    private final List<String> nonNettoyes = new ArrayList<>();
    private final Map<Integer, Integer> codes = new TreeMap<>();
    private final Map<String, Integer> operations = new TreeMap<>();
    private final HttpClient http = HttpClient.newHttpClient();
    private int requetes;

    @Test
    void travailleSurLesDonneesExistantesEtRemetToutEnEtat() throws Exception {
        verifierLaSupervision();
        double compteurAvant = compteurDeRequetes();

        Navigateur temoin = new Navigateur(URL);
        boolean employe = seConnecterEmploye(temoin);
        boolean manager = employe && ouvrir(temoin, "/statistiques").statusCode() == 200;
        Map<String, String> empreinteAvant = empreinte(temoin, employe);

        List<String> pagesVisiteur = explorer(new Navigateur(URL), pagesPubliques());
        List<String> pagesEmploye = employe ? explorer(temoin, pagesBackOffice()) : List.of();
        journal("%d pages publiques et %d pages back-office trouvées", pagesVisiteur.size(), pagesEmploye.size());
        if (!manager) {
            journal("ATTENTION : sans compte manager (WORKFLOW_IDENTIFIANT, WORKFLOW_MOT_DE_PASSE), aucune manipulation : "
                    + "le workflow ne pourrait pas supprimer ce qu'il crée.");
        }

        Instant fin = Instant.now().plus(DUREE);
        int tour = 0;
        while (Instant.now().isBefore(fin)) {
            tour++;
            rejouer(new Navigateur(URL), pagesVisiteur, fin);
            parcoursRefuses(employe);
            if (employe) {
                Navigateur session = new Navigateur(URL);
                seConnecterEmploye(session);
                rejouer(session, pagesEmploye, fin);
            }
            if (manager) {
                manipulations(tour);
            }
            journal("Tour %d : %d requêtes, codes HTTP %s, opérations %s", tour, requetes, codes, operations);
        }

        Map<String, String> empreinteApres = empreinte(temoin, employe);
        double compteurApres = compteurDeRequetes();
        journal("L'application a compté %.0f requêtes de plus : http://localhost:3000/d/autoloc-api/autoloc-api",
                compteurApres - compteurAvant);

        assertThat(nonNettoyes).as("éléments créés par le workflow et non supprimés").isEmpty();
        assertThat(erreursServeur).as("pages en erreur serveur (5xx)").isEmpty();
        assertThat(empreinteApres).as("les listes doivent être identiques avant et après le workflow")
                .isEqualTo(empreinteAvant);
        assertThat(compteurApres).as("requêtes comptées par l'application").isGreaterThan(compteurAvant);
    }

    // ---------------------------------------------------------------- real work

    /** One round of real work. Whatever happens, what was created is deleted, newest first. */
    private void manipulations(int tour) throws IOException, InterruptedException {
        String marqueur = "wf" + Long.toString(System.currentTimeMillis(), 36) + tour;
        Navigateur agent = new Navigateur(URL);
        seConnecterEmploye(agent);
        Deque<String> aDefaire = new ArrayDeque<>();
        // Dates far ahead, and different at each round, so as not to compete with real bookings.
        LocalDate debut = AUJOURDHUI.plusDays(400 + (tour * 13L) % 300);
        try {
            Set<Long> temporaires = referentiel(agent, marqueur, aDefaire);
            locationEnAgence(agent, marqueur, debut, temporaires, aDefaire);
            locationEnLigne(agent, marqueur, debut.plusDays(10), temporaires, aDefaire);
        } catch (AssertionError | RuntimeException e) {
            journal("Tour %d interrompu (%s) : remise en état", tour, e.getMessage());
            throw e;
        } finally {
            defaire(agent, aDefaire);
        }
    }

    /** Creates, edits and shows an equipment, a vehicle in an existing agency and a client. */
    private Set<Long> referentiel(Navigateur agent, String marqueur, Deque<String> aDefaire)
            throws IOException, InterruptedException {
        Set<Long> temporaires = new LinkedHashSet<>();

        String libelle = "Workflow " + marqueur;
        exiger(agent, poster(agent, PAGE_EMPLOYE, "/equipements", "libelle", libelle), ENREGISTRE, "création d'équipement");
        long equipement = aMoi(agent, "equipements", dernierId(agent, "/equipements?q=" + marqueur, "equipements"), marqueur, aDefaire);
        exiger(agent, poster(agent, PAGE_EMPLOYE, "/equipements/" + equipement, "libelle", libelle + " modifié"),
                ENREGISTRE, "modification d'équipement");

        String immatriculation = ("WF-" + marqueur).toUpperCase();
        String agence = String.valueOf(premierId(agent, "/agences", "agences", Set.of()));
        String[] vehicule = {"immatriculation", immatriculation, "marque", "Workflow", "modele", marqueur,
                "categorie", "CITADINE", "tarifJournalier", "35.00", "statut", "DISPONIBLE", "agence", agence};
        exiger(agent, poster(agent, PAGE_EMPLOYE, "/vehicules", vehicule), ENREGISTRE, "création de véhicule");
        long idVehicule = aMoi(agent, "vehicules", dernierId(agent, "/vehicules?q=" + immatriculation, "vehicules"),
                immatriculation, aDefaire);
        temporaires.add(idVehicule);
        vehicule[9] = "39.90";
        exiger(agent, poster(agent, PAGE_EMPLOYE, "/vehicules/" + idVehicule, vehicule), ENREGISTRE, "modification de véhicule");

        String email = marqueur + "@workflow.test";
        String[] client = {"nom", "Workflow", "prenom", marqueur, "email", email, "telephone", "0600000000",
                "numPermis", "WF-" + marqueur, "dateInscription", AUJOURDHUI.toString()};
        exiger(agent, poster(agent, PAGE_EMPLOYE, "/clients", client), ENREGISTRE, "création de client");
        long idClient = aMoi(agent, "clients", dernierId(agent, "/clients?q=" + email, "clients"), email, aDefaire);
        temporaires.add(idClient);
        client[7] = "0700000000";
        exiger(agent, poster(agent, PAGE_EMPLOYE, "/clients/" + idClient, client), ENREGISTRE, "modification de client");
        return temporaires;
    }

    /** The agency process, on an existing client and an existing vehicle. */
    private void locationEnAgence(Navigateur agent, String marqueur, LocalDate debut, Set<Long> temporaires,
                                  Deque<String> aDefaire) throws IOException, InterruptedException {
        LocalDate fin = debut.plusDays(2);
        Long vehicule = vehiculeLibre(debut, fin.plusDays(1), temporaires);
        if (vehicule == null) {
            journal("Aucun de vos véhicules n'est libre du %s au %s : location en agence sautée", debut, fin);
            return;
        }
        long client = premierId(agent, "/clients", "clients", temporaires);
        long employe = premierId(agent, "/employes", "employes", Set.of());
        String[] reservation = {"dateDebut", debut.toString(), "dateFin", fin.toString(), "statut", "EN_ATTENTE",
                "client", String.valueOf(client), "vehicule", String.valueOf(vehicule)};

        exiger(agent, poster(agent, PAGE_EMPLOYE, "/reservations", reservation), ENREGISTRE, "réservation en agence");
        long id = aMoi(agent, "reservations", dernierId(agent, "/vehicules/" + vehicule, "reservations"),
                debut.format(FRANCAIS), aDefaire);
        String fiche = "/reservations/" + id;

        reservation[3] = fin.plusDays(1).toString();
        exiger(agent, poster(agent, PAGE_EMPLOYE, fiche, reservation), ENREGISTRE, "modification de réservation");
        exiger(agent, poster(agent, PAGE_EMPLOYE, fiche + "/confirmer"), "Réservation confirmée.", "confirmation");

        Matcher montant = MONTANT_TOTAL.matcher(ouvrir(agent, "/contrats/nouveau?reservation=" + id).body());
        assertThat(montant.find()).as("montant proposé pour le contrat").isTrue();
        BigDecimal total = new BigDecimal(montant.group(1) != null ? montant.group(1) : montant.group(2));
        exiger(agent, poster(agent, PAGE_EMPLOYE, "/contrats", "reservation", String.valueOf(id),
                "employe", String.valueOf(employe), "dateSignature", AUJOURDHUI.toString(),
                "montantTotal", total.toPlainString(), "valide", "true"), ENREGISTRE, "contrat");
        long contrat = defaireApres(dernierId(agent, fiche, "contrats"), "contrats", aDefaire);

        BigDecimal acompte = total.divide(BigDecimal.valueOf(2), 2, RoundingMode.DOWN);
        payer(agent, contrat, acompte, "CARTE", aDefaire);
        HttpResponse<String> tropPaye = poster(agent, PAGE_EMPLOYE, "/paiements", "contrat", String.valueOf(contrat),
                "montant", total.toPlainString(), "datePaiement", AUJOURDHUI.toString(), "modePaiement", "CARTE");
        assertThat(tropPaye.body()).as("paiement supérieur au reste dû").contains("dépasse le reste à payer");
        payer(agent, contrat, total.subtract(acompte), "ESPECES", aDefaire);

        exiger(agent, poster(agent, PAGE_EMPLOYE, fiche + "/depart"), "Départ enregistré", "départ");
        String retour = fiche + "/retour";
        aDefaire.addFirst(retour);

        exiger(agent, poster(agent, PAGE_EMPLOYE, "/maintenances", "vehicule", String.valueOf(vehicule),
                "dateDebut", AUJOURDHUI.toString(), "description", "Workflow " + marqueur), ENREGISTRE, "maintenance");
        long maintenance = aMoi(agent, "maintenances", dernierId(agent, "/vehicules/" + vehicule, "maintenances"),
                marqueur, aDefaire);

        exiger(agent, poster(agent, PAGE_EMPLOYE, retour), "Retour enregistré", "retour");
        aDefaire.remove(retour);
        exiger(agent, poster(agent, PAGE_EMPLOYE, "/maintenances/" + maintenance + "/cloturer"),
                "Maintenance clôturée.", "clôture de maintenance");
        ouvrir(agent, "/contrats/" + contrat + "/imprimer");
        compter("location en agence");
    }

    /** The online process of a new client: sign-up, booking, card payment, then a booking cancelled online. */
    private void locationEnLigne(Navigateur agent, String marqueur, LocalDate debut, Set<Long> temporaires,
                                 Deque<String> aDefaire) throws IOException, InterruptedException {
        Long vehicule = vehiculeLibre(debut, debut.plusDays(15), temporaires);
        if (vehicule == null) {
            journal("Aucun de vos véhicules n'est libre du %s : location en ligne sautée", debut);
            return;
        }
        String email = marqueur + "-web@workflow.test";
        Navigateur client = new Navigateur(URL);
        HttpResponse<String> inscription = poster(client, "/inscription", "/inscription", "prenom", marqueur + "web",
                "nom", "Workflow", "email", email, "telephone", "0600000000", "numPermis", "WFW-" + marqueur,
                "motDePasse", MOT_DE_PASSE_CLIENT, "confirmation", MOT_DE_PASSE_CLIENT);
        assertThat(redirection(inscription)).as("inscription en ligne").isEqualTo("/espace");
        aMoi(agent, "clients", dernierId(agent, "/clients?q=" + email, "clients"), email, aDefaire);

        long reservation = reserverEnLigne(client, vehicule, debut, debut.plusDays(2), aDefaire);
        String espace = "/espace/reservations/" + reservation;
        ouvrir(client, espace + "/paiement");
        String expiration = YearMonth.now().plusYears(1).format(DateTimeFormatter.ofPattern("MM/yy"));
        HttpResponse<String> paiement = poster(client, espace + "/paiement", espace + "/paiement",
                "titulaire", "Workflow", "numero", "4242 4242 4242 4242", "expiration", expiration, "cryptogramme", "123");
        assertThat(redirection(paiement)).as("paiement en ligne").isEqualTo(espace + "/confirmation");

        long contrat = defaireApres(dernierId(agent, "/reservations/" + reservation, "contrats"), "contrats", aDefaire);
        for (long idPaiement : ids(ouvrir(agent, "/contrats/" + contrat).body(), "paiements")) {
            defaireApres(idPaiement, "paiements", aDefaire);
        }
        for (String page : List.of(espace + "/confirmation", espace, espace + "/contrat", espace + "/parking", "/espace")) {
            ouvrir(client, page);
        }

        long annulee = reserverEnLigne(client, vehicule, debut.plusDays(10), debut.plusDays(11), aDefaire);
        String pageAnnulee = "/espace/reservations/" + annulee;
        assertThat(redirection(poster(client, pageAnnulee, pageAnnulee + "/annuler"))).as("annulation en ligne").isNotNull();
        assertThat(ouvrir(agent, "/reservations/" + annulee).body()).as("réservation annulée").contains("Annulée");
        compter("location en ligne");
    }

    private long reserverEnLigne(Navigateur client, long vehicule, LocalDate debut, LocalDate fin, Deque<String> aDefaire)
            throws IOException, InterruptedException {
        String page = "/espace/reservations/nouvelle?vehicule=" + vehicule + "&debut=" + debut + "&fin=" + fin;
        ouvrir(client, page);
        HttpResponse<String> reponse = poster(client, PAGE_CLIENT, "/espace/reservations",
                "vehicule", String.valueOf(vehicule), "debut", debut.toString(), "fin", fin.toString());
        Matcher id = Pattern.compile("/espace/reservations/(\\d+)/paiement").matcher(String.valueOf(redirection(reponse)));
        assertThat(id.matches()).as("réservation en ligne du %s", debut).isTrue();
        return defaireApres(Long.parseLong(id.group(1)), "reservations", aDefaire);
    }

    private void payer(Navigateur agent, long contrat, BigDecimal montant, String mode, Deque<String> aDefaire)
            throws IOException, InterruptedException {
        Set<Long> avant = ids(ouvrir(agent, "/contrats/" + contrat).body(), "paiements");
        exiger(agent, poster(agent, PAGE_EMPLOYE, "/paiements", "contrat", String.valueOf(contrat),
                "montant", montant.toPlainString(), "datePaiement", AUJOURDHUI.toString(), "modePaiement", mode),
                ENREGISTRE, "paiement " + mode);
        Set<Long> apres = ids(ouvrir(agent, "/contrats/" + contrat).body(), "paiements");
        apres.removeAll(avant);
        assertThat(apres).as("nouveau paiement sur le contrat").hasSize(1);
        defaireApres(apres.iterator().next(), "paiements", aDefaire);
    }

    /** Deletes what the round created, newest first, and keeps a list of anything that resisted. */
    private void defaire(Navigateur agent, Deque<String> aDefaire) throws IOException, InterruptedException {
        while (!aDefaire.isEmpty()) {
            String action = aDefaire.poll();
            String attendu = action.endsWith("/retour") ? "Retour enregistré" : SUPPRIME;
            try {
                HttpResponse<String> reponse = poster(agent, PAGE_EMPLOYE, action);
                String suite = redirection(reponse);
                if (suite == null || !ouvrir(agent, suite).body().contains(attendu)) {
                    nonNettoyes.add(action);
                }
            } catch (IOException | RuntimeException e) {
                nonNettoyes.add(action + " (" + e.getMessage() + ")");
            }
        }
        if (!nonNettoyes.isEmpty()) {
            journal("ATTENTION, à supprimer à la main : %s", nonNettoyes);
        }
    }

    /**
     * Only deletes a row whose page shows the marker of this round: an id picked up by mistake can never
     * point the clean-up at an existing row.
     */
    private long aMoi(Navigateur agent, String section, long id, String marqueur, Deque<String> aDefaire)
            throws IOException, InterruptedException {
        String fiche = ouvrir(agent, "/" + section + "/" + id).body();
        assertThat(fiche).as("la fiche /%s/%d doit être celle créée par le workflow", section, id).contains(marqueur);
        return defaireApres(id, section, aDefaire);
    }

    /** For rows found from a row of the workflow (its contract, its payments), which are therefore its own. */
    private static long defaireApres(long id, String section, Deque<String> aDefaire) {
        aDefaire.addFirst("/" + section + "/" + id + "/supprimer");
        return id;
    }

    /** An existing vehicle available today and free on the period, other than the workflow's own. */
    private Long vehiculeLibre(LocalDate debut, LocalDate fin, Set<Long> exclus) throws IOException, InterruptedException {
        Navigateur visiteur = new Navigateur(URL);
        Set<Long> libres = ids(ouvrir(visiteur, "/catalogue?debut=" + debut + "&fin=" + fin).body(), "catalogue");
        libres.retainAll(ids(ouvrir(visiteur, "/catalogue").body(), "catalogue"));
        libres.removeAll(exclus);
        return libres.isEmpty() ? null : libres.iterator().next();
    }

    private long premierId(Navigateur navigateur, String page, String section, Set<Long> exclus)
            throws IOException, InterruptedException {
        Set<Long> trouves = ids(ouvrir(navigateur, page).body(), section);
        trouves.removeAll(exclus);
        assertThat(trouves).as("au moins un élément existant sur %s", page).isNotEmpty();
        return trouves.iterator().next();
    }

    private long dernierId(Navigateur navigateur, String page, String section) throws IOException, InterruptedException {
        Set<Long> trouves = ids(ouvrir(navigateur, page).body(), section);
        assertThat(trouves).as("lien /%s/... sur %s", section, page).isNotEmpty();
        return trouves.stream().max(Long::compare).orElseThrow();
    }

    private static Set<Long> ids(String page, String section) {
        Set<Long> ids = new LinkedHashSet<>();
        Matcher lien = Pattern.compile("href=\"/" + section + "/(\\d+)").matcher(page);
        while (lien.find()) {
            ids.add(Long.parseLong(lien.group(1)));
        }
        return ids;
    }

    private void exiger(Navigateur navigateur, HttpResponse<String> reponse, String message, String etape)
            throws IOException, InterruptedException {
        String suite = redirection(reponse);
        assertThat(suite).as("%s : le formulaire a été refusé", etape).isNotNull();
        assertThat(ouvrir(navigateur, suite).body()).as("%s", etape).contains(message);
        compter(etape);
    }

    private void compter(String operation) {
        operations.merge(operation, 1, Integer::sum);
    }

    // ---------------------------------------------------------------- reading

    private List<String> pagesPubliques() {
        return List.of("/", "/catalogue", "/catalogue?q=a", "/catalogue?categorie=SUV",
                "/catalogue?debut=" + DEMAIN + "&fin=" + DANS_3_JOURS, "/connexion", "/inscription");
    }

    private List<String> pagesBackOffice() {
        List<String> pages = new ArrayList<>(List.of("/tableau-de-bord", "/planning", "/parking", "/statistiques",
                "/mon-compte", "/disponibilites", "/disponibilites?debut=" + DEMAIN + "&fin=" + DANS_3_JOURS));
        for (String section : SECTIONS) {
            pages.add("/" + section);
            pages.add("/" + section + "?q=a");
            pages.add("/" + section + "/nouveau");
        }
        return pages;
    }

    /** Follows the links from the given pages: every screen and every existing row they lead to. */
    private List<String> explorer(Navigateur navigateur, List<String> depart) throws IOException, InterruptedException {
        Set<String> vues = new LinkedHashSet<>();
        Map<String, Integer> parEcran = new HashMap<>();
        Deque<String> aOuvrir = new ArrayDeque<>(depart);
        while (!aOuvrir.isEmpty() && vues.size() < PAGES_MAXIMUM) {
            String page = aOuvrir.poll();
            if (vues.contains(page) || JAMAIS_OUVERTS.stream().anyMatch(page::startsWith) || !placeDisponible(page, parEcran)) {
                continue;
            }
            vues.add(page);
            Matcher liens = LIEN.matcher(ouvrir(navigateur, page).body());
            while (liens.find()) {
                aOuvrir.add(liens.group(1).replace("&amp;", "&"));
            }
        }
        return List.copyOf(vues);
    }

    /** The planning links to the next month forever: each screen is only visited a limited number of times. */
    private static boolean placeDisponible(String page, Map<String, Integer> parEcran) {
        int separateur = page.indexOf('?');
        boolean filtres = separateur >= 0;
        String chemin = filtres ? page.substring(0, separateur) : page;
        String ecran = NOMBRE.matcher(chemin).replaceAll("#") + (filtres ? "?" : "");
        return parEcran.merge(ecran, 1, Integer::sum) <= (filtres ? PAGES_PAR_ECRAN_AVEC_FILTRES : PAGES_PAR_ECRAN);
    }

    private void rejouer(Navigateur navigateur, List<String> pages, Instant fin) throws IOException, InterruptedException {
        for (String page : pages) {
            if (Instant.now().isAfter(fin)) {
                return;
            }
            ouvrir(navigateur, page);
            Thread.sleep(PAUSE_MILLISECONDES);
        }
    }

    /** Requests the application must turn down: they exercise the error paths without changing anything. */
    private void parcoursRefuses(boolean employe) throws IOException, InterruptedException {
        Navigateur visiteur = new Navigateur(URL);
        ouvrir(visiteur, "/catalogue/999999999");
        ouvrir(visiteur, "/vehicules");
        HttpResponse<String> connexion = noter("POST /connexion",
                visiteur.seConnecter("workflow-compte-inconnu", "mauvais-mot-de-passe"));
        assertThat(redirection(connexion)).isEqualTo("/connexion?error");
        HttpResponse<String> inscription = poster(visiteur, "/inscription", "/inscription",
                "email", "pas-un-email", "motDePasse", "court", "confirmation", "different");
        assertThat(inscription.statusCode()).as("inscription invalide").isEqualTo(200);
        if (employe) {
            Navigateur session = new Navigateur(URL);
            seConnecterEmploye(session);
            ouvrir(session, "/vehicules/999999999");
            for (String section : FORMULAIRES_REFUSES) {
                assertThat(redirection(poster(session, PAGE_EMPLOYE, "/" + section)))
                        .as("formulaire vide sur /%s : il doit être refusé", section).isNull();
            }
        }
        Navigateur client = new Navigateur(URL);
        noter("POST /espace/reservations", client.soumettre("/connexion", "/espace/reservations",
                "vehicule", "1", "debut", HIER, "fin", HIER));
    }

    private boolean seConnecterEmploye(Navigateur navigateur) throws IOException, InterruptedException {
        String identifiant = System.getenv("WORKFLOW_IDENTIFIANT");
        String motDePasse = System.getenv("WORKFLOW_MOT_DE_PASSE");
        if (identifiant == null || identifiant.isBlank() || motDePasse == null) {
            return false;
        }
        HttpResponse<String> reponse = noter("POST /connexion", navigateur.seConnecter(identifiant, motDePasse));
        assertThat(redirection(reponse)).as("connexion de l'employé %s", identifiant).isEqualTo("/tableau-de-bord");
        return true;
    }

    /** What the lists show, without the anti-forgery tokens, which change with each page. */
    private Map<String, String> empreinte(Navigateur navigateur, boolean employe) throws IOException, InterruptedException {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("/catalogue", sansJeton(ouvrir(new Navigateur(URL), "/catalogue").body()));
        if (employe) {
            for (String section : SECTIONS) {
                pages.put("/" + section, sansJeton(ouvrir(navigateur, "/" + section).body()));
            }
        }
        return pages;
    }

    private static String sansJeton(String page) {
        return JETON.matcher(page).replaceAll("$1");
    }

    // ---------------------------------------------------------------- HTTP and monitoring

    private HttpResponse<String> ouvrir(Navigateur navigateur, String page) throws IOException, InterruptedException {
        return noter("GET " + page, navigateur.ouvrir(page));
    }

    private HttpResponse<String> poster(Navigateur navigateur, String page, String action, String... champs)
            throws IOException, InterruptedException {
        return noter("POST " + action, navigateur.soumettre(page, action, champs));
    }

    private HttpResponse<String> noter(String requete, HttpResponse<String> reponse) {
        requetes++;
        codes.merge(reponse.statusCode(), 1, Integer::sum);
        if (reponse.statusCode() >= 500) {
            erreursServeur.add(requete + " -> " + reponse.statusCode());
        }
        return reponse;
    }

    private void verifierLaSupervision() throws IOException, InterruptedException {
        String requete = URLEncoder.encode("up{job=\"autoloc-api\"}", StandardCharsets.UTF_8);
        String reponse;
        try {
            reponse = lire(PROMETHEUS + "/api/v1/query?query=" + requete);
        } catch (IOException e) {
            throw new IllegalStateException("Prometheus est injoignable sur " + PROMETHEUS + " : lancez docker compose -f docker-compose.cicd.yml --env-file ci/.env up -d", e);
        }
        assertThat(reponse).as("Prometheus doit voir l'application démarrée").contains("\"1\"]");
    }

    private double compteurDeRequetes() throws IOException, InterruptedException {
        return lire(URL + "/actuator/prometheus").lines()
                .filter(ligne -> ligne.startsWith("http_server_requests_seconds_count{"))
                .mapToDouble(ligne -> Double.parseDouble(ligne.substring(ligne.lastIndexOf(' ') + 1)))
                .sum();
    }

    private String lire(String adresse) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(adresse)).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static void journal(String message, Object... valeurs) {
        System.out.printf("[workflow] " + message + "%n", valeurs);
    }
}
