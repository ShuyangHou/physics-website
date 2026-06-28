package com.physics.dto;

import lombok.Data;

/**
 * 学生成绩信息DTO - 用于横屏显示优化
 */
@Data
public class StudentGradeInfoDTO {
    
    /**
     * 学生ID
     */
    private Long userId;
    
    /**
     * 学号
     */
    private String schoolId;
    
    /**
     * 姓名
     */
    private String studentName;
    
    /**
     * 班级
     */
    private String classId;
    
    /**
     * 小组
     */
    private String groupName;
    
    /**
     * 成绩
     */
    private Double score;
    
    /**
     * 成绩ID
     */
    private Long gradeId;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 序号 - 用于表格显示
     */
    private Integer index;
    
    // 显式getter方法，确保兼容性
    public Long getUserId() {
        return userId;
    }
    
    public String getSchoolId() {
        return schoolId;
    }
    
    public String getStudentName() {
        return studentName;
    }
    
    public String getClassId() {
        return classId;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public Double getScore() {
        return score;
    }
    
    public Long getGradeId() {
        return gradeId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public Integer getIndex() {
        return index;
    }
}
