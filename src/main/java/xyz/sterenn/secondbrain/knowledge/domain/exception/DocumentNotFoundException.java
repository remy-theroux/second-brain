package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Aucun document de ce propriétaire ne porte cet identifiant.
 *
 * <p>Le document d'autrui lève cette même exception, et c'est délibéré : distinguer
 * « n'existe pas » de « ne vous appartient pas » ferait de la route un oracle d'existence,
 * exactement ce que la vérification d'email évite déjà en confondant ses trois refus.
 */
public class DocumentNotFoundException extends RuntimeException {

    /**
     * Le refus, affichable tel quel.
     *
     * <p>Constante parce que <strong>deux routes le rendent</strong> : la suppression le
     * traduit depuis l'exception, la lecture depuis un {@code Optional} vide — une query ne
     * lève pas. Les deux doivent dire exactement la même chose, sans quoi l'utilisateur
     * croirait à deux causes différentes.
     */
    public static final String MESSAGE = "Ce document est introuvable dans votre base de connaissance.";

    public DocumentNotFoundException() {
        super(MESSAGE);
    }
}
