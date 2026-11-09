package fr.ece.autoloc.domain;

import java.util.Set;
import java.util.HashSet;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "vehicule", uniqueConstraints = @UniqueConstraint(columnNames = {"id_agence", "place_parking"}))
@Getter
@Setter
@NoArgsConstructor
public class Vehicule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVehicule;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 20, message = "20 caractères maximum.")
    @Column(nullable = false, unique = true, length = 20)
    private String immatriculation;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    @Column(nullable = false, length = 50)
    private String marque;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    @Column(nullable = false, length = 50)
    private String modele;

    @NotNull(message = "Ce champ est obligatoire.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategorieVehicule categorie;

    @NotNull(message = "Ce champ est obligatoire.")
    @Positive(message = "Le montant doit être supérieur à 0.")
    @Digits(integer = 8, fraction = 2, message = "8 chiffres et 2 décimales maximum.")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tarifJournalier;

    @NotNull(message = "Ce champ est obligatoire.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutVehicule statut;

    @NotNull(message = "Ce champ est obligatoire.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_agence")
    private Agence agence;

    @ManyToMany
    @JoinTable(name = "vehicule_equipement",
            joinColumns = @JoinColumn(name = "id_vehicule"),
            inverseJoinColumns = @JoinColumn(name = "id_equipement"))
    private Set<Equipement> equipements = new HashSet<>();

    @Min(value = 1, message = "Le numéro de place commence à 1.")
    @Max(value = 500, message = "500 places maximum.")
    @Column(name = "place_parking")
    private Integer placeParking;

    /** A new vehicle, without equipment yet: the id is given by the database. */
    public Vehicule(String immatriculation, String marque, String modele, CategorieVehicule categorie,
                    BigDecimal tarifJournalier, StatutVehicule statut, Agence agence) {
        this.immatriculation = immatriculation;
        this.marque = marque;
        this.modele = modele;
        this.categorie = categorie;
        this.tarifJournalier = tarifJournalier;
        this.statut = statut;
        this.agence = agence;
    }

    /** Stable index of the body colour used to draw this vehicle in the catalogue. */
    public int getTeinte() {
        return Math.floorMod(immatriculation.hashCode(), 5);
    }

    public String getLibelle() {
        return marque + " " + modele + " (" + immatriculation + ")";
    }
}
