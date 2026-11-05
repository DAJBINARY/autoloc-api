package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Agence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgenceRepository extends RechercheRepository<Agence> {

    @Query("""
            select a from Agence a where lower(concat(a.nom, ' ', a.ville, ' ', a.adresse)) like lower(concat('%', :q, '%'))
            """)
    Page<Agence> rechercher(@Param("q") String q, Pageable pageable);
}
