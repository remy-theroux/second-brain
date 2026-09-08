package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.command.NotifyDriveChange;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveFailures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnknownDriveChannelException;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

/**
 * The webhook Google calls. It is public, and declared as such in {@code SecurityConfig}: under
 * {@code /api} everything is denied by default, and an undeclared route answers 401 — which
 * Google reads as a failing channel and closes, without a word.
 */
@RestController
public class NotifyDriveChangeController {

    static final String CHANNEL_ID_HEADER = "X-Goog-Channel-ID";

    static final String CHANNEL_TOKEN_HEADER = "X-Goog-Channel-Token";

    static final String RESOURCE_STATE_HEADER = "X-Goog-Resource-State";

    private static final Logger LOG = LoggerFactory.getLogger(NotifyDriveChangeController.class);

    private final CommandBus commandBus;

    public NotifyDriveChangeController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * The body is never read: Google puts everything in headers, and it is empty on a change as
     * on a sync.
     */
    @PostMapping("/api/drive/notifications")
    public ResponseEntity<Void> notifyDriveChange(
            @RequestHeader(value = CHANNEL_ID_HEADER, required = false) String channelId,
            @RequestHeader(value = CHANNEL_TOKEN_HEADER, required = false) String channelToken,
            @RequestHeader(value = RESOURCE_STATE_HEADER, required = false) String resourceState) {
        commandBus.dispatch(new NotifyDriveChange(channelId, channelToken, resourceState));
        return ResponseEntity.ok().build();
    }

    /** 404 rather than 401: inviting an anonymous caller to authenticate makes no sense here, and
     * it would confirm that the URL is a live webhook. */
    @ExceptionHandler(UnknownDriveChannelException.class)
    public ResponseEntity<Void> unknownChannel() {
        return ResponseEntity.notFound().build();
    }

    /**
     * Google unsubscribes a channel that answers in error repeatedly. A momentarily unreachable
     * database would then cost the channel, where the periodic scan would have caught up anyway:
     * whatever goes wrong on this side, the answer stays a 200.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Void> keepTheChannelAlive(RuntimeException failure) {
        LOG.error(
                "A Drive notification could not be handled, the channel is kept alive: {}",
                DriveFailures.describe(failure));
        return ResponseEntity.ok().build();
    }
}
