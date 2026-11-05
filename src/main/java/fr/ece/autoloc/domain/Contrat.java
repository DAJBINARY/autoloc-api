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
@Table(name = "contrat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Contrat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idContrat;

    @NotNull(message = "Ce champ est obligatoire.")
    @Column(nullable = false)
    private LocalDate dateSignature;

    @NotNull(message = "Ce champ est obligatoire.")
    @Positive(message = "Le montant doit être supérieur à 0.")
    @Digits(integer = 8, fraction = 2, message = "8 chiffres et 2 décimales maximum.")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montantTotal;

    @Column(nullable = false)
    private boolean valide;

    @NotNull(message = "Ce champ est obligatoire.")
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_reservation", unique = true)
    private Reservation reservation;

    /** Empty for a contract signed online by the client. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_employe")
    private Employe employe;

    public String getLibelle() {
        String nomClient = reservation == null || reservation.getClient() == null
                ? "Client non renseigné" : reservation.getClient().getNomComplet();
        return "Contrat n°" + idContrat + " – " + nomClient;
    }
}
