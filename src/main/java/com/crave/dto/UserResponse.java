package com.crave.dto;

import com.crave.entity.User;

public class UserResponse {

    private Long id;
    private String email;
    private String fullName;
    private String role;
    private String authProvider;
    private String pictureUrl;

    public UserResponse() {
    }

    public UserResponse(Long id, String email, String fullName, String role, String authProvider, String pictureUrl) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.authProvider = authProvider;
        this.pictureUrl = pictureUrl;
    }

    public static UserResponse fromEntity(User user) {
        if (user == null) return null;
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getAuthProvider(),
                user.getPictureUrl()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }

    public void setPictureUrl(String pictureUrl) {
        this.pictureUrl = pictureUrl;
    }
}
