package com.physics.dto;

import lombok.Data;
import java.util.List;

/**
 * 课表生成请求DTO
 */
@Data
public class GenerateScheduleRequest {
    
    /**
     * 学期ID
     */
    private Long semesterId;
    
    /**
     * 实验套ID（可选）
     */
    private Long suiteId;
    
    /**
     * 学生组列表
     */
    private List<String> groups;
    
    /**
     * 周次类型：0-单周，1-双周
     */
    private Integer weekType;
}
