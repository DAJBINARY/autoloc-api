package fr.ece.autoloc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Repository of an entity whose list screen can be searched with free text.
 */
@NoRepositoryBean
public interface RechercheRepository<T> extends JpaRepository<T, Long> {

    Page<T> rechercher(String q, Pageable pageable);
}
