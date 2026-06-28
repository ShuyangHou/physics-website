package com.physics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 额外资料文件实体
 */
@Data
@TableName("extra_file")
public class ExtraFile {
    
    @TableId(value = "file_id", type = IdType.AUTO)
    private Long fileId;
    
    @TableField("file_name")
    private String fileName;
    
    @TableField("file_size")
    private Long fileSize;
    
    @TableField("file_type")
    private String fileType;
    
    @TableField("file_path")
    private String filePath;
    
    @TableField("upload_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadTime;
    
    @TableField("uploader_id")
    private Long uploaderId;
    
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 逻辑删除标记，配合全局 logic-delete-field: deleted 使用。
     * 删除时由 MyBatis-Plus 自动置 1，并在查询时自动过滤。
     */
    @JsonIgnore
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
