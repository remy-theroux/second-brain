package xyz.sterenn.secondbrain.knowledge.domain.port;

import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;

/** Trades the lasting authorization of a connection for the short-lived token every Drive call carries. */
public interface GoogleAccessTokens {

    /**
     * @throws DriveAuthorizationRevokedException when Google answers {@code invalid_grant}, the
     *     only signal it gives of an access withdrawn from the account.
     */
    DriveAccessToken forConnection(DriveConnection connection);

    /** Drops whatever token is kept for this connection: the next call buys a fresh one. */
    void invalidate(DriveConnection connection);
}
