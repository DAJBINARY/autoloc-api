package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.repository.PaiementRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class
PaiementService extends CrudService<Paiement> {

    /** Id that no payment has: nothing is left out of the total. */
    private static final long AUCUN_PAIEMENT_EXCLU = -1L;

    private final PaiementRepository repository;

    public PaiementService(PaiementRepository repository) {
        super(repository, Sort.by(Sort.Direction.DESC, "datePaiement"));
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Paiement> listerParContrat(Long idContrat) {
        return repository.findByContrat_IdContratOrderByDatePaiementDesc(idContrat);
    }

    @Transactional(readOnly = true)
    public BigDecimal totalEncaisse() {
        return repository.totalEncaisse();
    }

    @Transactional(readOnly = true)
    public BigDecimal totalPourContrat(Long idContrat) {
        return repository.totalPourContrat(idContrat, AUCUN_PAIEMENT_EXCLU);
    }

    /** Amount already paid on the contract, leaving out the payment being edited. */
    @Transactional(readOnly = true)
    public BigDecimal totalPourContrat(Long idContrat, Long idExclu) {
        return repository.totalPourContrat(idContrat, idExclu == null ? AUCUN_PAIEMENT_EXCLU : idExclu);
    }
}
