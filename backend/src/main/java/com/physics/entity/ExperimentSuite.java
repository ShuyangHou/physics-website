package com.physics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("experiment_suite")
public class ExperimentSuite {
    
    @TableId(type = IdType.AUTO)
    private Long suiteId;
    
    private String suiteName;
    
    private String experimentIds;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
