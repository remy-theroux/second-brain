package xyz.sterenn.secondbrain.users.infrastructure.web;

import java.util.Map;

/**
 * Corps d'un refus de saisie : un message par champ fautif, affichable tel quel sous le
 * champ concerné.
 *
 * <p>C'est la forme d'erreur <em>de ce projet</em>. {@code /api/token} ne la suit pas et
 * répond {@code {error, error_description}} : cette forme-là lui est imposée par RFC 6749,
 * dont il imite le {@code password grant}. Toute route future suit celle-ci.
 *
 * @param errors nom du champ → message de refus
 */
public record ValidationErrorResponse(Map<String, String> errors) {
}
