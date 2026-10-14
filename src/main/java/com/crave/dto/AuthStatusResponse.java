package com.crave.dto;

public class AuthStatusResponse {

    private boolean authenticated;
    private UserResponse user;

    public AuthStatusResponse() {
    }

    public AuthStatusResponse(boolean authenticated, UserResponse user) {
        this.authenticated = authenticated;
        this.user = user;
    }

    public static AuthStatusResponse unauthenticated() {
        return new AuthStatusResponse(false, null);
    }

    public static AuthStatusResponse authenticated(UserResponse user) {
        return new AuthStatusResponse(true, user);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public UserResponse getUser() {
        return user;
    }

    public void setUser(UserResponse user) {
        this.user = user;
    }
}
