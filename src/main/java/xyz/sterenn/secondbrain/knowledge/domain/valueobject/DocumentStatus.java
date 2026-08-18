package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

/**
 * Étape d'un document dans la chaîne d'ingestion.
 *
 * <p>Une seule valeur pour l'instant, et c'est volontaire : le dépôt ne déclenche aucun
 * traitement, donc rien ne fait aujourd'hui sortir un document de {@code PENDING}.
 * Déclarer d'avance les états que personne n'atteint ferait croire à un cycle de vie qui
 * n'existe pas. Le ticket qui orchestrera l'ingestion ajoutera les siens.
 */
public enum DocumentStatus {

    /** Déposé, son fichier d'origine conservé, en attente de traitement. */
    PENDING
}
