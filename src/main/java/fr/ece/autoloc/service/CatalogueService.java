package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * What the public can rent: only available vehicles ever leave this service.
 */
@Service
public class CatalogueService {

    private final VehiculeRepository vehicules;
    private final ReservationRepository reservations;
    private final MaintenanceRepository maintenances;

    public CatalogueService(VehiculeRepository vehicules, ReservationRepository reservations,
                            MaintenanceRepository maintenances) {
        this.vehicules = vehicules;
        this.reservations = reservations;
        this.maintenances = maintenances;
    }

    /**
     * Without dates: vehicles available today. With dates: vehicles free on the whole period, which
     * includes a vehicle out on rent today if it is back in time.
     */
    @Transactional(readOnly = true)
    public List<Vehicule> chercher(String q, LocalDate debut, LocalDate fin, CategorieVehicule categorie, Long agence) {
        List<Vehicule> base = debut != null && fin != null
                ? vehicules.trouverDisponibles(debut, fin).stream()
                        .filter(vehicule -> vehicule.getStatut() != StatutVehicule.MAINTENANCE)
                        .toList()
                : vehicules.findByStatutOrderByMarqueAscModeleAsc(StatutVehicule.DISPONIBLE);
        String texte = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        return base.stream()
                .filter(vehicule -> categorie == null || vehicule.getCategorie() == categorie)
                .filter(vehicule -> agence == null
                        || (vehicule.getAgence() != null && agence.equals(vehicule.getAgence().getIdAgence())))
                .filter(vehicule -> (vehicule.getMarque() + " " + vehicule.getModele()).toLowerCase(Locale.ROOT).contains(texte))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean estLibre(Vehicule vehicule, LocalDate debut, LocalDate fin) {
        return vehicule.getStatut() != StatutVehicule.MAINTENANCE
                && reservations.compterChevauchements(vehicule.getIdVehicule(), debut, fin, -1L) == 0
                && !maintenances.existeSurPeriode(vehicule.getIdVehicule(), debut, fin);
    }
}
