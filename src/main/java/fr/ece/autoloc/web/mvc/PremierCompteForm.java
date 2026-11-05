package fr.ece.autoloc.web.mvc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * First manager account, created on an application that has no account yet.
 * The agency fields are only used when no agency exists.
 */
@Getter
@Setter
public class PremierCompteForm {

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    private String nom;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    private String prenom;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    private String identifiant;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(min = 8, max = 72, message = "Entre 8 et 72 caractères.")
    private String motDePasse;

    private Long idAgence;

    @Size(max = 100, message = "100 caractères maximum.")
    private String nomAgence;

    @Size(max = 100, message = "100 caractères maximum.")
    private String villeAgence;

    @Size(max = 255, message = "255 caractères maximum.")
    private String adresseAgence;

    @Size(max = 20, message = "20 caractères maximum.")
    private String telephoneAgence;
}
