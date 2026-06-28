package com.physics.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("experiment_schedule")
public class ExperimentSchedule {
    
    @TableId(type = IdType.AUTO)
    private Long scheduleId;
    
    @TableField("teacher_ids")
    private String teacherIds;
    
    @TableField("group_ids")
    private String groupIds;
    
    @TableField("class_ids")
    private String classIds;

    private Long semesterId;

    @TableField("suite_id")
    private Long suiteId;
    
    @TableField("week_type")
    private Integer weekType; // 0 单周，1 双周
    
    private String experimentTime;
    
    // 绪论课教师ID，用于标识负责绪论课的教师
    @TableField("intro_teacher_id")
    private Long introTeacherId;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getTeacherIds() {
        return teacherIds;
    }

    public void setTeacherIds(String teacherIds) {
        this.teacherIds = teacherIds;
    }

    public String getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(String groupIds) {
        this.groupIds = groupIds;
    }

    public String getClassIds() {
        return classIds;
    }

    public void setClassIds(String classIds) {
        this.classIds = classIds;
    }

    public Long getSemesterId() {
        return semesterId;
    }

    public void setSemesterId(Long semesterId) {
        this.semesterId = semesterId;
    }

    public Long getSuiteId() {
        return suiteId;
    }

    public void setSuiteId(Long suiteId) {
        this.suiteId = suiteId;
    }

    public Integer getWeekType() {
        return weekType;
    }

    public void setWeekType(Integer weekType) {
        this.weekType = weekType;
    }

    public String getExperimentTime() {
        return experimentTime;
    }

    public void setExperimentTime(String experimentTime) {
        this.experimentTime = experimentTime;
    }

    public Long getIntroTeacherId() {
        return introTeacherId;
    }

    public void setIntroTeacherId(Long introTeacherId) {
        this.introTeacherId = introTeacherId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}