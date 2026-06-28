package com.physics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    
    @TableId(type = IdType.AUTO)
    private Long userId;
    
    private String username;
    
    private String password;
    
    private String realName;
    
    // 移除 email 与 phone 字段以适配数据库删除
    
    private String userType;
    
    private String schoolId;
    
    private String classId;
    
    private String major;
    
    private String groupName;
    
    // 分组相关字段：用于区分单周和双周的分组
    private Long semesterId;  // 学期ID（用于分组）
    private Long suiteId;     // 实验套ID（用于分组）
    private Integer weekType; // 周类型：0-单周，1-双周（用于分组）
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    // 显式getter方法，确保兼容性
    public Long getUserId() {
        return userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getRealName() {
        return realName;
    }
    
    public String getUserType() {
        return userType;
    }
    
    public String getSchoolId() {
        return schoolId;
    }
    
    public String getClassId() {
        return classId;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public Long getSemesterId() {
        return semesterId;
    }
    
    public Long getSuiteId() {
        return suiteId;
    }
    
    public Integer getWeekType() {
        return weekType;
    }
    
    public String getPassword() {
        return password;
    }
    
    // 显式setter方法，确保兼容性
    public void setUsername(String username) {
        this.username = username;
    }
    
    public void setPassword(String password) {
        this.password = password;
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
    
    public void setSemesterId(Long semesterId) {
        this.semesterId = semesterId;
    }
    
    public void setSuiteId(Long suiteId) {
        this.suiteId = suiteId;
    }
    
    public void setWeekType(Integer weekType) {
        this.weekType = weekType;
    }
}