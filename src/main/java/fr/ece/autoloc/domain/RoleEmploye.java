package fr.ece.autoloc.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleEmploye {
    AGENT("Agent"),
    MANAGER("Manager");

    private final String libelle;
}
