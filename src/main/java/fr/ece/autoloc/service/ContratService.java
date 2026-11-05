package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Contrat;
import fr.ece.autoloc.repository.ContratRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ContratService extends CrudService<Contrat> {

    private final ContratRepository repository;

    public ContratService(ContratRepository repository) {
        super(repository, Sort.by(Sort.Direction.DESC, "dateSignature"));
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<Contrat> trouverParReservation(Long idReservation) {
        return repository.findByReservation_IdReservation(idReservation);
    }

    @Transactional(readOnly = true)
    public List<Contrat> listerParEmploye(Long idEmploye) {
        return repository.findByEmploye_IdEmployeOrderByDateSignatureDesc(idEmploye);
    }
}
