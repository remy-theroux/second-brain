package xyz.sterenn.secondbrain.users.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.application.command.VerifyAccount;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidVerificationLinkException;

/**
 * Adapter entrant de la route de vérification. Il traduit les paramètres du lien en
 * commande, puis les refus métier en message affichable. Aucune règle métier ici.
 *
 * <p>Les paramètres sont optionnels et vides par défaut : un lien tronqué doit donner la
 * même page de refus qu'un lien falsifié, pas une erreur 400.
 */
@Controller
public class VerifyAccountController {

    private final CommandBus commandBus;

    public VerifyAccountController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @GetMapping("/verification")
    public String verify(
            @RequestParam(name = "compte", defaultValue = "") String compte,
            @RequestParam(name = "jeton", defaultValue = "") String jeton,
            Model model
    ) {
        try {
            commandBus.dispatch(new VerifyAccount(compte, jeton));
            model.addAttribute("verifie", true);
        } catch (InvalidVerificationLinkException
                 | ExpiredVerificationLinkException
                 | AlreadyUsedVerificationLinkException e) {
            model.addAttribute("verifie", false);
            model.addAttribute("erreur", e.getMessage());
        }
        return "verification";
    }
}
