package fr.ece.autoloc.domain;

import java.time.format.DateTimeFormatter;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idReservation;

    @NotNull(message = "Ce champ est obligatoire.")
    @Column(nullable = false)
    private LocalDate dateDebut;

    @NotNull(message = "Ce champ est obligatoire.")
    @Column(nullable = false)
    private LocalDate dateFin;

    @NotNull(message = "Ce champ est obligatoire.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutReservation statut;

    @NotNull(message = "Ce champ est obligatoire.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_client")
    private Client client;

    @NotNull(message = "Ce champ est obligatoire.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vehicule")
    private Vehicule vehicule;

    /** Set by the departure and return actions only, never by a form. */
    @Setter(AccessLevel.NONE)
    private LocalDate dateDepart;

    @Setter(AccessLevel.NONE)
    private LocalDate dateRetour;

    public Reservation(Long idReservation, LocalDate dateDebut, LocalDate dateFin, StatutReservation statut,
                       Client client, Vehicule vehicule) {
        this.idReservation = idReservation;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.statut = statut;
        this.client = client;
        this.vehicule = vehicule;
    }

    public void marquerDepart(LocalDate date) {
        this.dateDepart = date;
    }

    public void marquerRetour(LocalDate date) {
        this.dateRetour = date;
    }

    public void reprendreSuivi(Reservation autre) {
        this.dateDepart = autre.dateDepart;
        this.dateRetour = autre.dateRetour;
    }

    public boolean isEnCours() {
        return dateDepart != null && dateRetour == null;
    }

    public String getLibelle() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String nomClient = client == null ? "Client non renseigné" : client.getNomComplet();
        return "N°" + idReservation + " – " + nomClient + " – " + dateDebut.format(format)
                + " au " + dateFin.format(format);
    }
}
