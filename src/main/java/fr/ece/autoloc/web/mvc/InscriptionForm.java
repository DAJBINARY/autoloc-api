package fr.ece.autoloc.web.mvc;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InscriptionForm {

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    private String prenom;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    private String nom;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Email(message = "Adresse e-mail invalide.")
    @Size(max = 100, message = "100 caractères maximum.")
    private String email;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 20, message = "20 caractères maximum.")
    private String telephone;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 30, message = "30 caractères maximum.")
    private String numPermis;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(min = 8, max = 72, message = "Entre 8 et 72 caractères.")
    private String motDePasse;

    @NotBlank(message = "Ce champ est obligatoire.")
    private String confirmation;
}
