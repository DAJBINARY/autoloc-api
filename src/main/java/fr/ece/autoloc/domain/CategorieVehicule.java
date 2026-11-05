package fr.ece.autoloc.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CategorieVehicule {
    CITADINE("Citadine"),
    BERLINE("Berline"),
    SUV("SUV"),
    UTILITAIRE("Utilitaire");

    private final String libelle;
}
