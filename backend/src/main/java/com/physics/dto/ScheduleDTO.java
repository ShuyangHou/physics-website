package com.physics.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ScheduleDTO {
    
    private Long scheduleId;
    
    private Long experimentId;
    
    private String experimentName;
    
    private Integer experimentType;
    
    private String teacherIds;
    
    private List<String> teacherNames;
    
    private String groupIds;
    
    private List<String> groupNames;
    
    private String location;
    
    private String experimentTime;
    
    private Integer weekType; // 0 单周，1 双周
    
    private Long suiteId; // 实验套ID
    
    private Integer isIntroCourse = 0; // 是否为绪论课：0 不是，1 是
    
    private Long introTeacherId; // 绪论课教师ID（experiment_schedule.intro_teacher_id）
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }

    public Long getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(Long experimentId) {
        this.experimentId = experimentId;
    }

    public String getExperimentName() {
        return experimentName;
    }

    public void setExperimentName(String experimentName) {
        this.experimentName = experimentName;
    }

    public Integer getExperimentType() {
        return experimentType;
    }

    public void setExperimentType(Integer experimentType) {
        this.experimentType = experimentType;
    }

    public String getTeacherIds() {
        return teacherIds;
    }

    public void setTeacherIds(String teacherIds) {
        this.teacherIds = teacherIds;
    }

    public List<String> getTeacherNames() {
        return teacherNames;
    }

    public void setTeacherNames(List<String> teacherNames) {
        this.teacherNames = teacherNames;
    }

    public String getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(String groupIds) {
        this.groupIds = groupIds;
    }

    public List<String> getGroupNames() {
        return groupNames;
    }

    public void setGroupNames(List<String> groupNames) {
        this.groupNames = groupNames;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getExperimentTime() {
        return experimentTime;
    }

    public void setExperimentTime(String experimentTime) {
        this.experimentTime = experimentTime;
    }

    public Integer getWeekType() {
        return weekType;
    }

    public void setWeekType(Integer weekType) {
        this.weekType = weekType;
    }

    public Long getSuiteId() {
        return suiteId;
    }

    public void setSuiteId(Long suiteId) {
        this.suiteId = suiteId;
    }

    public Integer getIsIntroCourse() {
        return isIntroCourse;
    }

    public void setIsIntroCourse(Integer isIntroCourse) {
        this.isIntroCourse = isIntroCourse;
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