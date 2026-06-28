package com.physics.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.physics.entity.ExperimentSchedule;
import com.physics.dto.TeacherAssignmentRequest;

import java.util.List;

public interface ExperimentScheduleService extends IService<ExperimentSchedule> {
    
    /**
     * 根据实验时间和学期ID查找实验安排
     * 
     * @param experimentTime 实验时间，如 "周一上午"
     * @param semesterId 学期ID
     * @return 实验安排记录
     */
    ExperimentSchedule getByExperimentTimeAndSemester(String experimentTime, Long semesterId,Integer suiteId);
    
    /**
     * 根据实验时间、学期和周次类型获取实验安排
     */
    ExperimentSchedule getByExperimentTimeAndSemesterAndWeekType(String experimentTime, Long semesterId, Integer weekType,Integer suiteId);
    
    /**
     * 根据分组信息自动生成课表
     * @param semesterId 学期ID
     * @param suiteId 实验套ID（可选）
     * @param groups 学生组列表
     * @param weekType 周次类型：0-单周，1-双周
     * @return 是否生成成功
     */
    boolean generateScheduleFromGroups(Long semesterId, Long suiteId, List<String> groups, Integer weekType);
    
    /**
     * 根据实验套ID获取实验ID列表
     */
    List<Long> getExperimentIdsBySuiteId(Long suiteId);
    
    /**
     * 保存教师分配
     * @param request 教师分配请求
     * @return 是否保存成功
     */
    boolean saveTeacherAssignment(TeacherAssignmentRequest request);
    

} 