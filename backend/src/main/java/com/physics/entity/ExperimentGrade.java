package com.physics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("experiment_grade")
public class ExperimentGrade {
    
    @TableId(type = IdType.AUTO)
    private Long gradeId;
    
    private Long userId;
    
    private Long experimentId;
    
    // 小组名称用于归属和筛选
    private String groupName;
    
    // 授课教师、学期、实验套
    private Long teacherId;
    
    private Long semesterId;
    
    private Long suiteId;
    
    // 单一成绩分数，支持Double和String("N")
    private Object score;

    private String remark;

    private Boolean isLocked;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public void setGradeId(Long gradeId) {
        this.gradeId = gradeId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}