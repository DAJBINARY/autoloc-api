package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Employe;
import fr.ece.autoloc.repository.EmployeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeService extends CrudService<Employe> {

    private final EmployeRepository repository;

    public EmployeService(EmployeRepository repository) {
        super(repository, Sort.by("nom", "prenom"));
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Employe> listerParAgence(Long idAgence) {
        return repository.findByAgence_IdAgenceOrderByNomAscPrenomAsc(idAgence);
    }
}
