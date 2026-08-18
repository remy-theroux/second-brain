package xyz.sterenn.secondbrain.users.infrastructure.web;

import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;
import xyz.sterenn.secondbrain.shared.web.ValidationErrorResponse;
import xyz.sterenn.secondbrain.users.application.command.RegisterUser;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidEmailException;
import xyz.sterenn.secondbrain.users.domain.exception.WeakPasswordException;

/**
 * Adapter entrant : traduit une demande JSON en commande, puis les exceptions métier en
 * erreurs de champ. Aucune règle métier ne vit ici.
 *
 * <p>Le {@link BindingResult} est déclaré en paramètre à dessein : sa présence empêche
 * Spring de lever {@code MethodArgumentNotValidException}, donc la traduction des refus
 * reste dans ce contrôleur plutôt que de partir dans un {@code @RestControllerAdvice}
 * qui vaudrait pour tout le contexte.
 *
 * <p>Le {@code 201} n'a ni corps ni en-tête {@code Location} : le compte créé n'est
 * lisible par personne tant qu'il n'est pas vérifié et qu'aucun jeton n'a été délivré.
 */
@RestController
public class RegisterUserController {

    private final CommandBus commandBus;

    public RegisterUserController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping("/api/registrations")
    public ResponseEntity<Object> register(
            @Valid @RequestBody RegistrationRequest registrationRequest, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return unprocessable(champsFautifs(bindingResult));
        }

        try {
            commandBus.dispatch(new RegisterUser(registrationRequest.email(), registrationRequest.password()));
        } catch (InvalidEmailException | EmailAlreadyUsedException e) {
            return unprocessable(Map.of("email", e.getMessage()));
        } catch (WeakPasswordException e) {
            return unprocessable(Map.of("password", e.getMessage()));
        } catch (MailException e) {
            // Le rollback a déjà eu lieu côté SpringCommandBus : aucune faute de champ ici,
            // c'est le canal de notification qui a échoué, pas la saisie de l'utilisateur.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse(
                            "Votre compte n'a pas pu être créé : l'email de vérification n'a pas pu être "
                                    + "envoyé. Réessayez dans quelques instants."));
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private static ResponseEntity<Object> unprocessable(Map<String, String> errors) {
        return ResponseEntity.unprocessableEntity().body(new ValidationErrorResponse(errors));
    }

    /**
     * {@code LinkedHashMap} et non {@code Map.of} : l'ordre de déclaration des champs est
     * conservé, ce qui rend la réponse stable d'une exécution à l'autre.
     */
    private static Map<String, String> champsFautifs(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError erreur : bindingResult.getFieldErrors()) {
            errors.putIfAbsent(erreur.getField(), erreur.getDefaultMessage());
        }
        return errors;
    }
}
