package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Reservation;
import fr.ece.autoloc.domain.StatutReservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends RechercheRepository<Reservation> {

    @EntityGraph(attributePaths = {"client", "vehicule"})
    @Query("""
            select r from Reservation r left join r.client c left join r.vehicule v
            where lower(concat(coalesce(c.nom, ''), ' ', coalesce(c.prenom, ''), ' ', coalesce(v.immatriculation, ''), ' ',
                               coalesce(v.marque, ''), ' ', coalesce(v.modele, ''))) like lower(concat('%', :q, '%'))
            """)
    Page<Reservation> rechercher(@Param("q") String q, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"client", "vehicule"})
    List<Reservation> findAll(Sort sort);

    @EntityGraph(attributePaths = "vehicule")
    List<Reservation> findByClient_IdClientOrderByDateDebutDesc(Long idClient);

    @EntityGraph(attributePaths = "client")
    List<Reservation> findByVehicule_IdVehiculeOrderByDateDebutDesc(Long idVehicule);

    @EntityGraph(attributePaths = {"client", "vehicule"})
    List<Reservation> findTop5ByDateDebutGreaterThanEqualAndStatutInOrderByDateDebutAsc(
            LocalDate date, Collection<StatutReservation> statuts);

    long countByStatut(StatutReservation statut);

    @EntityGraph(attributePaths = "vehicule.agence")
    List<Reservation> findByClient_EmailOrderByDateDebutDesc(String email);

    @EntityGraph(attributePaths = {"client", "vehicule.agence"})
    Optional<Reservation> findByIdReservationAndClient_Email(Long idReservation, String email);

    long countByDateDepartIsNotNullAndDateRetourIsNull();

    @EntityGraph(attributePaths = "client")
    @Query("""
            select r from Reservation r
            where r.statut <> fr.ece.autoloc.domain.StatutReservation.ANNULEE
              and r.dateDebut <= :fin and r.dateFin >= :debut
            """)
    List<Reservation> trouverSurPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("select r.statut, count(r) from Reservation r group by r.statut")
    List<Object[]> compterParStatut();

    @Query("""
            select r.vehicule, count(r) from Reservation r
            where r.vehicule is not null and r.statut <> fr.ece.autoloc.domain.StatutReservation.ANNULEE
            group by r.vehicule
            order by count(r) desc
            """)
    List<Object[]> vehiculesLesPlusReserves(Pageable pageable);

    @Query("""
            select count(r) from Reservation r
            where r.vehicule.idVehicule = :idVehicule
              and r.statut <> fr.ece.autoloc.domain.StatutReservation.ANNULEE
              and r.dateDebut <= :fin and r.dateFin >= :debut
              and r.idReservation <> :idExclu
            """)
    long compterChevauchements(@Param("idVehicule") Long idVehicule, @Param("debut") LocalDate debut,
                               @Param("fin") LocalDate fin, @Param("idExclu") Long idExclu);

    @EntityGraph(attributePaths = {"client", "vehicule"})
    @Query("""
            select r from Reservation r
            where r.idReservation = :idCourant
               or (r.statut <> fr.ece.autoloc.domain.StatutReservation.ANNULEE
                   and not exists (select 1 from Contrat c where c.reservation = r))
            order by r.dateDebut desc
            """)
    List<Reservation> trouverSansContrat(@Param("idCourant") Long idCourant);
}
