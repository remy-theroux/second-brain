package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Aucun document de ce propriétaire ne porte cet identifiant.
 *
 * <p>Le document d'autrui lève cette même exception, et c'est délibéré : distinguer
 * « n'existe pas » de « ne vous appartient pas » ferait de la route un oracle d'existence,
 * exactement ce que la vérification d'email évite déjà en confondant ses trois refus.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException() {
        super("Ce document est introuvable dans votre base de connaissance.");
    }
}
