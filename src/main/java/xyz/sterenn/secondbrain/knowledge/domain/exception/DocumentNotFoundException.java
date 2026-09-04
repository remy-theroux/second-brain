package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class DocumentNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Ce document est introuvable dans votre base de connaissance.";

    public DocumentNotFoundException() {
        super(MESSAGE);
    }
}
