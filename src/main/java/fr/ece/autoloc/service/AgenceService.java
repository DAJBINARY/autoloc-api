package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Agence;
import fr.ece.autoloc.repository.AgenceRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AgenceService extends CrudService<Agence> {

    public AgenceService(AgenceRepository repository) {
        super(repository, Sort.by("nom"));
    }
}
