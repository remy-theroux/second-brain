package xyz.sterenn.secondbrain.knowledge.infrastructure.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import xyz.sterenn.secondbrain.knowledge.application.command.RenewDriveChannels;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

/**
 * The clock of the subscriptions. Unlike the synchronisation round, this one does the work on
 * its thread rather than announcing it: renewing is a handful of calls to Google, and a round
 * that overlapped itself would open a second channel for a connection that has one.
 * {@code fixedDelay} is therefore what keeps two rounds apart here.
 */
@Configuration(proxyBeanMethods = false)
@Profile("worker")
@EnableScheduling
class DriveChannelRenewalScheduler {

    private final CommandBus commandBus;

    DriveChannelRenewalScheduler(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @Scheduled(
            fixedDelayString = "${secondbrain.drive.channel-renewal-interval}",
            initialDelayString = "${secondbrain.drive.channel-renewal-interval}")
    void renewTheChannels() {
        commandBus.dispatch(new RenewDriveChannels());
    }
}
