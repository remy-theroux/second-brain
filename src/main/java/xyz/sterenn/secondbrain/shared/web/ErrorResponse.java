package xyz.sterenn.secondbrain.shared.web;

/**
 * Corps d'un refus qui ne vise aucun champ : la saisie était bonne, c'est le traitement
 * qui n'a pas abouti, ou la demande elle-même qui n'était pas recevable.
 *
 * @param message message affichable tel quel
 */
public record ErrorResponse(String message) {}
