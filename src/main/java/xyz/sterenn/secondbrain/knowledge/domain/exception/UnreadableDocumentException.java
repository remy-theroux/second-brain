package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Le fichier n'a pas pu être ouvert : zip corrompu, PDF tronqué, ou {@code .docx} qui n'est
 * un {@code .docx} que par son extension.
 *
 * <p>Le format se déduit de l'extension au dépôt, faute de mieux ; c'est ici, et seulement
 * ici, qu'on découvre qu'elle mentait.
 */
public class UnreadableDocumentException extends DocumentExtractionException {

    private static final String MESSAGE = "Ce fichier n'a pas pu être lu :"
            + " il est peut-être endommagé, ou son contenu ne correspond pas à son extension.";

    public UnreadableDocumentException(Throwable cause) {
        super(MESSAGE, cause);
    }

    public UnreadableDocumentException() {
        super(MESSAGE);
    }
}
