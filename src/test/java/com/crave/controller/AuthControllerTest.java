package com.crave.controller;

import com.crave.dto.LoginRequest;
import com.crave.dto.RegisterRequest;
import com.crave.dto.UserResponse;
import com.crave.service.AuthService;
import com.crave.service.GoogleTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;
    private GoogleTokenVerifier googleTokenVerifier;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        googleTokenVerifier = Mockito.mock(GoogleTokenVerifier.class);
        objectMapper = new ObjectMapper();

        when(googleTokenVerifier.getClientId())
                .thenReturn("755498069210-a53fiibrvm5gd8vkvkdjv88re3ae5prj.apps.googleusercontent.com");

        AuthController controller = new AuthController(authService, googleTokenVerifier);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/auth/config returns the configured Google Client ID")
    void testGetConfig() throws Exception {
        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleClientId").value("755498069210-a53fiibrvm5gd8vkvkdjv88re3ae5prj.apps.googleusercontent.com"));
    }

    @Test
    @DisplayName("POST /api/auth/register succeeds and returns user details")
    void testRegisterSuccess() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("David Miller");
        req.setEmail("david@example.com");
        req.setPassword("pass123");
        req.setConfirmPassword("pass123");

        UserResponse res = new UserResponse(15L, "david@example.com", "David Miller", "CUSTOMER", "LOCAL", null);
        when(authService.register(any(RegisterRequest.class), any(HttpServletRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("david@example.com"))
                .andExpect(jsonPath("$.fullName").value("David Miller"))
                .andExpect(jsonPath("$.authProvider").value("LOCAL"));
    }

    @Test
    @DisplayName("POST /api/auth/login succeeds for valid credentials")
    void testLoginSuccess() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("david@example.com");
        req.setPassword("pass123");

        UserResponse res = new UserResponse(15L, "david@example.com", "David Miller", "CUSTOMER", "LOCAL", null);
        when(authService.login(any(LoginRequest.class), any(HttpServletRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("david@example.com"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 200 with user when authenticated")
    void testGetMeAuthenticated() throws Exception {
        UserResponse res = new UserResponse(15L, "david@example.com", "David Miller", "CUSTOMER", "LOCAL", null);
        when(authService.getCurrentUser(any(HttpSession.class))).thenReturn(Optional.of(res));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value("david@example.com"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 200 with authenticated=false when unauthenticated")
    void testGetMeUnauthenticated() throws Exception {
        when(authService.getCurrentUser(any(HttpSession.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/logout invalidates session and returns success")
    void testLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
