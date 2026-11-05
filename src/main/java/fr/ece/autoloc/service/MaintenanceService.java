package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.Maintenance;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.MaintenanceRepository;
import fr.ece.autoloc.repository.VehiculeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class MaintenanceService extends CrudService<Maintenance> {

    private final MaintenanceRepository repository;
    private final VehiculeRepository vehicules;

    public MaintenanceService(MaintenanceRepository repository, VehiculeRepository vehicules) {
        super(repository, Sort.by(Sort.Direction.DESC, "dateDebut"));
        this.repository = repository;
        this.vehicules = vehicules;
    }

    @Transactional(readOnly = true)
    public List<Maintenance> listerParVehicule(Long idVehicule) {
        return repository.findByVehicule_IdVehiculeOrderByDateDebutDesc(idVehicule);
    }

    @Override
    @Transactional
    public Maintenance enregistrer(Maintenance maintenance) {
        Maintenance enregistree = super.enregistrer(maintenance);
        synchroniserVehicule(enregistree.getVehicule().getIdVehicule());
        return enregistree;
    }

    @Override
    @Transactional
    public void supprimer(Long id) {
        Vehicule vehicule = repository.findById(id).orElseThrow().getVehicule();
        super.supprimer(id);
        repository.flush();
        if (vehicule != null) {
            synchroniserVehicule(vehicule.getIdVehicule());
        }
    }

    @Transactional
    public void cloturer(Long id) {
        Maintenance maintenance = repository.findById(id).orElseThrow();
        if (maintenance.getDateFin() != null) {
            throw new RegleMetierException("Cette maintenance est déjà clôturée.");
        }
        if (maintenance.getVehicule() == null) {
            throw new RegleMetierException("Renseignez d'abord le véhicule de cette maintenance.");
        }
        maintenance.setDateFin(LocalDate.now());
        repository.flush();
        synchroniserVehicule(maintenance.getVehicule().getIdVehicule());
    }

    /** A rented vehicle keeps its status: its return will take the open maintenance into account. */
    private void synchroniserVehicule(Long idVehicule) {
        Vehicule vehicule = vehicules.findById(idVehicule).orElseThrow();
        if (vehicule.getStatut() == StatutVehicule.LOUE) {
            return;
        }
        vehicule.setStatut(repository.existsByVehicule_IdVehiculeAndDateFinIsNull(idVehicule)
                ? StatutVehicule.MAINTENANCE : StatutVehicule.DISPONIBLE);
    }
}
