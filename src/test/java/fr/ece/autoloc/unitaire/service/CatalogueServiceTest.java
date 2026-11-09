package fr.ece.autoloc.unitaire.service;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.CatalogueService;
import fr.ece.autoloc.support.Donnees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogueServiceTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 3, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 3, 3);

    @Mock
    private VehiculeRepository vehicules;
    @Mock
    private ReservationRepository reservations;
    @Mock
    private MaintenanceRepository maintenances;
    @InjectMocks
    private CatalogueService service;

    private final Agence paris = Donnees.agence(1L);
    private final Agence lyon = Donnees.agence(2L);
    private final Vehicule clio = Donnees.vehicule(1L, "AA-111-AA", paris, StatutVehicule.DISPONIBLE);
    private final Vehicule suv = suv();

    private Vehicule suv() {
        Vehicule vehicule = Donnees.vehicule(2L, "BB-222-BB", lyon, StatutVehicule.DISPONIBLE);
        vehicule.setMarque("Peugeot");
        vehicule.setModele("3008");
        vehicule.setCategorie(CategorieVehicule.SUV);
        return vehicule;
    }

    private void disponiblesAujourdhui() {
        when(vehicules.findByStatutOrderByMarqueAscModeleAsc(StatutVehicule.DISPONIBLE)).thenReturn(List.of(suv, clio));
    }

    @Test
    void sansDatesProposeLesVehiculesDisponiblesAujourdhui() {
        disponiblesAujourdhui();

        assertThat(service.chercher(null, null, null, null, null)).containsExactly(suv, clio);
        verify(vehicules, never()).trouverDisponibles(any(), any());
    }

    @Test
    void uneSeuleDateNeSuffitPasPourChercherSurUnePeriode() {
        disponiblesAujourdhui();

        assertThat(service.chercher(null, DEBUT, null, null, null)).containsExactly(suv, clio);
        verify(vehicules, never()).trouverDisponibles(any(), any());
    }

    @Test
    void avecDatesInclutUnVehiculeLoueMaisExclutUnVehiculeEnMaintenance() {
        Vehicule loue = Donnees.vehicule(3L, "CC-333-CC", paris, StatutVehicule.LOUE);
        Vehicule auGarage = Donnees.vehicule(4L, "DD-444-DD", paris, StatutVehicule.MAINTENANCE);
        when(vehicules.trouverDisponibles(DEBUT, FIN)).thenReturn(List.of(loue, auGarage));

        assertThat(service.chercher(null, DEBUT, FIN, null, null)).containsExactly(loue);
    }

    @Test
    void filtreParCategorie() {
        disponiblesAujourdhui();

        assertThat(service.chercher(null, null, null, CategorieVehicule.SUV, null)).containsExactly(suv);
    }

    @Test
    void filtreParAgence() {
        disponiblesAujourdhui();

        assertThat(service.chercher(null, null, null, null, 1L)).containsExactly(clio);
    }

    @Test
    void ignoreUnVehiculeSansAgenceQuandOnFiltreParAgence() {
        Vehicule sansAgence = Donnees.vehicule(5L, "EE-555-EE", null, StatutVehicule.DISPONIBLE);
        when(vehicules.findByStatutOrderByMarqueAscModeleAsc(StatutVehicule.DISPONIBLE)).thenReturn(List.of(sansAgence));

        assertThat(service.chercher(null, null, null, null, 1L)).isEmpty();
    }

    @Test
    void chercheLeTexteSansTenirCompteDeLaCasseNiDesEspaces() {
        disponiblesAujourdhui();

        assertThat(service.chercher("  RENAULT cl ", null, null, null, null)).containsExactly(clio);
        assertThat(service.chercher("3008", null, null, null, null)).containsExactly(suv);
        assertThat(service.chercher("inconnu", null, null, null, null)).isEmpty();
    }

    @Test
    void combineLesFiltres() {
        disponiblesAujourdhui();

        assertThat(service.chercher("peugeot", null, null, CategorieVehicule.SUV, 1L)).isEmpty();
        assertThat(service.chercher("peugeot", null, null, CategorieVehicule.SUV, 2L)).containsExactly(suv);
    }

    @Test
    void unVehiculeSansReservationNiTravauxEstLibre() {
        when(reservations.compterChevauchements(1L, DEBUT, FIN, -1L)).thenReturn(0L);
        when(maintenances.existeSurPeriode(1L, DEBUT, FIN)).thenReturn(false);

        assertThat(service.estLibre(clio, DEBUT, FIN)).isTrue();
    }

    @Test
    void unVehiculeReserveNEstPasLibre() {
        when(reservations.compterChevauchements(1L, DEBUT, FIN, -1L)).thenReturn(1L);

        assertThat(service.estLibre(clio, DEBUT, FIN)).isFalse();
    }

    @Test
    void desTravauxPrevusRendentLeVehiculeIndisponible() {
        when(reservations.compterChevauchements(1L, DEBUT, FIN, -1L)).thenReturn(0L);
        when(maintenances.existeSurPeriode(1L, DEBUT, FIN)).thenReturn(true);

        assertThat(service.estLibre(clio, DEBUT, FIN)).isFalse();
    }

    @Test
    void unVehiculeAuGarageNEstJamaisLibre() {
        clio.setStatut(StatutVehicule.MAINTENANCE);

        assertThat(service.estLibre(clio, DEBUT, FIN)).isFalse();
    }
}
