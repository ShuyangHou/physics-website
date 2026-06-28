package com.physics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.physics.entity.ExperimentSchedule;
import com.physics.mapper.ExperimentScheduleMapper;
import com.physics.service.ExperimentScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.physics.entity.GroupExperiment;
import com.physics.entity.Semester;
import com.physics.service.GroupExperimentService;
import com.physics.service.SemesterService;
import lombok.extern.slf4j.Slf4j;
import com.physics.entity.Experiment;
import com.physics.service.ExperimentSuiteService;
import com.physics.service.ExperimentService;
import com.physics.dto.TeacherAssignmentRequest;
import com.physics.utils.WeekUtils;


@Slf4j
@Service
public class ExperimentScheduleServiceImpl extends ServiceImpl<ExperimentScheduleMapper, ExperimentSchedule> implements ExperimentScheduleService {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private GroupExperimentService groupExperimentService;

    @Autowired
    private ExperimentSuiteService experimentSuiteService;

    @Autowired
    private SemesterService semesterService;
    


    @Override
    public ExperimentSchedule getByExperimentTimeAndSemester(String experimentTime, Long semesterId,Integer suiteId) {
        QueryWrapper<ExperimentSchedule> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("experiment_time", experimentTime)
                   .eq("semester_id", semesterId)
                   .eq("suite_id", suiteId);
        return this.getOne(queryWrapper);
    }
    
    /**
     * 根据实验时间、学期ID和周类型查找实验安排
     * 
     * @param experimentTime 实验时间，如 "周一上午"
     * @param semesterId 学期ID
     * @param weekType 周类型，0-单周，1-双周
     * @return 实验安排记录
     */
    public ExperimentSchedule getByExperimentTimeAndSemesterAndWeekType(String experimentTime, Long semesterId, Integer weekType,Integer suiteId) {
        QueryWrapper<ExperimentSchedule> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("experiment_time", experimentTime)
                   .eq("semester_id", semesterId)
                   .eq("week_type", weekType)
                   .eq("suite_id", suiteId);
        return this.getOne(queryWrapper);
    }

    public List<Long> parseExperimentIds(String experimentIds) {
        if (experimentIds == null || experimentIds.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(experimentIds, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            // 如果JSON解析失败，返回空列表
            return new ArrayList<>();
        }
    }
    
    @Override
    public boolean generateScheduleFromGroups(Long semesterId, Long suiteId, List<String> groups, Integer weekType) {
        try {
            log.info("开始生成课表: semesterId={}, suiteId={}, groups={}, weekType={}", 
                    semesterId, suiteId, groups, weekType);
            
            if (groups == null || groups.isEmpty()) {
                log.warn("学生组列表为空，无法生成课表");
                return false;
            }
            
            // 1. 获取实验套信息
            List<Long> experimentIds = new ArrayList<>();
            if (suiteId != null) {
                experimentIds = getExperimentIdsBySuiteId(suiteId);
                if (experimentIds.isEmpty()) {
                    log.warn("实验套 {} 没有配置实验ID顺序，尝试从已有分组安排推导", suiteId);
                }
            }

            // 1.1 兜底：若套未配置实验，尝试从已有的小组实验安排中推导实验顺序
            if (experimentIds.isEmpty()) {
                try {
                    com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GroupExperiment> geQ = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
                    geQ.eq(semesterId != null, "semester_id", semesterId);
                    geQ.eq(suiteId != null, "suite_id", suiteId);
                    if (groups != null && !groups.isEmpty()) {
                        geQ.in("group_name", groups);
                    }
                    List<GroupExperiment> geList = groupExperimentService.list(geQ);
                    experimentIds = geList.stream()
                            .map(GroupExperiment::getExperimentId)
                            .filter(id -> id != null)
                            .distinct()
                            .sorted()
                            .collect(java.util.stream.Collectors.toList());
                    if (experimentIds.isEmpty()) {
                        log.warn("未能从已有分组安排推导出实验ID列表，无法生成课表");
                        return false;
                    }
                } catch (Exception ex) {
                    log.error("推导实验ID失败", ex);
                    return false;
                }
            }
            
            // 2. 定义周次安排（规则B：第1周绪论课，实验从第3周开始；周数由学期日期决定）
            List<Integer> weeks = getWeeksByType(semesterId, weekType);
            if (weeks.isEmpty()) {
                log.warn("动态生成周次为空: semesterId={}, weekType={}", semesterId, weekType);
                return false;
            }
            
            // 3. 为每个组生成实验安排
            for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
                String groupName = groups.get(groupIndex);
                
                // 计算该组的起始实验索引（轮换逻辑）
                // 第一组从第0个实验开始，后续组轮换
                int startExperimentIndex = experimentIds.isEmpty() ? 0 : (groupIndex % experimentIds.size());
                
                // 为每个周次分配实验
                for (int weekIndex = 0; weekIndex < weeks.size(); weekIndex++) {
                    int week = weeks.get(weekIndex);
                    
                    // 按轮换逻辑分配实验
                    int experimentIndex = experimentIds.isEmpty() ? 0 : ((startExperimentIndex + weekIndex) % experimentIds.size());
                    Long experimentId = experimentIds.get(experimentIndex);
                    
                    // 创建或更新实验安排（只针对指定周次类型）
                    createOrUpdateGroupExperiment(groupName, semesterId, suiteId, experimentId, week, weekType);
                }
            }
            
            log.info("课表生成完成，共处理 {} 个组，周次类型: {}", groups.size(), weekType == 0 ? "单周" : "双周");
            return true;
            
        } catch (Exception e) {
            log.error("生成课表失败", e);
            return false;
        }
    }
    

    
    /**
     * 根据周次类型获取对应的周次列表
     */
    private List<Integer> getWeeksByType(Long semesterId, Integer weekType) {
        if (semesterId == null) {
            return new ArrayList<>();
        }
        Semester semester = semesterService.getById(semesterId);
        if (semester == null || semester.getStartDate() == null || semester.getEndDate() == null) {
            return new ArrayList<>();
        }
        return WeekUtils.getTeachingWeeksRuleB(semester.getStartDate(), semester.getEndDate(), weekType);
    }
    
    /**
     * 创建或更新小组实验安排
     */
    private void createOrUpdateGroupExperiment(String groupName, Long semesterId, Long suiteId, 
                                             Long experimentId, Integer week, Integer weekType) {
        // TODO: Fix Lombok compilation issues
        /*
        try {
            // 构建实验时间字符串 - GroupExperiment使用具体周次
            String experimentTime = String.format("第%d周", week);
            
            // 获取实验信息，包括地点
            String location = null;
            
            // 查找是否已存在安排
            QueryWrapper<GroupExperiment> wrapper = new QueryWrapper<>();
            wrapper.eq("group_name", groupName)
                   .eq("semester_id", semesterId)
                   .eq("suite_id", suiteId)
                   .eq("experiment_id", experimentId);
            
            GroupExperiment existing = groupExperimentService.getOne(wrapper);
            
            if (existing != null) {
                // 更新现有记录
                existing.setExperimentTime(experimentTime);
                existing.setLocation(location);
                groupExperimentService.updateById(existing);
            } else {
                // 创建新记录
                GroupExperiment newRecord = new GroupExperiment();
                newRecord.setGroupName(groupName);
                newRecord.setSemesterId(semesterId);
                newRecord.setSuiteId(suiteId);
                newRecord.setExperimentId(experimentId);
                newRecord.setExperimentTime(experimentTime);
                newRecord.setLocation(location);
                groupExperimentService.save(newRecord);
            }
            
        } catch (Exception e) {
            log.error("创建或更新小组实验安排失败: groupName={}, week={}", groupName, week, e);
        }
        */
    }
    
    @Override
    public List<Long> getExperimentIdsBySuiteId(Long suiteId) {
        // TODO: Fix Lombok compilation issues
        /*
        try {
            if (suiteId == null) {
                return new ArrayList<>();
            }
            
            // 获取实验套信息
            ExperimentSuite suite = experimentSuiteService.getById(suiteId);
            if (suite == null) {
                log.warn("实验套 {} 不存在", suiteId);
                return new ArrayList<>();
            }
            
            // 解析实验ID列表
            String experimentIdsStr = suite.getExperimentIds();
            if (experimentIdsStr == null || experimentIdsStr.trim().isEmpty()) {
                log.warn("实验套 {} 没有实验", suiteId);
                return new ArrayList<>();
            }
            
            return parseExperimentIds(experimentIdsStr);
            
        } catch (Exception e) {
            log.error("获取实验套实验列表失败: suiteId={}", suiteId, e);
            return new ArrayList<>();
        }
        */
        return new ArrayList<>();
    }
    
    @Override
    public boolean saveTeacherAssignment(TeacherAssignmentRequest request) {
        try {
            log.info("开始保存教师分配: semesterId={}, suiteId={}, timeSlot={}, weekType={}", 
                    request.getSemesterId(), request.getSuiteId(), request.getTimeSlot(), request.getWeekType());
            
            if (request.getAssignments() == null || request.getAssignments().isEmpty()) {
                log.warn("教师分配列表为空");
                return false;
            }
            
            // 构建时间段字符串：必须包含"单"/"双"前缀，格式为"单周一上午"或"双周一上午"
            String timeSlot = request.getTimeSlot(); // 前端传入的是"周一上午"格式
            String weekTypeText = (request.getWeekType() == null || request.getWeekType() == 0) ? "单" : "双";
            String experimentTime = weekTypeText + timeSlot; // 格式：单周一上午 或 双周一上午
            
            // 验证所有分配项的必要字段
            for (TeacherAssignmentRequest.TeacherAssignmentItem item : request.getAssignments()) {
                if (item.getExperimentId() == null || item.getTeacherId() == null) {
                    log.warn("实验ID或教师ID为空: experimentId={}, teacherId={}", 
                            item.getExperimentId(), item.getTeacherId());
                    return false;
                }
            }
            
            // 收集所有教师ID（去重）
            List<Long> allTeacherIds = request.getAssignments().stream()
                .map(TeacherAssignmentRequest.TeacherAssignmentItem::getTeacherId)
                .distinct()
                .collect(Collectors.toList());

            // 绪论课老师：以 request.assignments 中 isIntroCourse=1 的 teacherId 为准（同时间段仅允许一个）
            Long introTeacherId = null;
            int introCount = 0;
            for (TeacherAssignmentRequest.TeacherAssignmentItem item : request.getAssignments()) {
                if (item == null) continue;
                if (item.getIsIntroCourse() != null && item.getIsIntroCourse() == 1) {
                    introCount++;
                    if (introTeacherId == null) {
                        introTeacherId = item.getTeacherId();
                    }
                }
            }
            if (introCount > 1) {
                log.warn("同一时间段提交了多个绪论课标记，将仅保留第一个: semesterId={}, suiteId={}, timeSlot={}, weekType={}, introTeacherId={}, introCount={} ",
                        request.getSemesterId(), request.getSuiteId(), request.getTimeSlot(), request.getWeekType(), introTeacherId, introCount);
            }
            
            // 创建或更新教师分配记录（ExperimentSchedule表用于整体时间段管理）
            createOrUpdateTeacherAssignment(
                request.getSemesterId(),
                request.getSuiteId(),
                allTeacherIds,
                experimentTime,
                request.getWeekType(),
                introTeacherId
            );
            
            // 同时更新GroupExperiment表中的教师分配（包含具体的实验信息）
            boolean groupUpdateSuccess = groupExperimentService.updateTeacherAssignment(request);
            if (!groupUpdateSuccess) {
                log.warn("更新GroupExperiment表教师分配失败，但ExperimentSchedule表更新成功");
            }
            
            log.info("教师分配保存完成，共处理 {} 个分配，涉及 {} 个教师", 
                    request.getAssignments().size(), allTeacherIds.size());
            return true;
            
        } catch (Exception e) {
            log.error("保存教师分配失败", e);
            return false;
        }
    }
    
    /**
     * 构建时间段字符串
     */
    private String buildTimeSlotString(String timeSlot, Integer weekType) {
        // 构建格式：单周一上午 或 双周一上午
        String weekTypeText = (weekType == null || weekType == 0) ? "单" : "双";
        return weekTypeText + timeSlot;
    }
    
    /**
     * 创建或更新教师分配记录
     */
    private void createOrUpdateTeacherAssignment(Long semesterId, Long suiteId, List<Long> teacherIds, String experimentTime, Integer weekType, Long introTeacherId) {
        try {
            // 查找是否已存在教师分配记录
            QueryWrapper<ExperimentSchedule> wrapper = new QueryWrapper<>();
            wrapper.eq("semester_id", semesterId)
                   .eq("suite_id", suiteId)
                   .eq("week_type", weekType)
                   .eq("experiment_time", experimentTime);
            
            ExperimentSchedule existing = this.getOne(wrapper);
            
            if (existing != null) {
                // 更新现有记录
                existing.setTeacherIds("[" + String.join(",", teacherIds.stream().map(String::valueOf).collect(Collectors.toList())) + "]");
                existing.setIntroTeacherId(introTeacherId);
                this.updateById(existing);
                log.info("更新现有教师分配记录: scheduleId={}, teacherIds={}", existing.getScheduleId(), teacherIds);
            } else {
                // 创建新记录
                ExperimentSchedule newRecord = new ExperimentSchedule();
                newRecord.setSemesterId(semesterId);
                newRecord.setSuiteId(suiteId);
                newRecord.setTeacherIds("[" + String.join(",", teacherIds.stream().map(String::valueOf).collect(Collectors.toList())) + "]");
                newRecord.setExperimentTime(experimentTime);
                newRecord.setWeekType(weekType);
                newRecord.setIntroTeacherId(introTeacherId);
                this.save(newRecord);
                log.info("创建新教师分配记录: experimentTime={}, weekType={}, teacherIds={}", experimentTime, weekType, teacherIds);
            }
            
        } catch (Exception e) {
            log.error("创建或更新教师分配记录失败: teacherIds={}", teacherIds, e);
        }
    }
    

    

    

} 