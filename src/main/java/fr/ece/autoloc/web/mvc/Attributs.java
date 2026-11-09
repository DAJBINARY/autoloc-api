package fr.ece.autoloc.web.mvc;

/**
 * Names shared by the controllers and the Thymeleaf templates: model attributes, flash messages
 * and form fields.
 */
final class Attributs {

    /** Flash or model message shown in red by the layout. */
    static final String ERREUR = "erreur";
    /** Flash message shown in green by the layout. */
    static final String SUCCES = "succes";

    static final String OBJET = "objet";
    static final String CLIENT = "client";
    static final String VEHICULE = "vehicule";
    static final String RESERVATION = "reservation";
    static final String MONTANT = "montant";
    static final String JOURS = "jours";
    static final String AUJOURDHUI = "aujourdhui";

    private Attributs() {
    }
}
