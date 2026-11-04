package fr.ece.autoloc.config;

import fr.ece.autoloc.domain.CategorieVehicule;
import fr.ece.autoloc.domain.StatutVehicule;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.VehiculeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.List;

@Configuration
@Profile("dev")
@Slf4j
public class DemoDataConfig {

    @Bean
    CommandLineRunner insertDemoVehicules(VehiculeRepository vehiculeRepository) {
        return args -> {
            if (vehiculeRepository.count() > 0) {
                log.debug("Vehicles already present: demo data not inserted");
                return;
            }

            List<Vehicule> vehicules = vehiculeRepository.saveAll(List.of(
                    new Vehicule(null, "AA-123-AA", "Renault", "Clio", CategorieVehicule.CITADINE,
                            new BigDecimal("35.00"), StatutVehicule.DISPONIBLE),
                    new Vehicule(null, "BB-456-BB", "Peugeot", "3008", CategorieVehicule.SUV,
                            new BigDecimal("70.00"), StatutVehicule.DISPONIBLE),
                    new Vehicule(null, "CC-789-CC", "Citroen", "Berlingo", CategorieVehicule.UTILITAIRE,
                            new BigDecimal("55.00"), StatutVehicule.MAINTENANCE)));
            log.info("{} demo vehicles inserted", vehicules.size());
        };
    }
}
