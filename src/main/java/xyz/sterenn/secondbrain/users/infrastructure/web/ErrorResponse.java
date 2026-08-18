package xyz.sterenn.secondbrain.users.infrastructure.web;

/**
 * Corps d'un refus qui ne vise aucun champ : la saisie était bonne, c'est le traitement
 * qui n'a pas abouti.
 *
 * @param message message affichable tel quel
 */
public record ErrorResponse(String message) {}
