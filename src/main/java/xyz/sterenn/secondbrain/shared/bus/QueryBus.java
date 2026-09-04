package xyz.sterenn.secondbrain.shared.bus;

public interface QueryBus {

    <R> R ask(Query<R> query);
}
