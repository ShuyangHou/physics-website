package com.physics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("experiment")
public class Experiment {
    
    @TableId(type = IdType.AUTO)
    private Long experimentId;
    
    private String experimentName;
    
    private String location;
    
    private String filePath;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
} 