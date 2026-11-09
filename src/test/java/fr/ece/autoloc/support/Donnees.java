package fr.ece.autoloc.support;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Valid objects for the tests. A null id gives an object ready to be saved.
 */
public final class Donnees {

    private Donnees() {
    }

    public static Agence agence(Long id) {
        return new Agence(id, "AutoLoc Paris", "Paris", "1 rue de Rivoli", "0100000000");
    }

    public static Client client(Long id, String email, String numPermis) {
        return new Client(id, "Durand", "Alice", email, "0600000000", numPermis, LocalDate.of(2026, 1, 1));
    }

    public static Vehicule vehicule(Long id, String immatriculation, Agence agence, StatutVehicule statut) {
        Vehicule vehicule = new Vehicule(immatriculation, "Renault", "Clio", CategorieVehicule.CITADINE,
                new BigDecimal("40.00"), statut, agence);
        vehicule.setIdVehicule(id);
        return vehicule;
    }

    public static Reservation reservation(Long id, Client client, Vehicule vehicule, LocalDate debut, LocalDate fin,
                                          StatutReservation statut) {
        return new Reservation(id, debut, fin, statut, client, vehicule);
    }
}
