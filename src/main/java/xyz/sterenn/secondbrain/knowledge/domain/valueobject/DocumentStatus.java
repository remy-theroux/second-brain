package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

/**
 * Étape d'un document dans la chaîne d'ingestion.
 *
 * <p>{@code READY} est l'état d'aboutissement : le texte est extrait, découpé, vectorisé, et
 * le document est interrogeable. Il n'a été déclaré qu'au moment où quelqu'un l'atteint — un
 * état que personne n'atteint fait croire à un cycle de vie qui n'existe pas.
 *
 * <p>{@code EXTRACTED} n'est donc plus un aboutissement mais une étape : un document qui s'y
 * arrête a bien du texte, et rien qui permette de l'interroger.
 *
 * <p>{@code FAILED} n'est pas un état terminal. Une réextraction (RAG-7) en repart, et
 * {@code markTextExtracted} efface alors le motif de l'échec précédent.
 */
public enum DocumentStatus {

    /** Déposé, son fichier d'origine conservé, en attente de traitement. */
    PENDING,

    /** Son texte a été extrait et rangé dans un {@code TextExtraction}. */
    EXTRACTED,

    /** Ses extraits sont découpés, vectorisés et rangés : il est interrogeable. */
    READY,

    /** Le traitement a échoué ; le motif est lisible sur le document. */
    FAILED
}
