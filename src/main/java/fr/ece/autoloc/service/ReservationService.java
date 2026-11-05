package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReservationService extends CrudService<Reservation> {

    private final ReservationRepository repository;
    private final MaintenanceRepository maintenances;

    public ReservationService(ReservationRepository repository, MaintenanceRepository maintenances) {
        super(repository, Sort.by(Sort.Direction.DESC, "dateDebut"));
        this.repository = repository;
        this.maintenances = maintenances;
    }

    @Transactional
    public void confirmer(Long id) {
        Reservation reservation = repository.findById(id).orElseThrow();
        exiger(reservation.getStatut() == StatutReservation.EN_ATTENTE, "Seule une réservation en attente peut être confirmée.");
        exiger(reservation.getClient() != null && reservation.getVehicule() != null,
                "Renseignez d'abord le client et le véhicule de cette réservation.");
        reservation.setStatut(StatutReservation.CONFIRMEE);
    }

    @Transactional
    public void annuler(Long id) {
        Reservation reservation = repository.findById(id).orElseThrow();
        exiger(reservation.getStatut() == StatutReservation.EN_ATTENTE
                || reservation.getStatut() == StatutReservation.CONFIRMEE, "Cette réservation ne peut plus être annulée.");
        exiger(reservation.getDateDepart() == null, "Le véhicule est déjà parti : enregistrez plutôt son retour.");
        reservation.setStatut(StatutReservation.ANNULEE);
    }

    @Transactional
    public void enregistrerDepart(Long id) {
        Reservation reservation = repository.findById(id).orElseThrow();
        exiger(reservation.getStatut() == StatutReservation.CONFIRMEE && reservation.getDateDepart() == null,
                "Seule une réservation confirmée peut donner lieu à un départ.");
        Vehicule vehicule = reservation.getVehicule();
        exiger(vehicule != null && vehicule.getStatut() == StatutVehicule.DISPONIBLE,
                "Le véhicule n'est pas disponible pour un départ.");
        reservation.marquerDepart(LocalDate.now());
        vehicule.setStatut(StatutVehicule.LOUE);
    }

    @Transactional
    public void enregistrerRetour(Long id) {
        Reservation reservation = repository.findById(id).orElseThrow();
        exiger(reservation.isEnCours(), "Aucun départ en cours pour cette réservation.");
        reservation.marquerRetour(LocalDate.now());
        reservation.setStatut(StatutReservation.TERMINEE);
        Vehicule vehicule = reservation.getVehicule();
        vehicule.setStatut(maintenances.existsByVehicule_IdVehiculeAndDateFinIsNull(vehicule.getIdVehicule())
                ? StatutVehicule.MAINTENANCE : StatutVehicule.DISPONIBLE);
    }

    private static void exiger(boolean condition, String message) {
        if (!condition) {
            throw new RegleMetierException(message);
        }
    }

    @Transactional(readOnly = true)
    public boolean vehiculeEnMaintenance(Long idVehicule, LocalDate debut, LocalDate fin) {
        return maintenances.existeSurPeriode(idVehicule, debut, fin);
    }

    /** Daily rate times the rented days, the first and last days both being charged. */
    public BigDecimal estimer(BigDecimal tarifJournalier, LocalDate debut, LocalDate fin) {
        return tarifJournalier.multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(debut, fin) + 1));
    }

    @Transactional(readOnly = true)
    public List<Reservation> listerParClient(Long idClient) {
        return repository.findByClient_IdClientOrderByDateDebutDesc(idClient);
    }

    @Transactional(readOnly = true)
    public List<Reservation> listerParVehicule(Long idVehicule) {
        return repository.findByVehicule_IdVehiculeOrderByDateDebutDesc(idVehicule);
    }

    /** Reservations a contract can be drawn up for, plus the one the edited contract already uses. */
    @Transactional(readOnly = true)
    public List<Reservation> listerSansContrat(Long idCourant) {
        return repository.trouverSansContrat(idCourant == null ? -1L : idCourant);
    }

    @Transactional(readOnly = true)
    public List<Reservation> prochainsDeparts() {
        return repository.findTop5ByDateDebutGreaterThanEqualAndStatutInOrderByDateDebutAsc(LocalDate.now(),
                List.of(StatutReservation.EN_ATTENTE, StatutReservation.CONFIRMEE));
    }

    @Transactional(readOnly = true)
    public long compterParStatut(StatutReservation statut) {
        return repository.countByStatut(statut);
    }

    @Transactional(readOnly = true)
    public long compterLocationsEnCours() {
        return repository.countByDateDepartIsNotNullAndDateRetourIsNull();
    }

    /** True when another active reservation already holds the vehicle on these dates. */
    @Transactional(readOnly = true)
    public boolean vehiculeDejaReserve(Long idVehicule, LocalDate debut, LocalDate fin, Long idExclu) {
        return repository.compterChevauchements(idVehicule, debut, fin, idExclu == null ? -1L : idExclu) > 0;
    }

    public BigDecimal montantEstime(Reservation reservation) {
        return estimer(reservation.getVehicule().getTarifJournalier(), reservation.getDateDebut(), reservation.getDateFin());
    }
}
