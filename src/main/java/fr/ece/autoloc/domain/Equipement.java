package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "equipement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Equipement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idEquipement;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 100, message = "100 caractères maximum.")
    @Column(nullable = false, length = 100)
    private String libelle;
}
