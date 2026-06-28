package com.physics.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GradeDTO {
    
    private Long gradeId;
    
    private Long userId;
    
    private String studentName;
    
    private Long experimentId;
    
    private String experimentName;
    
    private Long scheduleId;
    
    // 由于删除了Group实体，不再使用groupId
    // private Long groupId;
    
    private String groupName;
    
    private Object score;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
} 