package fr.ece.autoloc.unitaire.service;

import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import fr.ece.autoloc.service.PaiementService;
import fr.ece.autoloc.service.VehiculeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behaviour shared by the services, checked through two of them.
 */
@ExtendWith(MockitoExtension.class)
class CrudServiceTest {

    @Mock
    private VehiculeRepository vehicules;
    @Mock
    private PaiementRepository paiements;

    private Pageable pageDemandee(int page) {
        when(vehicules.rechercher(eq("clio"), any())).thenReturn(Page.empty());
        new VehiculeService(vehicules).rechercher("clio", page);
        ArgumentCaptor<Pageable> demande = ArgumentCaptor.forClass(Pageable.class);
        verify(vehicules).rechercher(eq("clio"), demande.capture());
        return demande.getValue();
    }

    @Test
    void chercheParPagesDeQuinzeAvecLeTriDuService() {
        Pageable page = pageDemandee(2);

        assertThat(page.getPageNumber()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(15);
        assertThat(page.getSort()).isEqualTo(Sort.by("marque", "modele"));
    }

    @Test
    void rameneUnePageNegativeALaPremiere() {
        assertThat(pageDemandee(-3).getPageNumber()).isZero();
    }

    @Test
    void uneNouvellePlaceDeParkingNExclutAucunVehicule() {
        when(vehicules.existsByAgence_IdAgenceAndPlaceParkingAndIdVehiculeNot(1L, 7, -1L)).thenReturn(true);

        assertThat(new VehiculeService(vehicules).placeOccupee(1L, 7, null)).isTrue();
    }

    @Test
    void uneModificationDeVehiculeNeSeBloquePasSurSaProprePlace() {
        when(vehicules.existsByAgence_IdAgenceAndPlaceParkingAndIdVehiculeNot(1L, 7, 3L)).thenReturn(false);

        assertThat(new VehiculeService(vehicules).placeOccupee(1L, 7, 3L)).isFalse();
    }

    @Test
    void leTotalDUnContratPeutLaisserDeCoteLePaiementModifie() {
        PaiementService service = new PaiementService(paiements);
        when(paiements.totalPourContrat(1L, -1L)).thenReturn(new BigDecimal("100"));
        when(paiements.totalPourContrat(1L, 9L)).thenReturn(new BigDecimal("60"));

        assertThat(service.totalPourContrat(1L)).isEqualByComparingTo("100");
        assertThat(service.totalPourContrat(1L, 9L)).isEqualByComparingTo("60");
    }
}
