package com.physics.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.physics.entity.GroupExperiment;

import java.util.List;

public interface GroupExperimentService extends IService<GroupExperiment> {
    
    /**
     * 根据小组名称获取实验安排
     */
    List<GroupExperiment> getByGroupName(String groupName);
    
    /**
     * 批量创建小组实验安排
     */
    boolean batchCreateGroupExperiments(List<GroupExperiment> groupExperiments);
    
    /**
     * 如果小组没有实验安排，则自动创建
     */
    List<GroupExperiment> createGroupExperimentsIfNotExists(String groupName);
    
    /**
     * 批量设置指定学期/套件、若干小组、若干周次的实验与教师
     */
    boolean batchAssignExperimentsAndTeachers(Long semesterId, Long suiteId, List<String> groupNames, List<String> weeks, Long experimentId, Long teacherId);

    /**
     * 批量按明细更新：每个周次可对应不同的实验/教师
     */
    boolean batchAssignItems(Long semesterId, Long suiteId, String groupName, String timeSlot, List<Item> items);
    
    /**
     * 根据时间段获取实验安排
     */
    List<GroupExperiment> getByTimeSlot(String timeSlot);
    
    /**
     * 更新教师分配信息
     */
    boolean updateTeacherAssignment(com.physics.dto.TeacherAssignmentRequest request);

    /**
     * 将若干小组绑定到指定时间段：更新 group_experiment.time_slot
     */
    boolean bindGroupsToTimeSlot(Long semesterId, Long suiteId, Integer weekType, String timeSlot, java.util.List<String> groupNames);

    class Item {
        public String week;        // “第X周” 或可解析出数字
        public Boolean enabled;    // 是否启用；false 表示该周次在该 timeSlot 下需要删除
        public Long experimentId;  // 可为 null
        public Long teacherId;     // 可为 null
    }
}
