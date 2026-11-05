package fr.ece.autoloc.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "paiement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPaiement;

    @NotNull(message = "Ce champ est obligatoire.")
    @Positive(message = "Le montant doit être supérieur à 0.")
    @Digits(integer = 8, fraction = 2, message = "8 chiffres et 2 décimales maximum.")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant;

    @NotNull(message = "Ce champ est obligatoire.")
    @Column(nullable = false)
    private LocalDate datePaiement;

    @NotNull(message = "Ce champ est obligatoire.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModePaiement modePaiement;

    @NotNull(message = "Ce champ est obligatoire.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_contrat")
    private Contrat contrat;
}
