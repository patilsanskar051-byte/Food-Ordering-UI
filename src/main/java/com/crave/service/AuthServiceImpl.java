package com.crave.service;

import com.crave.dto.GoogleAuthRequest;
import com.crave.dto.LoginRequest;
import com.crave.dto.RegisterRequest;
import com.crave.dto.UserResponse;
import com.crave.entity.User;
import com.crave.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service implementation for user authentication, registration, BCrypt password hashing,
 * Google ID token verification, and server-side session management.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AuthServiceImpl(
            UserRepository userRepository,
            GoogleTokenVerifier googleTokenVerifier) {
        this.userRepository = userRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Constructor for unit testing with custom password encoder.
     */
    public AuthServiceImpl(
            UserRepository userRepository,
            GoogleTokenVerifier googleTokenVerifier,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (request.getPassword() == null || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email already exists. Please log in.");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = new User(
                normalizedEmail,
                passwordHash,
                request.getFullName(),
                "CUSTOMER",
                "LOCAL"
        );

        User savedUser = userRepository.save(user);

        // Establish secure session with fixation protection
        establishSession(savedUser, httpRequest);

        return UserResponse.fromEntity(savedUser);
    }

    @Override
    @Transactional
    public UserResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new SecurityException("Invalid email or password."));

        if (user.getPasswordHash() == null) {
            throw new SecurityException("This account was created with Google Sign-In. Please sign in using Google.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new SecurityException("Invalid email or password.");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Establish secure session with fixation protection
        establishSession(user, httpRequest);

        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional
    public UserResponse authenticateGoogle(GoogleAuthRequest request, HttpServletRequest httpRequest) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verifyToken(request.getCredential());

        String googleSub = payload.getSubject();
        String email = payload.getEmail() != null ? payload.getEmail().trim().toLowerCase() : null;
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        if (email == null) {
            throw new SecurityException("Google account must provide an email address.");
        }

        User user;
        Optional<User> existingGoogleUser = userRepository.findByGoogleSub(googleSub);

        if (existingGoogleUser.isPresent()) {
            user = existingGoogleUser.get();
            user.setLastLoginAt(LocalDateTime.now());
            if (picture != null) user.setPictureUrl(picture);
            if (name != null && (user.getFullName() == null || user.getFullName().trim().isEmpty())) {
                user.setFullName(name);
            }
            user = userRepository.save(user);
        } else {
            // Check if email already belongs to a local account
            Optional<User> existingEmailUser = userRepository.findByEmail(email);
            if (existingEmailUser.isPresent()) {
                throw new SecurityException("An account with this email already exists using password login. Please log in with your email and password.");
            }

            // Create new Google-authenticated user
            User newUser = new User(
                    email,
                    null, // No local password
                    name != null && !name.trim().isEmpty() ? name : "Google Foodie",
                    "CUSTOMER",
                    "GOOGLE"
            );
            newUser.setGoogleSub(googleSub);
            newUser.setPictureUrl(picture);

            user = userRepository.save(newUser);
        }

        // Establish secure session with fixation protection
        establishSession(user, httpRequest);

        return UserResponse.fromEntity(user);
    }

    @Override
    public Optional<UserResponse> getCurrentUser(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }

        Long userId = (Long) session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return Optional.empty();
        }

        return userRepository.findById(userId).map(UserResponse::fromEntity);
    }

    @Override
    public void logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
    }

    /**
     * Internal helper to establish/rotate a session and prevent session fixation.
     */
    private void establishSession(User user, HttpServletRequest httpRequest) {
        if (httpRequest == null) return;

        // Invalidate old session to prevent fixation
        HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }

        // Create fresh session
        HttpSession newSession = httpRequest.getSession(true);
        newSession.setAttribute(SESSION_USER_ID, user.getId());
        newSession.setAttribute(SESSION_USER_EMAIL, user.getEmail());
        newSession.setAttribute(SESSION_USER_ROLE, user.getRole());
    }

    public PasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }
}
