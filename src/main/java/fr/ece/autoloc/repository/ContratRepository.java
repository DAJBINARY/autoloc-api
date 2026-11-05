package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Contrat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContratRepository extends RechercheRepository<Contrat> {

    @EntityGraph(attributePaths = {"reservation.client", "reservation.vehicule", "employe"})
    @Query("""
            select c from Contrat c left join c.reservation r left join r.client cl left join r.vehicule v
            where lower(concat(coalesce(cl.nom, ''), ' ', coalesce(cl.prenom, ''), ' ', coalesce(v.immatriculation, '')))
            like lower(concat('%', :q, '%'))
            """)
    Page<Contrat> rechercher(@Param("q") String q, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"reservation.client"})
    List<Contrat> findAll(Sort sort);

    Optional<Contrat> findByReservation_IdReservation(Long idReservation);

    @EntityGraph(attributePaths = {"reservation.client"})
    List<Contrat> findByEmploye_IdEmployeOrderByDateSignatureDesc(Long idEmploye);
}
