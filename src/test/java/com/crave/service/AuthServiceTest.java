package com.crave.service;

import com.crave.dto.GoogleAuthRequest;
import com.crave.dto.LoginRequest;
import com.crave.dto.RegisterRequest;
import com.crave.dto.UserResponse;
import com.crave.entity.User;
import com.crave.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private GoogleTokenVerifier googleTokenVerifier;
    private PasswordEncoder passwordEncoder;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        googleTokenVerifier = Mockito.mock(GoogleTokenVerifier.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        authService = new AuthServiceImpl(userRepository, googleTokenVerifier, passwordEncoder);
    }

    @Test
    @DisplayName("Register: Successfully registers new user with BCrypt hashed password")
    void testRegisterSuccess() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("John Doe");
        req.setEmail("john@example.com");
        req.setPassword("password123");
        req.setConfirmPassword("password123");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedPassword");

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setFullName("John Doe");
        savedUser.setEmail("john@example.com");
        savedUser.setRole("CUSTOMER");
        savedUser.setAuthProvider("LOCAL");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        UserResponse response = authService.register(req, httpRequest);

        assertNotNull(response);
        assertEquals("john@example.com", response.getEmail());
        assertEquals("John Doe", response.getFullName());
        assertEquals("CUSTOMER", response.getRole());
        assertEquals("LOCAL", response.getAuthProvider());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User captured = captor.getValue();
        assertEquals("$2a$10$hashedPassword", captured.getPasswordHash());

        MockHttpSession session = (MockHttpSession) httpRequest.getSession(false);
        assertNotNull(session);
        assertEquals(1L, session.getAttribute(AuthService.SESSION_USER_ID));
        assertEquals("john@example.com", session.getAttribute(AuthService.SESSION_USER_EMAIL));
    }

    @Test
    @DisplayName("Register: Rejects registration if email already exists")
    void testRegisterDuplicateEmail() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Jane Doe");
        req.setEmail("jane@example.com");
        req.setPassword("secret123");
        req.setConfirmPassword("secret123");

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req, httpRequest));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("Register: Rejects registration if passwords do not match")
    void testRegisterPasswordMismatch() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Jane Doe");
        req.setEmail("jane@example.com");
        req.setPassword("secret123");
        req.setConfirmPassword("differentPassword");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req, httpRequest));
        assertTrue(ex.getMessage().contains("do not match"));
    }

    @Test
    @DisplayName("Login: Valid credentials succeed and establish session")
    void testLoginSuccess() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("mypassword");

        User user = new User();
        user.setId(2L);
        user.setFullName("Alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("$2a$10$hashedAlice");
        user.setRole("CUSTOMER");
        user.setAuthProvider("LOCAL");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mypassword", "$2a$10$hashedAlice")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(user);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        UserResponse res = authService.login(req, httpRequest);

        assertNotNull(res);
        assertEquals("alice@example.com", res.getEmail());
        MockHttpSession session = (MockHttpSession) httpRequest.getSession(false);
        assertNotNull(session);
        assertEquals(2L, session.getAttribute(AuthService.SESSION_USER_ID));
    }

    @Test
    @DisplayName("Login: Fails when password does not match")
    void testLoginInvalidPassword() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@example.com");
        req.setPassword("wrongpassword");

        User user = new User();
        user.setEmail("alice@example.com");
        user.setPasswordHash("$2a$10$hashedAlice");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", "$2a$10$hashedAlice")).thenReturn(false);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        assertThrows(SecurityException.class, () -> authService.login(req, httpRequest));
    }

    @Test
    @DisplayName("Login: Fails when email is not found")
    void testLoginUnknownEmail() {
        LoginRequest req = new LoginRequest();
        req.setEmail("nobody@example.com");
        req.setPassword("password");

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        assertThrows(SecurityException.class, () -> authService.login(req, httpRequest));
    }

    @Test
    @DisplayName("Google Auth: Successfully registers and signs in new Google user")
    void testGoogleAuthNewUser() {
        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setCredential("valid.google.idtoken");

        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-12345");
        payload.set("email", "googleuser@gmail.com");
        payload.set("name", "Google User");
        payload.set("picture", "https://example.com/avatar.jpg");

        when(googleTokenVerifier.verifyToken("valid.google.idtoken")).thenReturn(payload);
        when(userRepository.findByGoogleSub("google-sub-12345")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("googleuser@gmail.com")).thenReturn(Optional.empty());

        User newUser = new User();
        newUser.setId(10L);
        newUser.setFullName("Google User");
        newUser.setEmail("googleuser@gmail.com");
        newUser.setGoogleSub("google-sub-12345");
        newUser.setPictureUrl("https://example.com/avatar.jpg");
        newUser.setAuthProvider("GOOGLE");
        newUser.setRole("CUSTOMER");

        when(userRepository.save(any(User.class))).thenReturn(newUser);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        UserResponse response = authService.authenticateGoogle(req, httpRequest);

        assertNotNull(response);
        assertEquals("googleuser@gmail.com", response.getEmail());
        assertEquals("Google User", response.getFullName());
        assertEquals("GOOGLE", response.getAuthProvider());
        assertEquals("https://example.com/avatar.jpg", response.getPictureUrl());

        MockHttpSession session = (MockHttpSession) httpRequest.getSession(false);
        assertNotNull(session);
        assertEquals(10L, session.getAttribute(AuthService.SESSION_USER_ID));
    }

    @Test
    @DisplayName("Google Auth: Blocks Google login if account already registered locally (account linking protection)")
    void testGoogleAuthAccountConflict() {
        GoogleAuthRequest req = new GoogleAuthRequest();
        req.setCredential("valid.google.idtoken");

        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-999");
        payload.set("email", "existinglocal@example.com");

        when(googleTokenVerifier.verifyToken("valid.google.idtoken")).thenReturn(payload);
        when(userRepository.findByGoogleSub("google-sub-999")).thenReturn(Optional.empty());

        User existingLocalUser = new User();
        existingLocalUser.setAuthProvider("LOCAL");
        existingLocalUser.setEmail("existinglocal@example.com");
        when(userRepository.findByEmail("existinglocal@example.com")).thenReturn(Optional.of(existingLocalUser));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        SecurityException ex = assertThrows(SecurityException.class, () -> authService.authenticateGoogle(req, httpRequest));
        assertTrue(ex.getMessage().contains("already exists using password login"));
    }

    @Test
    @DisplayName("Me: Returns authenticated user when session is present")
    void testGetCurrentUserAuthenticated() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID, 5L);

        User user = new User();
        user.setId(5L);
        user.setFullName("Session User");
        user.setEmail("session@example.com");
        user.setRole("CUSTOMER");
        user.setAuthProvider("LOCAL");

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        Optional<UserResponse> status = authService.getCurrentUser(session);
        assertTrue(status.isPresent());
        assertEquals("Session User", status.get().getFullName());
    }

    @Test
    @DisplayName("Me: Returns empty when session is empty")
    void testGetCurrentUserUnauthenticated() {
        MockHttpSession session = new MockHttpSession();
        Optional<UserResponse> status = authService.getCurrentUser(session);
        assertTrue(status.isEmpty());
    }

    @Test
    @DisplayName("Logout: Invalidates session")
    void testLogout() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.SESSION_USER_ID, 1L);

        authService.logout(session);
        assertTrue(session.isInvalid());
    }
}
