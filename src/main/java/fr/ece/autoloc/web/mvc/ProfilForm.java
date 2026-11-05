package fr.ece.autoloc.web.mvc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfilForm {

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    private String prenom;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 50, message = "50 caractères maximum.")
    private String nom;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 20, message = "20 caractères maximum.")
    private String telephone;
}
