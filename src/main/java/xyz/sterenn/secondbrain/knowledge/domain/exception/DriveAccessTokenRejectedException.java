package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Google refused the access token itself, which the clock still believed alive: an access
 * withdrawn from the account kills it at once. A refusal that survives a fresh token is an
 * anomaly, and this is a {@link GoogleDriveUnavailableException} so that it reads as one.
 */
public class DriveAccessTokenRejectedException extends GoogleDriveUnavailableException {

    public DriveAccessTokenRejectedException(Throwable cause) {
        super(cause);
    }
}
