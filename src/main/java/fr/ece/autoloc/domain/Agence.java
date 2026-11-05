package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "agence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Agence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAgence;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 100, message = "100 caractères maximum.")
    @Column(nullable = false, length = 100)
    private String nom;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 100, message = "100 caractères maximum.")
    @Column(nullable = false, length = 100)
    private String ville;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 255, message = "255 caractères maximum.")
    @Column(nullable = false)
    private String adresse;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 20, message = "20 caractères maximum.")
    @Column(nullable = false, length = 20)
    private String telephone;
}
