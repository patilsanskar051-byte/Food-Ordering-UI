package com.crave.service;

import com.crave.dto.GoogleAuthRequest;
import com.crave.dto.LoginRequest;
import com.crave.dto.RegisterRequest;
import com.crave.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Optional;

/**
 * Service contract for Crave authentication, session management, and Google sign-in.
 */
public interface AuthService {

    String SESSION_USER_ID = "CRAVE_AUTH_USER_ID";
    String SESSION_USER_EMAIL = "CRAVE_AUTH_USER_EMAIL";
    String SESSION_USER_ROLE = "CRAVE_AUTH_USER_ROLE";

    /**
     * Registers a new customer using email and password.
     */
    UserResponse register(RegisterRequest request, HttpServletRequest httpRequest);

    /**
     * Authenticates an existing customer with email and password.
     */
    UserResponse login(LoginRequest request, HttpServletRequest httpRequest);

    /**
     * Authenticates a customer using a verified Google OAuth 2.0 ID token.
     */
    UserResponse authenticateGoogle(GoogleAuthRequest request, HttpServletRequest httpRequest);

    /**
     * Retrieves the current authenticated user from session, if any.
     */
    Optional<UserResponse> getCurrentUser(HttpSession session);

    /**
     * Logs out the user and invalidates the session.
     */
    void logout(HttpSession session);
}
