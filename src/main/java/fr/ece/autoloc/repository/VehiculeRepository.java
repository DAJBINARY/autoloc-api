package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VehiculeRepository extends RechercheRepository<Vehicule> {

    @EntityGraph(attributePaths = {"agence"})
    @Query("""
            select v from Vehicule v left join v.agence a
            where lower(concat(v.immatriculation, ' ', v.marque, ' ', v.modele, ' ', coalesce(a.nom, ''))) like lower(concat('%', :q, '%'))
            """)
    Page<Vehicule> rechercher(@Param("q") String q, Pageable pageable);

    List<Vehicule> findByAgence_IdAgenceOrderByMarqueAscModeleAsc(Long idAgence);

    List<Vehicule> findByEquipements_IdEquipementOrderByMarqueAscModeleAsc(Long idEquipement);

    long countByStatut(StatutVehicule statut);

    boolean existsByImmatriculation(String immatriculation);

    @EntityGraph(attributePaths = "agence")
    List<Vehicule> findByStatutOrderByMarqueAscModeleAsc(StatutVehicule statut);

    boolean existsByAgence_IdAgenceAndPlaceParkingAndIdVehiculeNot(Long idAgence, Integer placeParking, Long idExclu);

    /** Locks the vehicle row so that two clients cannot book the same dates at the same moment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicule v where v.idVehicule = :id")
    Optional<Vehicule> verrouiller(@Param("id") Long id);

    @EntityGraph(attributePaths = "agence")
    @Query("""
            select v from Vehicule v
            where not exists (select 1 from Reservation r
                              where r.vehicule = v
                                and r.statut <> fr.ece.autoloc.domain.StatutReservation.ANNULEE
                                and r.dateDebut <= :fin and r.dateFin >= :debut)
              and not exists (select 1 from Maintenance m
                              where m.vehicule = v and m.dateDebut <= :fin
                                and (m.dateFin is null or m.dateFin >= :debut))
            order by v.marque, v.modele
            """)
    List<Vehicule> trouverDisponibles(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
