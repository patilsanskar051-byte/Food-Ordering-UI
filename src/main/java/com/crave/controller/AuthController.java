package com.crave.controller;

import com.crave.dto.*;
import com.crave.service.AuthService;
import com.crave.service.GoogleTokenVerifier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * REST controller for authentication endpoints:
 * - Local registration & login
 * - Google OAuth ID-token verification
 * - Session status (/api/auth/me)
 * - Logout
 * - Client configuration (/api/auth/config)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthController(
            AuthService authService,
            GoogleTokenVerifier googleTokenVerifier) {
        this.authService = authService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    /**
     * POST /api/auth/register
     * Register a new user with full name, email, and password.
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        UserResponse response = authService.register(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/auth/login
     * Authenticate an existing user with email and password.
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        UserResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/google
     * Verifies Google Identity Services ID token credential and logs in / registers user.
     */
    @PostMapping("/google")
    public ResponseEntity<UserResponse> googleSignIn(
            @Valid @RequestBody GoogleAuthRequest request,
            HttpServletRequest httpRequest) {
        UserResponse response = authService.authenticateGoogle(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/auth/me
     * Checks whether the current HTTP session is authenticated and returns user details.
     */
    @GetMapping("/me")
    public ResponseEntity<AuthStatusResponse> getCurrentUser(HttpSession session) {
        Optional<UserResponse> userOpt = authService.getCurrentUser(session);
        if (userOpt.isPresent()) {
            return ResponseEntity.ok(AuthStatusResponse.authenticated(userOpt.get()));
        } else {
            return ResponseEntity.ok(AuthStatusResponse.unauthenticated());
        }
    }

    /**
     * POST /api/auth/logout
     * Invalidates the server-side session and clears authentication cookies.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        authService.logout(session);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Successfully logged out of Crave."
        ));
    }

    /**
     * GET /api/auth/config
     * Returns public configuration required by frontend (e.g., Google OAuth Client ID).
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getAuthConfig() {
        return ResponseEntity.ok(Map.of(
                "googleClientId", googleTokenVerifier.getClientId()
        ));
    }
}
