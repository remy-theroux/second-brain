package xyz.sterenn.secondbrain.knowledge.infrastructure.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import xyz.sterenn.secondbrain.knowledge.application.command.RequestDriveSynchronisation;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

/**
 * The clock, and nothing else. It announces a round, it does not run one: the work belongs to the
 * listener, which consumes one message at a time, so two rounds never overlap — and a scheduled
 * method that synchronised on the spot would hold its thread for the whole round.
 *
 * <p>{@code fixedDelay} therefore keeps nothing from overlapping here, and is no different from a
 * {@code fixedRate}: this method publishes and hands its thread straight back. What serialises the
 * rounds is the {@code concurrency: 1} of the listener, and the price of that is a queue: a round
 * lasting longer than the interval piles requests up without a bound, and the worker then
 * synchronises without pause.
 */
@Configuration(proxyBeanMethods = false)
@Profile("worker")
@EnableScheduling
class DriveSynchronisationScheduler {

    private final CommandBus commandBus;

    DriveSynchronisationScheduler(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @Scheduled(
            fixedDelayString = "${secondbrain.drive.synchronisation-interval}",
            initialDelayString = "${secondbrain.drive.synchronisation-interval}")
    void requestASynchronisation() {
        commandBus.dispatch(new RequestDriveSynchronisation());
    }
}
