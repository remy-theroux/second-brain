package xyz.sterenn.secondbrain.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Page d'accueil publique. Elle n'appartient à aucun bounded context : elle se contente
 * d'orienter le visiteur vers les points d'entrée de l'application.
 */
@Controller
public class ShowHomeController {

    @GetMapping("/")
    public String showHome() {
        return "home";
    }
}
