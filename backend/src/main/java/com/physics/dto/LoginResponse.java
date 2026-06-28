package com.physics.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private Long userId;
    private String username;
    private String realName;
    private String userType;
    private String schoolId;
    // 由于删除了Group实体，不再使用groupId
    // private Long groupId;
    private String classId;
    private String token;
    
    // Explicit getters (in case Lombok isn't working properly)
    public String getToken() {
        return token;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    // 显式setter方法，确保兼容性
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public void setRealName(String realName) {
        this.realName = realName;
    }
    
    public void setUserType(String userType) {
        this.userType = userType;
    }
    
    public void setSchoolId(String schoolId) {
        this.schoolId = schoolId;
    }
    
    public void setClassId(String classId) {
        this.classId = classId;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
} 