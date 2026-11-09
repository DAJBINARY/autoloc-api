package fr.ece.autoloc.unitaire.service;

import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.MaintenanceService;
import fr.ece.autoloc.service.RegleMetierException;
import fr.ece.autoloc.support.Donnees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    private static final Long ID = 5L;
    private static final Long ID_VEHICULE = 2L;
    private static final LocalDate DEBUT = LocalDate.of(2026, 3, 1);

    @Mock
    private MaintenanceRepository repository;
    @Mock
    private VehiculeRepository vehicules;
    @InjectMocks
    private MaintenanceService service;

    private Vehicule vehicule;

    @BeforeEach
    void preparer() {
        vehicule = Donnees.vehicule(ID_VEHICULE, "AA-111-AA", Donnees.agence(1L), StatutVehicule.DISPONIBLE);
        lenient().when(vehicules.findById(ID_VEHICULE)).thenReturn(Optional.of(vehicule));
    }

    private void travauxOuverts(boolean ouverts) {
        when(repository.existsByVehicule_IdVehiculeAndDateFinIsNull(ID_VEHICULE)).thenReturn(ouverts);
    }

    @Test
    void uneMaintenanceOuverteImmobiliseLeVehicule() {
        when(repository.save(any())).thenAnswer(returnsFirstArg());
        travauxOuverts(true);

        service.enregistrer(new Maintenance(null, DEBUT, null, "Freins", vehicule));

        assertThat(vehicule.getStatut()).isEqualTo(StatutVehicule.MAINTENANCE);
    }

    @Test
    void uneMaintenanceDejaTermineeLaisseLeVehiculeDisponible() {
        when(repository.save(any())).thenAnswer(returnsFirstArg());
        travauxOuverts(false);

        service.enregistrer(new Maintenance(null, DEBUT, DEBUT.plusDays(1), "Freins", vehicule));

        assertThat(vehicule.getStatut()).isEqualTo(StatutVehicule.DISPONIBLE);
    }

    @Test
    void unVehiculeLoueResteLoueQuandOnOuvreUneMaintenance() {
        vehicule.setStatut(StatutVehicule.LOUE);
        when(repository.save(any())).thenAnswer(returnsFirstArg());

        service.enregistrer(new Maintenance(null, DEBUT, null, "Freins", vehicule));

        assertThat(vehicule.getStatut()).isEqualTo(StatutVehicule.LOUE);
    }

    @Test
    void laClotureDateLaMaintenanceEtRendLeVehicule() {
        vehicule.setStatut(StatutVehicule.MAINTENANCE);
        Maintenance maintenance = new Maintenance(ID, DEBUT, null, "Freins", vehicule);
        when(repository.findById(ID)).thenReturn(Optional.of(maintenance));
        travauxOuverts(false);

        service.cloturer(ID);

        assertThat(maintenance.getDateFin()).isEqualTo(LocalDate.now());
        assertThat(vehicule.getStatut()).isEqualTo(StatutVehicule.DISPONIBLE);
    }

    @Test
    void leVehiculeResteEnMaintenanceSiDAutresTravauxSontOuverts() {
        vehicule.setStatut(StatutVehicule.MAINTENANCE);
        when(repository.findById(ID)).thenReturn(Optional.of(new Maintenance(ID, DEBUT, null, "Freins", vehicule)));
        travauxOuverts(true);

        service.cloturer(ID);

        assertThat(vehicule.getStatut()).isEqualTo(StatutVehicule.MAINTENANCE);
    }

    @Test
    void neCloturePasDeuxFois() {
        LocalDate fin = DEBUT.plusDays(1);
        Maintenance maintenance = new Maintenance(ID, DEBUT, fin, "Freins", vehicule);
        when(repository.findById(ID)).thenReturn(Optional.of(maintenance));

        assertThatThrownBy(() -> service.cloturer(ID))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("déjà clôturée");
        assertThat(maintenance.getDateFin()).isEqualTo(fin);
    }

    @Test
    void neCloturePasUneMaintenanceSansVehicule() {
        when(repository.findById(ID)).thenReturn(Optional.of(new Maintenance(ID, DEBUT, null, "Freins", null)));

        assertThatThrownBy(() -> service.cloturer(ID))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Renseignez d'abord");
    }

    @Test
    void laSuppressionRendSonStatutAuVehicule() {
        vehicule.setStatut(StatutVehicule.MAINTENANCE);
        when(repository.findById(ID)).thenReturn(Optional.of(new Maintenance(ID, DEBUT, null, "Freins", vehicule)));
        travauxOuverts(false);

        service.supprimer(ID);

        verify(repository).deleteById(ID);
        assertThat(vehicule.getStatut()).isEqualTo(StatutVehicule.DISPONIBLE);
    }

    @Test
    void supprimeUneMaintenanceSansVehicule() {
        when(repository.findById(ID)).thenReturn(Optional.of(new Maintenance(ID, DEBUT, null, "Freins", null)));

        service.supprimer(ID);

        verify(repository).deleteById(ID);
    }
}
