package fr.ece.autoloc.unitaire.service;

import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.service.StatistiquesService;
import fr.ece.autoloc.service.StatistiquesService.Mois;
import fr.ece.autoloc.service.StatistiquesService.Nombre;
import fr.ece.autoloc.service.StatistiquesService.Total;
import fr.ece.autoloc.support.Donnees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatistiquesServiceTest {

    @Mock
    private PaiementRepository paiements;
    @Mock
    private ReservationRepository reservations;
    @InjectMocks
    private StatistiquesService service;

    private static Paiement paiement(String montant, LocalDate date) {
        return new Paiement(null, new BigDecimal(montant), date, ModePaiement.CARTE, null);
    }

    @Test
    void cumuleLesPaiementsParMoisDuPlusAncienAuPlusRecent() {
        YearMonth courant = YearMonth.now();
        when(paiements.findByDatePaiementGreaterThanEqual(courant.minusMonths(2).atDay(1))).thenReturn(List.of(
                paiement("100.00", courant.atDay(1)),
                paiement("100.00", courant.atEndOfMonth()),
                paiement("50.00", courant.minusMonths(2).atDay(15))));

        List<Mois> mois = service.encaisseParMois(3);

        assertThat(mois).extracting(Mois::total).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("200"));
    }

    @Test
    void calculeLesPourcentagesParRapportAuMeilleurMois() {
        YearMonth courant = YearMonth.now();
        when(paiements.findByDatePaiementGreaterThanEqual(any())).thenReturn(List.of(
                paiement("200.00", courant.atDay(1)),
                paiement("50.00", courant.minusMonths(2).atDay(1))));

        assertThat(service.encaisseParMois(3)).extracting(Mois::pourcentage).containsExactly(25, 0, 100);
    }

    @Test
    void afficheZeroPourCentSansAucunPaiement() {
        when(paiements.findByDatePaiementGreaterThanEqual(any())).thenReturn(List.of());

        assertThat(service.encaisseParMois(2)).extracting(Mois::pourcentage).containsExactly(0, 0);
    }

    @Test
    void traduitLesTotauxParMode() {
        when(paiements.totalParMode()).thenReturn(List.<Object[]>of(new Object[]{ModePaiement.ESPECES, new BigDecimal("12.50")}));

        assertThat(service.encaisseParMode()).containsExactly(new Total("Espèces", new BigDecimal("12.50")));
    }

    @Test
    void traduitLesReservationsParStatut() {
        when(reservations.compterParStatut()).thenReturn(List.<Object[]>of(new Object[]{StatutReservation.CONFIRMEE, 3L}));

        assertThat(service.reservationsParStatut()).containsExactly(new Nombre("Confirmée", 3));
    }

    @Test
    void limiteLeClassementDesVehiculesAuNombreDemande() {
        when(reservations.vehiculesLesPlusReserves(PageRequest.of(0, 5))).thenReturn(List.<Object[]>of(
                new Object[]{Donnees.vehicule(1L, "AA-111-AA", null, StatutVehicule.DISPONIBLE), 4L}));

        assertThat(service.vehiculesLesPlusReserves(5)).containsExactly(new Nombre("Renault Clio (AA-111-AA)", 4));
    }
}
