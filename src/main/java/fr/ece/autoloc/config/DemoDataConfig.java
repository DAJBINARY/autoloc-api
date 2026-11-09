package fr.ece.autoloc.config;

import fr.ece.autoloc.domain.*;
import fr.ece.autoloc.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DemoDataConfig {

    private static final String EMAIL_CLIENT_DEMO = "alice.durand@example.com";
    private static final int FLOTTE_DEMO = 9;
    private static final String RENAULT = "Renault";

    private final AgenceRepository agences;
    private final EmployeRepository employes;
    private final EquipementRepository equipements;
    private final ClientRepository clients;
    private final VehiculeRepository vehicules;
    private final ReservationRepository reservations;
    private final ContratRepository contrats;
    private final PaiementRepository paiements;
    private final MaintenanceRepository maintenances;
    private final PasswordEncoder encodeur;

    /** Password of every demo login, read from application-dev.properties rather than written in the code. */
    @Value("${autoloc.demo.mot-de-passe}")
    private String motDePasseDemo;

    /** Also runs on a database seeded by an earlier version: gives it parking places and a client login. */
    @Bean
    @Order(2)
    CommandLineRunner completerDemoData() {
        return args -> {
            clients.findByEmail(EMAIL_CLIENT_DEMO).filter(client -> !client.aUnCompte()).ifPresent(client -> {
                client.changerMotDePasse(encodeur.encode(motDePasseDemo));
                clients.save(client);
            });
            ajouterVehiculesDemo();
            Map<Long, Set<Integer>> occupees = new HashMap<>();
            List<Vehicule> flotte = vehicules.findAll();
            flotte.stream().filter(vehicule -> vehicule.getAgence() != null && vehicule.getPlaceParking() != null)
                    .forEach(vehicule -> occupees.computeIfAbsent(vehicule.getAgence().getIdAgence(), id -> new HashSet<>())
                            .add(vehicule.getPlaceParking()));
            for (Vehicule vehicule : flotte) {
                if (vehicule.getAgence() != null && vehicule.getPlaceParking() == null) {
                    Set<Integer> places = occupees.computeIfAbsent(vehicule.getAgence().getIdAgence(), id -> new HashSet<>());
                    int place = 4;
                    while (!places.add(place)) {
                        place += 3;
                    }
                    vehicule.setPlaceParking(place);
                    vehicules.save(vehicule);
                }
            }
            log.info("Demo client login: {}, password {}", EMAIL_CLIENT_DEMO, motDePasseDemo);
        };
    }

    @Bean
    @Order(1)
    CommandLineRunner insertDemoData() {
        return args -> {
            if (agences.count() > 0) {
                log.debug("Agencies already present: demo data not inserted");
                return;
            }

            LocalDate aujourdhui = LocalDate.now(Horloge.ZONE);
            Agence paris = agences.save(new Agence(null, "AutoLoc Paris", "Paris", "37 quai de Grenelle", "01 44 00 00 00"));
            Agence lyon = agences.save(new Agence(null, "AutoLoc Lyon", "Lyon", "12 rue de la République", "04 72 00 00 00"));

            Employe agent = employes.save(compte(new Employe(null, "Martin", "Sophie", RoleEmploye.AGENT, paris), "agent"));
            employes.save(compte(new Employe(null, "Bernard", "Lucas", RoleEmploye.MANAGER, paris), "manager"));
            employes.save(new Employe(null, "Petit", "Emma", RoleEmploye.AGENT, lyon));

            List<Equipement> options = equipements.saveAll(List.of(
                    new Equipement(null, "GPS"),
                    new Equipement(null, "Siège enfant"),
                    new Equipement(null, "Chaînes neige"),
                    new Equipement(null, "Attelage")));

            Client alice = clients.save(new Client(null, "Durand", "Alice", EMAIL_CLIENT_DEMO,
                    "06 11 22 33 44", "PERMIS-001", aujourdhui.minusMonths(6)));
            Client karim = clients.save(new Client(null, "Benali", "Karim", "karim.benali@example.com",
                    "06 55 66 77 88", "PERMIS-002", aujourdhui.minusMonths(2)));

            // Vehicles inserted by an earlier version of this class are kept and linked to an agency.
            List<Vehicule> flotte = new ArrayList<>(vehicules.findAll());
            if (flotte.isEmpty()) {
                flotte.add(vehicule("AA-123-AA", RENAULT, "Clio", CategorieVehicule.CITADINE, "35.00", StatutVehicule.DISPONIBLE));
                flotte.add(vehicule("BB-456-BB", "Peugeot", "3008", CategorieVehicule.SUV, "70.00", StatutVehicule.DISPONIBLE));
                flotte.add(vehicule("CC-789-CC", "Citroen", "Berlingo", CategorieVehicule.UTILITAIRE, "55.00", StatutVehicule.MAINTENANCE));
            }
            for (int i = 0; i < flotte.size(); i++) {
                Vehicule vehicule = flotte.get(i);
                if (vehicule.getAgence() == null) {
                    vehicule.setAgence(i % 2 == 0 ? paris : lyon);
                }
                vehicule.setEquipements(new HashSet<>(Set.of(options.get(i % options.size()))));
            }
            Vehicule premier = flotte.get(0);
            Vehicule dernier = flotte.get(flotte.size() - 1);
            dernier.setStatut(StatutVehicule.MAINTENANCE);
            premier.setStatut(StatutVehicule.LOUE);
            flotte = vehicules.saveAll(flotte);
            premier = flotte.get(0);
            dernier = flotte.get(flotte.size() - 1);

            Reservation enCours = new Reservation(null, aujourdhui.minusDays(1), aujourdhui.plusDays(3),
                    StatutReservation.CONFIRMEE, alice, premier);
            enCours.marquerDepart(aujourdhui.minusDays(1));
            enCours = reservations.save(enCours);
            reservations.save(new Reservation(null, aujourdhui.plusDays(5), aujourdhui.plusDays(8),
                    StatutReservation.EN_ATTENTE, karim, premier));
            reservations.save(new Reservation(null, aujourdhui.plusDays(10), aujourdhui.plusDays(12),
                    StatutReservation.CONFIRMEE, alice, flotte.get(Math.min(1, flotte.size() - 1))));

            BigDecimal montant = premier.getTarifJournalier().multiply(BigDecimal.valueOf(5));
            Contrat contrat = contrats.save(new Contrat(null, aujourdhui.minusDays(1), montant, true, enCours, agent));
            paiements.save(new Paiement(null, montant.divide(BigDecimal.valueOf(2)), aujourdhui.minusDays(1),
                    ModePaiement.CARTE, contrat));

            maintenances.save(new Maintenance(null, aujourdhui.minusDays(2), null, "Révision des freins", dernier));

            log.info("Demo data inserted for {} vehicles. Demo logins: manager and agent, password {}",
                    flotte.size(), motDePasseDemo);
        };
    }

    /** Enough available vehicles for the catalogue to be worth showing. */
    private void ajouterVehiculesDemo() {
        List<Agence> liste = agences.findAll();
        if (liste.isEmpty() || vehicules.count() >= FLOTTE_DEMO) {
            return;
        }
        List<Vehicule> ajouts = List.of(
                vehicule("DD-101-DD", "Fiat", "500", CategorieVehicule.CITADINE, "32.00", StatutVehicule.DISPONIBLE),
                vehicule("EE-202-EE", RENAULT, "Megane", CategorieVehicule.BERLINE, "49.00", StatutVehicule.DISPONIBLE),
                vehicule("FF-303-FF", "Toyota", "RAV4", CategorieVehicule.SUV, "79.00", StatutVehicule.DISPONIBLE),
                vehicule("GG-404-GG", "Dacia", "Duster", CategorieVehicule.SUV, "58.00", StatutVehicule.DISPONIBLE),
                vehicule("HH-505-HH", "Peugeot", "508", CategorieVehicule.BERLINE, "65.00", StatutVehicule.DISPONIBLE),
                vehicule("JJ-606-JJ", RENAULT, "Master", CategorieVehicule.UTILITAIRE, "89.00", StatutVehicule.DISPONIBLE));
        for (int i = 0; i < ajouts.size(); i++) {
            Vehicule ajout = ajouts.get(i);
            if (vehicules.count() < FLOTTE_DEMO && !vehicules.existsByImmatriculation(ajout.getImmatriculation())) {
                ajout.setAgence(liste.get(i % liste.size()));
                vehicules.save(ajout);
            }
        }
    }

    private Employe compte(Employe employe, String identifiant) {
        employe.setIdentifiant(identifiant);
        employe.changerMotDePasse(encodeur.encode(motDePasseDemo));
        return employe;
    }

    private static Vehicule vehicule(String immatriculation, String marque, String modele, CategorieVehicule categorie,
                                     String tarif, StatutVehicule statut) {
        Vehicule vehicule = new Vehicule();
        vehicule.setImmatriculation(immatriculation);
        vehicule.setMarque(marque);
        vehicule.setModele(modele);
        vehicule.setCategorie(categorie);
        vehicule.setTarifJournalier(new BigDecimal(tarif));
        vehicule.setStatut(statut);
        return vehicule;
    }
}
