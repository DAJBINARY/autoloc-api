package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Employe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeRepository extends RechercheRepository<Employe> {

    Optional<Employe> findByIdentifiant(String identifiant);

    boolean existsByMotDePasseIsNotNull();

    @EntityGraph(attributePaths = {"agence"})
    @Query("""
            select e from Employe e left join e.agence a
            where lower(concat(e.nom, ' ', e.prenom, ' ', coalesce(a.nom, ''))) like lower(concat('%', :q, '%'))
            """)
    Page<Employe> rechercher(@Param("q") String q, Pageable pageable);

    List<Employe> findByAgence_IdAgenceOrderByNomAscPrenomAsc(Long idAgence);
}
