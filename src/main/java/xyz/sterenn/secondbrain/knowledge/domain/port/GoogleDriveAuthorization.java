package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.net.URI;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveGrant;

/** The two exchanges with Google that an authorization needs, and nothing more. */
public interface GoogleDriveAuthorization {

    /** Where to send the browser so that the account holder grants a read access. */
    URI consentUrl(DriveAuthorizationState state);

    /** Trades the code of the callback for a lasting authorization and the address that granted it. */
    DriveGrant exchange(String authorizationCode);
}
