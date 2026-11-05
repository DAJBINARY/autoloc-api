package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.repository.ClientRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ClientService extends CrudService<Client> {

    private final ClientRepository repository;

    public ClientService(ClientRepository repository) {
        super(repository, Sort.by("nom", "prenom"));
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<Client> trouverParEmail(String email) {
        return repository.findByEmail(email);
    }
}
