package xyz.sterenn.secondbrain.shared.bus;

public class HandlerNotFoundException extends RuntimeException {

    public HandlerNotFoundException(Class<?> messageType) {
        super("No handler registered for " + messageType.getSimpleName());
    }
}
