package fr.ece.autoloc.unitaire.service;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.ClientRepository;
import fr.ece.autoloc.repository.ContratRepository;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.CatalogueService;
import fr.ece.autoloc.service.RegleMetierException;
import fr.ece.autoloc.service.ReservationClientService;
import fr.ece.autoloc.service.ReservationService;
import fr.ece.autoloc.support.Donnees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationClientServiceTest {

    private static final String EMAIL = "alice@example.com";
    private static final Long ID = 10L;
    private static final Long ID_VEHICULE = 2L;
    private static final LocalDate DEMAIN = LocalDate.now().plusDays(1);
    private static final LocalDate APRES_DEMAIN = DEMAIN.plusDays(1);

    @Mock
    private ReservationRepository reservations;
    @Mock
    private VehiculeRepository vehicules;
    @Mock
    private ClientRepository clients;
    @Mock
    private ContratRepository contrats;
    @Mock
    private PaiementRepository paiements;
    @Mock
    private ReservationService calculs;
    @Mock
    private CatalogueService catalogue;
    @InjectMocks
    private ReservationClientService service;

    private final Client client = Donnees.client(1L, EMAIL, "P1");
    private final Vehicule vehicule = Donnees.vehicule(ID_VEHICULE, "AA-111-AA", Donnees.agence(1L), StatutVehicule.DISPONIBLE);

    private Reservation existante(StatutReservation statut, LocalDate debut) {
        Reservation reservation = Donnees.reservation(ID, client, vehicule, debut, debut.plusDays(1), statut);
        when(reservations.findByIdReservationAndClient_Email(ID, EMAIL)).thenReturn(Optional.of(reservation));
        return reservation;
    }

    @Test
    void creeUneReservationEnAttentePourLeClientConnecte() {
        when(vehicules.verrouiller(ID_VEHICULE)).thenReturn(Optional.of(vehicule));
        when(catalogue.estLibre(vehicule, DEMAIN, APRES_DEMAIN)).thenReturn(true);
        when(clients.findByEmail(EMAIL)).thenReturn(Optional.of(client));
        when(reservations.save(any())).thenAnswer(returnsFirstArg());

        Reservation creee = service.reserver(EMAIL, ID_VEHICULE, DEMAIN, APRES_DEMAIN);

        assertThat(creee.getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
        assertThat(creee.getClient()).isSameAs(client);
        assertThat(creee.getVehicule()).isSameAs(vehicule);
        assertThat(creee.getDateDebut()).isEqualTo(DEMAIN);
        assertThat(creee.getDateFin()).isEqualTo(APRES_DEMAIN);
    }

    @Test
    void accepteUneReservationQuiCommenceAujourdhui() {
        LocalDate aujourdhui = LocalDate.now();
        when(vehicules.verrouiller(ID_VEHICULE)).thenReturn(Optional.of(vehicule));
        when(catalogue.estLibre(vehicule, aujourdhui, aujourdhui)).thenReturn(true);
        when(clients.findByEmail(EMAIL)).thenReturn(Optional.of(client));
        when(reservations.save(any())).thenAnswer(returnsFirstArg());

        assertThat(service.reserver(EMAIL, ID_VEHICULE, aujourdhui, aujourdhui)).isNotNull();
    }

    @Test
    void refuseUneDateDeDebutPassee() {
        LocalDate hier = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> service.reserver(EMAIL, ID_VEHICULE, hier, DEMAIN))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("passée");
        verify(reservations, never()).save(any());
    }

    @Test
    void refuseUneFinAvantLeDebut() {
        assertThatThrownBy(() -> service.reserver(EMAIL, ID_VEHICULE, APRES_DEMAIN, DEMAIN))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("date de fin");
        verify(reservations, never()).save(any());
    }

    @Test
    void refuseUnVehiculeSupprime() {
        when(vehicules.verrouiller(ID_VEHICULE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserver(EMAIL, ID_VEHICULE, DEMAIN, APRES_DEMAIN))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("n'existe plus");
    }

    @Test
    void refuseUnVehiculeDejaPris() {
        when(vehicules.verrouiller(ID_VEHICULE)).thenReturn(Optional.of(vehicule));
        when(catalogue.estLibre(vehicule, DEMAIN, APRES_DEMAIN)).thenReturn(false);

        assertThatThrownBy(() -> service.reserver(EMAIL, ID_VEHICULE, DEMAIN, APRES_DEMAIN))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("vient d'être réservé");
        verify(reservations, never()).save(any());
    }

    @Test
    void lePaiementCreeLeContratLePaiementEtConfirmeLaReservation() {
        Reservation reservation = existante(StatutReservation.EN_ATTENTE, DEMAIN);
        BigDecimal prix = new BigDecimal("80.00");
        when(contrats.findByReservation_IdReservation(ID)).thenReturn(Optional.empty());
        when(calculs.montantEstime(reservation)).thenReturn(prix);
        when(contrats.save(any())).thenAnswer(returnsFirstArg());

        service.payerEnLigne(ID, EMAIL);

        ArgumentCaptor<Contrat> contrat = ArgumentCaptor.forClass(Contrat.class);
        verify(contrats).save(contrat.capture());
        assertThat(contrat.getValue().getMontantTotal()).isEqualByComparingTo(prix);
        assertThat(contrat.getValue().getEmploye()).isNull();
        assertThat(contrat.getValue().isValide()).isTrue();
        assertThat(contrat.getValue().getReservation()).isSameAs(reservation);

        ArgumentCaptor<Paiement> paiement = ArgumentCaptor.forClass(Paiement.class);
        verify(paiements).save(paiement.capture());
        assertThat(paiement.getValue().getMontant()).isEqualByComparingTo(prix);
        assertThat(paiement.getValue().getModePaiement()).isEqualTo(ModePaiement.CARTE);
        assertThat(paiement.getValue().getContrat()).isSameAs(contrat.getValue());

        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
    }

    @ParameterizedTest
    @EnumSource(value = StatutReservation.class, names = "EN_ATTENTE", mode = EnumSource.Mode.EXCLUDE)
    void neFaitPasPayerUneReservationQuiNEstPlusEnAttente(StatutReservation statut) {
        existante(statut, DEMAIN);

        assertThatThrownBy(() -> service.payerEnLigne(ID, EMAIL)).isInstanceOf(RegleMetierException.class);
        verify(paiements, never()).save(any());
    }

    @Test
    void neFaitPasPayerDeuxFoisUnContrat() {
        Reservation reservation = existante(StatutReservation.EN_ATTENTE, DEMAIN);
        when(contrats.findByReservation_IdReservation(ID))
                .thenReturn(Optional.of(new Contrat(1L, DEMAIN, BigDecimal.TEN, true, reservation, null)));

        assertThatThrownBy(() -> service.payerEnLigne(ID, EMAIL)).isInstanceOf(RegleMetierException.class);
        verify(paiements, never()).save(any());
    }

    @Test
    void unClientNePayePasLaReservationDUnAutre() {
        when(reservations.findByIdReservationAndClient_Email(ID, "pirate@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payerEnLigne(ID, "pirate@example.com")).isInstanceOf(NoSuchElementException.class);
        verify(paiements, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = StatutReservation.class, names = {"EN_ATTENTE", "CONFIRMEE"})
    void annuleUneReservationFuture(StatutReservation statut) {
        Reservation reservation = existante(statut, DEMAIN);

        service.annuler(ID, EMAIL);

        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.ANNULEE);
    }

    @Test
    void nAnnulePasEnLigneUneReservationQuiCommenceAujourdhui() {
        Reservation reservation = existante(StatutReservation.CONFIRMEE, LocalDate.now());

        assertThatThrownBy(() -> service.annuler(ID, EMAIL)).isInstanceOf(RegleMetierException.class);
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
    }

    @Test
    void nAnnulePasEnLigneUneLocationEnCours() {
        Reservation reservation = existante(StatutReservation.CONFIRMEE, DEMAIN);
        reservation.marquerDepart(LocalDate.now());

        assertThatThrownBy(() -> service.annuler(ID, EMAIL)).isInstanceOf(RegleMetierException.class);
    }

    @ParameterizedTest
    @EnumSource(value = StatutReservation.class, names = {"ANNULEE", "TERMINEE"})
    void nAnnulePasUneReservationDejaFinie(StatutReservation statut) {
        existante(statut, DEMAIN);

        assertThatThrownBy(() -> service.annuler(ID, EMAIL)).isInstanceOf(RegleMetierException.class);
    }
}
