package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Paiement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PaiementRepository extends RechercheRepository<Paiement> {

    @EntityGraph(attributePaths = {"contrat.reservation.client"})
    @Query("""
            select p from Paiement p left join p.contrat c left join c.reservation r left join r.client cl
            where lower(concat(coalesce(cl.nom, ''), ' ', coalesce(cl.prenom, ''))) like lower(concat('%', :q, '%'))
            """)
    Page<Paiement> rechercher(@Param("q") String q, Pageable pageable);

    List<Paiement> findByContrat_IdContratOrderByDatePaiementDesc(Long idContrat);

    List<Paiement> findByDatePaiementGreaterThanEqual(LocalDate date);

    @Query("select p.modePaiement, sum(p.montant) from Paiement p group by p.modePaiement")
    List<Object[]> totalParMode();

    @Query("select coalesce(sum(p.montant), 0) from Paiement p")
    BigDecimal totalEncaisse();

    @Query("""
            select coalesce(sum(p.montant), 0) from Paiement p
            where p.contrat.idContrat = :idContrat and p.idPaiement <> :idExclu
            """)
    BigDecimal totalPourContrat(@Param("idContrat") Long idContrat, @Param("idExclu") Long idExclu);
}
