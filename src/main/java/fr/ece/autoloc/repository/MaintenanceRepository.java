package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Maintenance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MaintenanceRepository extends RechercheRepository<Maintenance> {

    @EntityGraph(attributePaths = {"vehicule"})
    @Query("""
            select m from Maintenance m left join m.vehicule v
            where lower(concat(m.description, ' ', coalesce(v.immatriculation, ''), ' ', coalesce(v.marque, ''), ' ',
                               coalesce(v.modele, ''))) like lower(concat('%', :q, '%'))
            """)
    Page<Maintenance> rechercher(@Param("q") String q, Pageable pageable);

    List<Maintenance> findByVehicule_IdVehiculeOrderByDateDebutDesc(Long idVehicule);

    boolean existsByVehicule_IdVehiculeAndDateFinIsNull(Long idVehicule);

    @Query("""
            select count(m) > 0 from Maintenance m
            where m.vehicule.idVehicule = :idVehicule and m.dateDebut <= :fin
              and (m.dateFin is null or m.dateFin >= :debut)
            """)
    boolean existeSurPeriode(@Param("idVehicule") Long idVehicule, @Param("debut") LocalDate debut,
                             @Param("fin") LocalDate fin);

    @Query("""
            select m from Maintenance m
            where m.dateDebut <= :fin and (m.dateFin is null or m.dateFin >= :debut)
            """)
    List<Maintenance> trouverSurPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
