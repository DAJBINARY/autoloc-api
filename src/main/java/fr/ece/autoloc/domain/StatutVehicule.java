package fr.ece.autoloc.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StatutVehicule {
    DISPONIBLE("Disponible"),
    LOUE("Loué"),
    MAINTENANCE("En maintenance");

    private final String libelle;
}
