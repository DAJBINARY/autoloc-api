package fr.ece.autoloc.service;

import fr.ece.autoloc.repository.RechercheRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Create, read, update and delete operations shared by the business services.
 */
public abstract class CrudService<T> {

    private static final int TAILLE_PAGE = 15;

    private final RechercheRepository<T> repository;
    private final Sort tri;

    protected CrudService(RechercheRepository<T> repository, Sort tri) {
        this.repository = repository;
        this.tri = tri;
    }

    @Transactional(readOnly = true)
    public List<T> lister() {
        return repository.findAll(tri);
    }

    @Transactional(readOnly = true)
    public Page<T> rechercher(String q, int page) {
        return repository.rechercher(q, PageRequest.of(Math.max(page, 0), TAILLE_PAGE, tri));
    }

    @Transactional(readOnly = true)
    public Optional<T> trouver(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public long compter() {
        return repository.count();
    }

    @Transactional
    public T enregistrer(T entite) {
        return repository.save(entite);
    }

    @Transactional
    public void supprimer(Long id) {
        repository.deleteById(id);
    }
}
