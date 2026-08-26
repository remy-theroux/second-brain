package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

/**
 * Étape d'un document dans la chaîne d'ingestion.
 *
 * <p>Trois valeurs, et c'est tout ce que ce ticket peut honnêtement porter : le texte est
 * extrait, ou il ne l'est pas. RAG-6 ajoutera ce qui suit la vectorisation — probablement
 * un {@code READY} après {@code EXTRACTED}. Ne pas le déclarer d'avance : un état que
 * personne n'atteint fait croire à un cycle de vie qui n'existe pas.
 *
 * <p>{@code FAILED} n'est pas un état terminal. Une réextraction (RAG-7) en repart, et
 * {@code markTextExtracted} efface alors le motif de l'échec précédent.
 */
public enum DocumentStatus {

    /** Déposé, son fichier d'origine conservé, en attente de traitement. */
    PENDING,

    /** Son texte a été extrait et rangé dans un {@code TextExtraction}. */
    EXTRACTED,

    /** Le traitement a échoué ; le motif est lisible sur le document. */
    FAILED
}
