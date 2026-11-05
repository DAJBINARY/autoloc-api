package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "maintenance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Maintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMaintenance;

    @NotNull(message = "Ce champ est obligatoire.")
    @Column(nullable = false)
    private LocalDate dateDebut;

    private LocalDate dateFin;

    @NotBlank(message = "Ce champ est obligatoire.")
    @Size(max = 500, message = "500 caractères maximum.")
    @Column(nullable = false, length = 500)
    private String description;

    @NotNull(message = "Ce champ est obligatoire.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vehicule")
    private Vehicule vehicule;
}
