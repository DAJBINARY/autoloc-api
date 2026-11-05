package fr.ece.autoloc.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModePaiement {
    CARTE("Carte"),
    ESPECES("Espèces"),
    VIREMENT("Virement");

    private final String libelle;
}
