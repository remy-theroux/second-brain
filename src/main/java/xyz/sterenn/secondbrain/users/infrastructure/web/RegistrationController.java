package xyz.sterenn.secondbrain.users.infrastructure.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.application.command.RegisterUser;
import xyz.sterenn.secondbrain.users.domain.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.InvalidEmailException;
import xyz.sterenn.secondbrain.users.domain.WeakPasswordException;

/**
 * Adapter entrant : traduit un formulaire HTML en commande, puis les exceptions métier
 * en erreurs de champ. Aucune règle métier ne vit ici.
 */
@Controller
@RequestMapping("/register")
public class RegistrationController {

    private final CommandBus commandBus;

    public RegistrationController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }

    @PostMapping
    public String register(@Valid @ModelAttribute RegistrationForm registrationForm,
                           BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            commandBus.dispatch(
                new RegisterUser(registrationForm.getEmail(), registrationForm.getPassword()));
        } catch (InvalidEmailException | EmailAlreadyUsedException e) {
            bindingResult.rejectValue("email", "email.invalide", e.getMessage());
            return "register";
        } catch (WeakPasswordException e) {
            bindingResult.rejectValue("password", "password.faible", e.getMessage());
            return "register";
        }

        // Redirect-after-post : un rafraîchissement ne renvoie pas le formulaire.
        return "redirect:/register?success";
    }
}
