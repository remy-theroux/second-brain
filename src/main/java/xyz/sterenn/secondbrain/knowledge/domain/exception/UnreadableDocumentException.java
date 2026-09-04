package xyz.sterenn.secondbrain.knowledge.domain.exception;

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
