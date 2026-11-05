package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Online booking of a signed-in client. Every lookup is scoped to the client's e-mail, so that a client
 * can never reach the reservation of another one.
 */
@Service
public class ReservationClientService {

    private final ReservationRepository reservations;
    private final VehiculeRepository vehicules;
    private final ClientRepository clients;
    private final ContratRepository contrats;
    private final PaiementRepository paiements;
    private final ReservationService calculs;
    private final CatalogueService catalogue;

    public ReservationClientService(ReservationRepository reservations, VehiculeRepository vehicules,
                                    ClientRepository clients, ContratRepository contrats, PaiementRepository paiements,
                                    ReservationService calculs, CatalogueService catalogue) {
        this.reservations = reservations;
        this.vehicules = vehicules;
        this.clients = clients;
        this.contrats = contrats;
        this.paiements = paiements;
        this.calculs = calculs;
        this.catalogue = catalogue;
    }

    @Transactional(readOnly = true)
    public List<Reservation> lister(String email) {
        return reservations.findByClient_EmailOrderByDateDebutDesc(email);
    }

    @Transactional(readOnly = true)
    public Optional<Reservation> trouver(Long id, String email) {
        return reservations.findByIdReservationAndClient_Email(id, email);
    }

    @Transactional
    public Reservation reserver(String email, Long idVehicule, LocalDate debut, LocalDate fin) {
        if (debut.isBefore(LocalDate.now())) {
            throw new RegleMetierException("La date de début ne peut pas être passée.");
        }
        if (fin.isBefore(debut)) {
            throw new RegleMetierException("La date de fin doit être postérieure ou égale à la date de début.");
        }
        Vehicule vehicule = vehicules.verrouiller(idVehicule)
                .orElseThrow(() -> new RegleMetierException("Ce véhicule n'existe plus."));
        if (!catalogue.estLibre(vehicule, debut, fin)) {
            throw new RegleMetierException("Ce véhicule vient d'être réservé sur ces dates. Essayez d'autres dates.");
        }
        return reservations.save(new Reservation(null, debut, fin, StatutReservation.EN_ATTENTE,
                clients.findByEmail(email).orElseThrow(), vehicule));
    }

    /**
     * Records a simulated card payment of the full price, computed here and never taken from the form,
     * and confirms the reservation.
     */
    @Transactional
    public void payerEnLigne(Long id, String email) {
        Reservation reservation = reservations.findByIdReservationAndClient_Email(id, email).orElseThrow();
        if (reservation.getStatut() != StatutReservation.EN_ATTENTE
                || contrats.findByReservation_IdReservation(id).isPresent()) {
            throw new RegleMetierException("Cette réservation n'est plus à payer.");
        }
        BigDecimal montant = calculs.montantEstime(reservation);
        Contrat contrat = contrats.save(new Contrat(null, LocalDate.now(), montant, true, reservation, null));
        paiements.save(new Paiement(null, montant, LocalDate.now(), ModePaiement.CARTE, contrat));
        reservation.setStatut(StatutReservation.CONFIRMEE);
    }

    @Transactional
    public void annuler(Long id, String email) {
        Reservation reservation = reservations.findByIdReservationAndClient_Email(id, email).orElseThrow();
        boolean annulable = reservation.getStatut() == StatutReservation.EN_ATTENTE
                || reservation.getStatut() == StatutReservation.CONFIRMEE;
        if (!annulable || reservation.getDateDepart() != null || !reservation.getDateDebut().isAfter(LocalDate.now())) {
            throw new RegleMetierException("Cette réservation ne peut plus être annulée en ligne. Contactez votre agence.");
        }
        reservation.setStatut(StatutReservation.ANNULEE);
    }
}
