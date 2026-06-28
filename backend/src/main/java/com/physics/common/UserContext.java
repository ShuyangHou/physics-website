package com.physics.common;

public class UserContext {
    private static final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<String> tokenHolder = new ThreadLocal<>();
    
    public static void setCurrentUserId(Long userId) {
        userIdHolder.set(userId);
    }
    
    public static Long getCurrentUserId() {
        return userIdHolder.get();
    }
    
    public static void setToken(String token) {
        tokenHolder.set(token);
    }
    
    public static String getToken() {
        return tokenHolder.get();
    }
    
    public static void clear() {
        userIdHolder.remove();
        tokenHolder.remove();
    }
} 