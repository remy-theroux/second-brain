package xyz.sterenn.secondbrain.shared.bus;

public interface CommandBus {

    void dispatch(Command command);
}
