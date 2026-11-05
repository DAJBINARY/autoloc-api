package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "client")
@Getter
@Setter
@NoArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idClient;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    @Column(nullable = false, length = 50)
    private String nom;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    @Column(nullable = false, length = 50)
    private String prenom;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Email(message = "Adresse e-mail invalide.")
    @Size(max = 100, message = "100 caractères maximum.")
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 20, message = "20 caractères maximum.")
    @Column(nullable = false, length = 20)
    private String telephone;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 30, message = "30 caractères maximum.")
    @Column(nullable = false, unique = true, length = 30)
    private String numPermis;

    @NotNull(message = "Ce champ est obligatoire.")
    @Column(nullable = false)
    private LocalDate dateInscription;

    /** Password hash of a client who signed up online: no setter, so a form can never write it. */
    @Setter(AccessLevel.NONE)
    private String motDePasse;

    public Client(Long idClient, String nom, String prenom, String email, String telephone, String numPermis,
                  LocalDate dateInscription) {
        this.idClient = idClient;
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.telephone = telephone;
        this.numPermis = numPermis;
        this.dateInscription = dateInscription;
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
