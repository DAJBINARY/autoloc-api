package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientRepository extends RechercheRepository<Client> {

    Optional<Client> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNumPermis(String numPermis);

    @Query("""
            select c from Client c
            where lower(concat(c.nom, ' ', c.prenom, ' ', c.prenom, ' ', c.nom, ' ', c.email, ' ', c.telephone, ' ', c.numPermis))
            like lower(concat('%', :q, '%'))
            """)
    Page<Client> rechercher(@Param("q") String q, Pageable pageable);
}
