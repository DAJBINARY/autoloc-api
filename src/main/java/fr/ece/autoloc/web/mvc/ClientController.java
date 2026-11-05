package fr.ece.autoloc.web.mvc;

import fr.ece.autoloc.domain.Client;
import fr.ece.autoloc.service.ClientService;
import fr.ece.autoloc.service.CompteService;
import fr.ece.autoloc.service.ReservationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;

@Controller
@RequestMapping("/clients")
public class ClientController extends CrudController<Client> {

    private final ReservationService reservations;
    private final CompteService comptes;

    public ClientController(ClientService service, ReservationService reservations, CompteService comptes) {
        super(service, "clients", ClientController::nouveauAvecDate, Client::setIdClient);
        this.reservations = reservations;
        this.comptes = comptes;
    }

    @Override
    protected void validerMetier(Client objet, Errors erreurs) {
        Client existant = objet.getIdClient() == null ? null : charger(objet.getIdClient());
        if (objet.getEmail() != null) {
            objet.setEmail(CompteService.normaliserEmail(objet.getEmail()));
            boolean change = existant == null || !objet.getEmail().equals(existant.getEmail());
            if (change && comptes.identifiantPris(objet.getEmail())) {
                erreurs.rejectValue("email", "doublon", "Cette adresse e-mail est déjà utilisée.");
            }
        }
        // The form knows nothing of the online account: keep it when the client is edited here.
        if (existant != null) {
            objet.changerMotDePasse(existant.getMotDePasse());
        }
    }

    private static Client nouveauAvecDate() {
        Client client = new Client();
        client.setDateInscription(LocalDate.now());
        return client;
    }

    @Override
    protected void completerFiche(Client objet, Model model) {
        model.addAttribute("reservations", reservations.listerParClient(objet.getIdClient()));
    }
}
