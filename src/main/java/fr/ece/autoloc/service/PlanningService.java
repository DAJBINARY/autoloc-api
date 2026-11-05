package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the month grid: one row per vehicle, one cell per day.
 */
@Service
public class PlanningService {

    private final VehiculeRepository vehicules;
    private final ReservationRepository reservations;
    private final MaintenanceRepository maintenances;

    public PlanningService(VehiculeRepository vehicules, ReservationRepository reservations,
                           MaintenanceRepository maintenances) {
        this.vehicules = vehicules;
        this.reservations = reservations;
        this.maintenances = maintenances;
    }

    @Transactional(readOnly = true)
    public List<Ligne> construire(YearMonth mois) {
        LocalDate debut = mois.atDay(1);
        LocalDate fin = mois.atEndOfMonth();
        List<Reservation> periode = reservations.trouverSurPeriode(debut, fin);
        List<Maintenance> travaux = maintenances.trouverSurPeriode(debut, fin);

        List<Ligne> lignes = new ArrayList<>();
        for (Vehicule vehicule : vehicules.findAll(Sort.by("marque", "modele"))) {
            List<Case> cases = new ArrayList<>();
            for (int jour = 1; jour <= mois.lengthOfMonth(); jour++) {
                cases.add(caseDuJour(vehicule, mois.atDay(jour), periode, travaux));
            }
            lignes.add(new Ligne(vehicule, cases));
        }
        return lignes;
    }

    private static Case caseDuJour(Vehicule vehicule, LocalDate jour, List<Reservation> periode,
                                   List<Maintenance> travaux) {
        for (Maintenance maintenance : travaux) {
            if (concerne(maintenance.getVehicule(), vehicule) && !jour.isBefore(maintenance.getDateDebut())
                    && (maintenance.getDateFin() == null || !jour.isAfter(maintenance.getDateFin()))) {
                return new Case("maintenance", "M", "Maintenance : " + maintenance.getDescription(),
                        "/maintenances/" + maintenance.getIdMaintenance());
            }
        }
        for (Reservation reservation : periode) {
            if (concerne(reservation.getVehicule(), vehicule) && !jour.isBefore(reservation.getDateDebut())
                    && !jour.isAfter(reservation.getDateFin())) {
                StatutReservation statut = reservation.getStatut();
                return new Case(statut.name().toLowerCase(), statut.getLibelle().substring(0, 1),
                        statut.getLibelle() + " : " + reservation.getLibelle(),
                        "/reservations/" + reservation.getIdReservation());
            }
        }
        return new Case("libre", "", "Libre", null);
    }

    private static boolean concerne(Vehicule lie, Vehicule vehicule) {
        return lie != null && Objects.equals(lie.getIdVehicule(), vehicule.getIdVehicule());
    }

    public record Ligne(Vehicule vehicule, List<Case> cases) {
    }

    public record Case(String type, String code, String titre, String lien) {
    }
}
