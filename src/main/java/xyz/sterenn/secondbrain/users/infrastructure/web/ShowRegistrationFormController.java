package xyz.sterenn.secondbrain.users.infrastructure.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Affiche le formulaire de création de compte. Une seule route, donc aucune dépendance :
 * ce contrôleur ne connaît pas le {@code CommandBus}.
 */
@Controller
public class ShowRegistrationFormController {

    @GetMapping("/register")
    public String showForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }
}
