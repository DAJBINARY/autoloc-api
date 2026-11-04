package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Vehicule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehiculeRepository extends JpaRepository<Vehicule, Long> {
}
