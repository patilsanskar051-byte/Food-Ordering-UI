package com.crave.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

/**
 * Contract for verifying Google OAuth 2.0 ID tokens.
 */
public interface GoogleTokenVerifier {

    /**
     * Verifies the given Google ID token string.
     *
     * @param idTokenString Raw ID token JWT
     * @return Verified Google token payload
     */
    GoogleIdToken.Payload verifyToken(String idTokenString);

    /**
     * Returns the configured Google OAuth 2.0 Client ID.
     */
    String getClientId();
}
