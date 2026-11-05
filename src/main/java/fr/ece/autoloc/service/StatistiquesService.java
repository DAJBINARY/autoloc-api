package fr.ece.autoloc.service;

import fr.ece.autoloc.domain.ModePaiement;
import fr.ece.autoloc.domain.Paiement;
import fr.ece.autoloc.domain.StatutReservation;
import fr.ece.autoloc.domain.Vehicule;
import fr.ece.autoloc.repository.PaiementRepository;
import fr.ece.autoloc.repository.ReservationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StatistiquesService {

    private static final DateTimeFormatter FORMAT_MOIS = DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH);

    private final PaiementRepository paiements;
    private final ReservationRepository reservations;

    public StatistiquesService(PaiementRepository paiements, ReservationRepository reservations) {
        this.paiements = paiements;
        this.reservations = reservations;
    }

    /** Amount received per month, oldest first, months without payment included. */
    @Transactional(readOnly = true)
    public List<Mois> encaisseParMois(int nombre) {
        YearMonth courant = YearMonth.now();
        Map<YearMonth, BigDecimal> totaux = new LinkedHashMap<>();
        for (int i = nombre - 1; i >= 0; i--) {
            totaux.put(courant.minusMonths(i), BigDecimal.ZERO);
        }
        for (Paiement paiement : paiements.findByDatePaiementGreaterThanEqual(courant.minusMonths(nombre - 1L).atDay(1))) {
            totaux.computeIfPresent(YearMonth.from(paiement.getDatePaiement()), (mois, total) -> total.add(paiement.getMontant()));
        }
        BigDecimal maximum = totaux.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        List<Mois> resultat = new ArrayList<>();
        totaux.forEach((mois, total) -> resultat.add(new Mois(mois.format(FORMAT_MOIS), total, pourcentage(total, maximum))));
        return resultat;
    }

    @Transactional(readOnly = true)
    public List<Total> encaisseParMode() {
        return paiements.totalParMode().stream()
                .map(ligne -> new Total(((ModePaiement) ligne[0]).getLibelle(), (BigDecimal) ligne[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Nombre> reservationsParStatut() {
        return reservations.compterParStatut().stream()
                .map(ligne -> new Nombre(((StatutReservation) ligne[0]).getLibelle(), (Long) ligne[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Nombre> vehiculesLesPlusReserves(int nombre) {
        return reservations.vehiculesLesPlusReserves(PageRequest.of(0, nombre)).stream()
                .map(ligne -> new Nombre(((Vehicule) ligne[0]).getLibelle(), (Long) ligne[1]))
                .toList();
    }

    private static int pourcentage(BigDecimal valeur, BigDecimal maximum) {
        if (maximum.signum() == 0) {
            return 0;
        }
        return valeur.multiply(BigDecimal.valueOf(100)).divide(maximum, 0, RoundingMode.HALF_UP).intValue();
    }

    public record Mois(String libelle, BigDecimal total, int pourcentage) {
    }

    public record Total(String libelle, BigDecimal total) {
    }

    public record Nombre(String libelle, long nombre) {
    }
}
