package com.physics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("group_experiment")
public class GroupExperiment {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("group_name")
    private String groupName;
    
    @TableField("experiment_id")
    private Long experimentId;
    
    @TableField(exist = false)
    private String experimentName;
    
    // 第X周
    @TableField("experiment_time")
    private String experimentTime;

    @TableField("time_slot")
    private String timeSlot;
    
    @TableField("teacher_id")
    private Long teacherId;
    
    @TableField(exist = false)
    private String teacherName;
    
    @TableField("location")
    private String location;
    
    @TableField("semester_id")
    private Long semesterId;
    
    @TableField("suite_id")
    private Long suiteId;
    
    @TableField("is_intro_course")
    private Integer isIntroCourse; // 是否为绪论课：0 不是，1 是
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    // 显式getter方法，确保兼容性
    public Long getExperimentId() {
        return experimentId;
    }
    
    public String getExperimentTime() {
        return experimentTime;
    }
    
    public Long getTeacherId() {
        return teacherId;
    }

    public String getTimeSlot() {
        return timeSlot;
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
    
    public String getTeacherName() {
        return teacherName;
    }
    
    public String getLocation() {
        return location;
    }
    
    // 显式setter方法，确保兼容性
    public void setExperimentId(Long experimentId) {
        this.experimentId = experimentId;
    }
    
    public void setExperimentTime(String experimentTime) {
        this.experimentTime = experimentTime;
    }
    
    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public void setSemesterId(Long semesterId) {
        this.semesterId = semesterId;
    }
    
    public void setSuiteId(Long suiteId) {
        this.suiteId = suiteId;
    }
    
    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }
    
    public String getExperimentName() {
        return experimentName;
    }
    
    public void setExperimentName(String experimentName) {
        this.experimentName = experimentName;
    }
}
