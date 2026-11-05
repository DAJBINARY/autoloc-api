package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class VehiculeService extends CrudService<Vehicule> {

    private final VehiculeRepository repository;

    public VehiculeService(VehiculeRepository repository) {
        super(repository, Sort.by("marque", "modele"));
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Vehicule> listerParAgence(Long idAgence) {
        return repository.findByAgence_IdAgenceOrderByMarqueAscModeleAsc(idAgence);
    }

    @Transactional(readOnly = true)
    public List<Vehicule> listerParEquipement(Long idEquipement) {
        return repository.findByEquipements_IdEquipementOrderByMarqueAscModeleAsc(idEquipement);
    }

    @Transactional(readOnly = true)
    public List<Vehicule> listerDisponibles(LocalDate debut, LocalDate fin) {
        return repository.trouverDisponibles(debut, fin);
    }

    @Transactional(readOnly = true)
    public boolean placeOccupee(Long idAgence, Integer place, Long idExclu) {
        return repository.existsByAgence_IdAgenceAndPlaceParkingAndIdVehiculeNot(idAgence, place, idExclu == null ? -1L : idExclu);
    }

    @Transactional(readOnly = true)
    public List<Vehicule> listerParStatut(StatutVehicule statut) {
        return repository.findByStatutOrderByMarqueAscModeleAsc(statut);
    }

    @Transactional(readOnly = true)
    public long compterParStatut(StatutVehicule statut) {
        return repository.countByStatut(statut);
    }
}
