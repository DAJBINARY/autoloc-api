package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Equipement;
import fr.ece.autoloc.repository.EquipementRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class EquipementService extends CrudService<Equipement> {

    public EquipementService(EquipementRepository repository) {
        super(repository, Sort.by("libelle"));
    }
}
