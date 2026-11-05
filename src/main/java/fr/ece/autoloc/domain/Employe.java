package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "employe")
@Getter
@Setter
@NoArgsConstructor
public class Employe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idEmploye;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    @Column(nullable = false, length = 50)
    private String nom;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    @Column(nullable = false, length = 50)
    private String prenom;

    @NotNull(message = "Ce champ est obligatoire.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleEmploye role;

    @NotNull(message = "Ce champ est obligatoire.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_agence")
    private Agence agence;

    @Size(max = 50, message = "50 caractères maximum.")
    @Column(unique = true, length = 50)
    private String identifiant;

    /** Password hash: it has no setter, so that a submitted form can never write it. */
    @Setter(AccessLevel.NONE)
    private String motDePasse;

    /** Clear password typed in the employee form; never stored. */
    @Transient
    @Size(min = 8, max = 72, message = "Entre 8 et 72 caractères.")
    private String nouveauMotDePasse;

    public Employe(Long idEmploye, String nom, String prenom, RoleEmploye role, Agence agence) {
        this.idEmploye = idEmploye;
        this.nom = nom;
        this.prenom = prenom;
        this.role = role;
        this.agence = agence;
    }

    public void changerMotDePasse(String hash) {
        this.motDePasse = hash;
    }

    public boolean aUnCompte() {
        return motDePasse != null;
    }

    public String getNomComplet() {
        return prenom + " " + nom;
    }
}
