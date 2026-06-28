package com.physics.dto;

import lombok.Data;
import java.util.List;

/**
 * 教师分配请求DTO
 */
@Data
public class TeacherAssignmentRequest {
    
    /**
     * 学期ID
     */
    private Long semesterId;
    
    /**
     * 实验套ID
     */
    private Long suiteId;
    
    /**
     * 时间段（如：周一上午）
     */
    private String timeSlot;
    
    /**
     * 周次类型：0-单周，1-双周
     */
    private Integer weekType;
    
    /**
     * 教师分配列表
     */
    private List<TeacherAssignmentItem> assignments;
    
    /**
     * 教师分配项
     */
    @Data
    public static class TeacherAssignmentItem {
        
        /**
         * 实验ID
         */
        private Long experimentId;
        
        /**
         * 教师ID
         */
        private Long teacherId;
        
        /**
         * 是否为绪论课：0 不是，1 是
         */
        private Integer isIntroCourse = 0;
    }
}
