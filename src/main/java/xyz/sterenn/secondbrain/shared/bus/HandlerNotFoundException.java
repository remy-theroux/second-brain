package xyz.sterenn.secondbrain.shared.bus;

public class HandlerNotFoundException extends RuntimeException {

    public HandlerNotFoundException(Class<?> messageType) {
        super("Aucun handler enregistré pour " + messageType.getSimpleName());
    }
}
