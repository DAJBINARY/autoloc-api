package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Equipement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipementRepository extends RechercheRepository<Equipement> {

    @Query("""
            select e from Equipement e where lower(e.libelle) like lower(concat('%', :q, '%'))
            """)
    Page<Equipement> rechercher(@Param("q") String q, Pageable pageable);
}
