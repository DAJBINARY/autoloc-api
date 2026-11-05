package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Vehicule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the parking plan of an agency from the place numbers of its vehicles.
 */
@Service
public class ParkingService {

    private static final int PLACES_PAR_RANGEE = 6;
    private static final int PLACES_MINIMUM = 12;

    private final VehiculeService vehicules;

    public ParkingService(VehiculeService vehicules) {
        this.vehicules = vehicules;
    }

    @Transactional(readOnly = true)
    public List<Place> plan(Long idAgence, Long idSurligne) {
        Map<Integer, Vehicule> occupees = vehicules.listerParAgence(idAgence).stream()
                .filter(vehicule -> vehicule.getPlaceParking() != null)
                .collect(Collectors.toMap(Vehicule::getPlaceParking, Function.identity()));
        int derniere = occupees.keySet().stream().max(Integer::compare).orElse(0);
        int total = Math.max(PLACES_MINIMUM, (derniere + PLACES_PAR_RANGEE - 1) / PLACES_PAR_RANGEE * PLACES_PAR_RANGEE);
        List<Place> places = new ArrayList<>();
        for (int numero = 1; numero <= total; numero++) {
            Vehicule vehicule = occupees.get(numero);
            places.add(new Place(numero, vehicule, vehicule != null && vehicule.getIdVehicule().equals(idSurligne)));
        }
        return places;
    }

    public record Place(int numero, Vehicule vehicule, boolean surlignee) {
    }
}
