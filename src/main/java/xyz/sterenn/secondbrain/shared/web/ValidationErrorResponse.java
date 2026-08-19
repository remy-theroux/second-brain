package xyz.sterenn.secondbrain.shared.web;

import java.util.Map;

/**
 * Corps d'un refus de saisie : un message par champ fautif, affichable tel quel sous le
 * champ concerné.
 *
 * <p>C'est la forme d'erreur <em>de ce projet</em>, et c'est ce qui la place ici plutôt que
 * dans un contexte borné : elle vaut pour toute route, présente ou future. {@code
 * /api/token} est la seule exception, et elle ne s'étend pas : cette route répond
 * {@code {error, error_description}} parce que RFC 6749 le lui impose, dont elle imite le
 * {@code password grant}.
 *
 * @param errors nom du champ → message de refus
 */
public record ValidationErrorResponse(Map<String, String> errors) {}
