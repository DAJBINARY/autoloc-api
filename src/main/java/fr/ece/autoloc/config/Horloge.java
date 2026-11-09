package fr.ece.autoloc.config;

import java.time.ZoneId;

/**
 * Time zone used for every "today" computed by the application, so that dates do not
 * depend on the default time zone of the server.
 */
public final class Horloge {

    public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private Horloge() {
    }
}
