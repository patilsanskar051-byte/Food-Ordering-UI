package com.crave.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Service for independently verifying Google OAuth 2.0 ID tokens on the backend.
 * Validates cryptographic signature, issuer, expiration, and audience.
 */
@Service
public class GoogleTokenVerifierService implements GoogleTokenVerifier {

    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleTokenVerifierService(
            @Value("${crave.google.client-id:755498069210-a53fiibrvm5gd8vkvkdjv88re3ae5prj.apps.googleusercontent.com}") String clientId) {
        this.clientId = clientId;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    /**
     * Constructor for testing and custom mock verifier injection.
     */
    public GoogleTokenVerifierService(String clientId, GoogleIdTokenVerifier verifier) {
        this.clientId = clientId;
        this.verifier = verifier;
    }

    /**
     * Verifies the given Google ID token string.
     *
     * @param idTokenString Raw ID token JWT from Google Identity Services
     * @return Verified payload containing sub, email, name, etc.
     * @throws SecurityException If the token is invalid, expired, incorrectly signed, or for the wrong audience
     */
    public GoogleIdToken.Payload verifyToken(String idTokenString) {
        if (idTokenString == null || idTokenString.trim().isEmpty()) {
            throw new IllegalArgumentException("Google ID token cannot be empty.");
        }

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString.trim());
            if (idToken == null) {
                throw new SecurityException("Google ID token verification failed: Invalid signature, audience, or token expired.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            if (payload == null || payload.getSubject() == null) {
                throw new SecurityException("Google ID token payload is missing required subject identifier.");
            }

            return payload;
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Failed to verify Google ID token with Google identity servers: " + e.getMessage(), e);
        }
    }

    public String getClientId() {
        return clientId;
    }
}
