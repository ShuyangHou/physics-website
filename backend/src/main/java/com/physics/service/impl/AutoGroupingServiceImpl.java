package com.physics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.physics.common.Result;
import com.physics.entity.ExperimentSchedule;
import com.physics.entity.GroupExperiment;
import com.physics.entity.Experiment;
import com.physics.entity.Semester;
import com.physics.entity.User;
import com.physics.service.AutoGroupingService;
import com.physics.service.ExperimentScheduleService;
import com.physics.service.GroupExperimentService;
import com.physics.service.ExperimentService;
import com.physics.service.ExperimentSuiteService;
import com.physics.service.SemesterService;
import com.physics.service.UserService;
import com.physics.utils.GroupingUtils;
import com.physics.utils.WeekUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 自动分组服务实现类
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class AutoGroupingServiceImpl implements AutoGroupingService {
    
    @Autowired
    private GroupingUtils groupingUtils;
    
    @Autowired
    private ExperimentScheduleService experimentScheduleService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private GroupExperimentService groupExperimentService;
    
    @Autowired
    private ExperimentService experimentService;
    
    @Autowired
    private ExperimentSuiteService experimentSuiteService;

    @Autowired
    private SemesterService semesterService;
    
    @Override
    public String generateGroups(String classIds) {
        try {
            log.info("开始生成分组，班级ID列表: {}", classIds);
            
            String groupIds = groupingUtils.generateGroups(classIds);
            
            log.info("分组生成完成，结果: {}", groupIds);
            return groupIds;
            
        } catch (Exception e) {
            log.error("生成分组失败", e);
            throw new RuntimeException("生成分组失败: " + e.getMessage());
        }
    }

    /**
     * 设置某学生的分组信息（学期/套件/周类型），不落库；由调用方收集后批量更新。
     */
    private void applyGroupAssignment(User student, String groupName, Long semesterId, Long suiteId, Integer weekType) {
        student.setGroupName(groupName);
        student.setSemesterId(semesterId);
        student.setSuiteId(suiteId);
        student.setWeekType(weekType);
    }

    /**
     * 将一批已设置好 groupName/semesterId/suiteId/weekType 的学生按组写回数据库：
     * 同组合并为一条 UPDATE ... WHERE user_id IN (...)，只更新分组相关列，
     * 避免整行重写与逐行网络往返。
     */
    private void flushGroupingUpdates(List<User> students) {
        if (students == null || students.isEmpty()) return;
        Map<String, List<Long>> groupToIds = new LinkedHashMap<>();
        Map<String, User> groupSample = new HashMap<>();
        for (User s : students) {
            if (s == null || s.getUserId() == null) continue;
            String g = s.getGroupName();
            groupToIds.computeIfAbsent(g, k -> new ArrayList<>()).add(s.getUserId());
            groupSample.putIfAbsent(g, s);
        }
        for (Map.Entry<String, List<Long>> e : groupToIds.entrySet()) {
            User sample = groupSample.get(e.getKey());
            userService.updateGroupingByIds(e.getValue(), e.getKey(),
                    sample.getSemesterId(), sample.getSuiteId(), sample.getWeekType());
        }
    }

    @Override
    public Result<Boolean> updateScheduleByTime(String experimentTime, String classIds, String groupIds, Long semesterId, Long suiteId, Integer weekType) {
        try {
            log.info("开始根据时间段更新实验安排分组，时间段: {}, 班级ID列表: {}, 学期ID: {}, 实验套ID: {}, 周类型: {}", experimentTime, classIds, semesterId, suiteId, weekType);
            
            // 根据时间段、学期ID和周类型查找实验安排记录
            // 必须使用weekType进行查询，避免单周和双周的记录混淆
            // experimentTime格式必须保持为"单周一上午"或"双周一上午"，不能改动
            ExperimentSchedule schedule = null;
            if (weekType != null) {
                schedule = experimentScheduleService.getByExperimentTimeAndSemesterAndWeekType(experimentTime, semesterId, weekType, suiteId.intValue());
            } else {
                // 如果weekType为null，使用不带weekType的查询（兼容旧数据）
                log.warn("weekType为null，使用不带weekType的查询，可能存在数据混淆风险");
                schedule = experimentScheduleService.getByExperimentTimeAndSemester(experimentTime, semesterId, suiteId.intValue());
            }
            
            if (schedule == null) {
                // 如果不存在，创建新的实验安排记录
                schedule = new ExperimentSchedule();
                // experimentTime必须保持原始格式，如"单周一上午"或"双周一上午"
                schedule.setExperimentTime(experimentTime);
                schedule.setClassIds(classIds);
                schedule.setGroupIds(groupIds);
                schedule.setSemesterId(semesterId);
                schedule.setSuiteId(suiteId);
                if (weekType != null) {
                    schedule.setWeekType(weekType);
                }
                
                boolean success = experimentScheduleService.save(schedule);
                if (success) {
                    log.info("创建新的实验安排记录成功，时间段: {}, 学期ID: {}", experimentTime, semesterId);
                    
                    // 更新学生的 groupName 字段（失败抛异常以触发整体回滚）
                    boolean updateStudentsSuccess = updateStudentGroups(classIds, groupIds, semesterId, suiteId, weekType);
                    if (!updateStudentsSuccess) {
                        throw new RuntimeException("更新学生分组失败");
                    }
                    
                    // 自动创建实验安排（轮换逻辑）
                    if (weekType != null) {
                        String[] groups = groupIds.split(",");
                        List<String> groupList = Arrays.asList(groups);
                        boolean createExperimentsSuccess = createGroupExperimentsForAllGroups(groupList, semesterId, suiteId, weekType, experimentTime);
                        if (!createExperimentsSuccess) {
                            throw new RuntimeException("自动创建实验安排失败");
                        }
                    }
                    
                    return Result.success(true);
                } else {
                    log.error("创建实验安排记录失败，时间段: {}, 学期ID: {}", experimentTime, semesterId);
                    return Result.error("创建实验安排失败");
                }
            } else {
                // 如果存在，检查weekType是否匹配，防止单周更新影响双周
                if (weekType != null && schedule.getWeekType() != null && !schedule.getWeekType().equals(weekType)) {
                    log.error("尝试更新不同周次类型的记录！当前记录weekType: {}, 请求weekType: {}, 时间段: {}, 学期ID: {}", 
                            schedule.getWeekType(), weekType, experimentTime, semesterId);
                    return Result.error("不能更新不同周次类型的实验安排记录");
                }
                
                // 如果存在，更新现有记录
                schedule.setClassIds(classIds);
                schedule.setGroupIds(groupIds);
                // 确保weekType正确设置
                if (weekType != null) {
                    schedule.setWeekType(weekType);
                }
                // experimentTime保持原始格式不变，如"单周一上午"或"双周一上午"
                
                boolean success = experimentScheduleService.updateById(schedule);
                if (success) {
                    log.info("更新实验安排记录成功，时间段: {}, 学期ID: {}, weekType: {}", experimentTime, semesterId, weekType);
                    
                    // 更新学生的 groupName 字段（失败抛异常以触发整体回滚）
                    boolean updateStudentsSuccess = updateStudentGroups(classIds, groupIds, semesterId, suiteId, weekType);
                    if (!updateStudentsSuccess) {
                        throw new RuntimeException("更新学生分组失败");
                    }
                    
                    // 自动创建实验安排（轮换逻辑）
                    if (weekType != null) {
                        String[] groups = groupIds.split(",");
                        List<String> groupList = Arrays.asList(groups);
                        boolean createExperimentsSuccess = createGroupExperimentsForAllGroups(groupList, semesterId, suiteId, weekType, experimentTime);
                        if (!createExperimentsSuccess) {
                            throw new RuntimeException("自动创建实验安排失败");
                        }
                    }
                    
                    return Result.success(true);
                } else {
                    log.error("更新实验安排记录失败，时间段: {}, 学期ID: {}", experimentTime, semesterId);
                    return Result.error("更新实验安排失败");
                }
            }
            
        } catch (Exception e) {
            log.error("根据时间段更新实验安排分组失败", e);
            // 抛出异常以触发 @Transactional 回滚，避免半成品脏数据；由控制器统一转为错误响应
            throw new RuntimeException("更新实验安排失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 更新学生分组
     * 
     * @param classIds 班级ID列表
     * @param groupIds 分组ID列表
     * @param semesterId 学期ID
     * @param suiteId 实验套ID
     * @param weekType 周类型：0-单周，1-双周
     * @return 是否更新成功
     */
    private boolean updateStudentGroups(String classIds, String groupIds, Long semesterId, Long suiteId, Integer weekType) {
        try {
            log.info("开始更新学生分组，班级ID列表: {}, 分组ID列表: {}, weekType: {}", classIds, groupIds, weekType);
            
            // 说明：每个学生在 user 表上只保存一份 group_name/semester_id/suite_id/week_type，
            // 即一个学生同一时间只属于一个分组。常规情况下一个学生只上单周或只上双周，
            // 故单行模型可满足；若对同一批学生再以不同 weekType 重新分组，会覆盖其原分组
            // （特殊学生需手动调整）。如需"同一学生在单/双周分属不同组"，须改为独立的
            // 学生-分组关联表 (user_id, semester_id, suite_id, week_type, group_name)。
            log.info("更新学生分组，学期ID: {}, 实验套ID: {}, 周类型: {}", semesterId, suiteId, weekType);
            
            // 解析班级列表
            List<String> classList = groupingUtils.parseClassIds(classIds);

            if (classList == null || classList.isEmpty()) {
                log.warn("班级列表为空，跳过分组更新");
                return true;
            }

            // 单一真值源：组名生成（generateGroups）与学生分配都来自同一个
            // GroupingUtils.buildGroupingPlan，保证组名与实际分配完全一致。
            LinkedHashMap<String, List<User>> plan = groupingUtils.buildGroupingPlan(classList);
            List<User> toUpdate = new ArrayList<>();
            for (Map.Entry<String, List<User>> entry : plan.entrySet()) {
                String groupName = entry.getKey();
                List<User> members = entry.getValue();
                if (members == null || members.isEmpty()) continue;
                for (User stu : members) {
                    applyGroupAssignment(stu, groupName, semesterId, suiteId, weekType);
                    toUpdate.add(stu);
                }
                log.info("组 {} 分配 {} 人", groupName, members.size());
            }
            flushGroupingUpdates(toUpdate);
            log.info("学生分组更新完成，weekType: {}", weekType);
            return true;
            
        } catch (Exception e) {
            log.error("更新学生分组失败", e);
            return false;
        }
    }
    
    /**
     * 为所有小组创建实验安排（按轮换逻辑）
     * 轮换规则：第一组实验1-8，第二组实验2-9，第三组实验3-10，第四组实验4-10+1，以此类推
     * 
     * @param groupList 小组列表
     * @param semesterId 学期ID
     * @param suiteId 实验套ID
     * @param weekType 周类型：0-单周（第1,3,5,7,9,11,13,15,17周），1-双周（第2,4,6,8,10,12,14,16,18周）
     * @return 是否创建成功
     */
    private boolean createGroupExperimentsForAllGroups(List<String> groupList, Long semesterId, Long suiteId, Integer weekType, String timeSlot) {
        try {
            log.info("开始为小组创建实验安排：semesterId={}, suiteId={}, groups={}, weekType={}, timeSlot={}", 
                    semesterId, suiteId, groupList, weekType, timeSlot);
            
            // 1. 获取实验套的实验ID列表
            List<Long> experimentIds = experimentSuiteService.getExperimentIdsBySuiteId(suiteId);
            if (experimentIds == null || experimentIds.isEmpty()) {
                log.warn("实验套 {} 没有配置实验ID，跳过创建实验安排", suiteId);
                return false;
            }
            
            // 2. 确定周次列表（每组8个实验，单周跳过第1周；双周跳过第2周）
            Semester semester = semesterService.getById(semesterId);
            if (semester == null || semester.getStartDate() == null || semester.getEndDate() == null) {
                log.warn("学期信息不完整，无法动态生成周次: semesterId={}", semesterId);
                return false;
            }
            List<Integer> weeks = WeekUtils.getTeachingWeeksRuleB(semester.getStartDate(), semester.getEndDate(), weekType);
            if (weeks.isEmpty()) {
                log.warn("动态生成周次为空: semesterId={}, weekType={}", semesterId, weekType);
                return false;
            }
            
            // 3. 按轮换逻辑为每个组分配实验（每组8个实验，分配到8个周次）
            // 先清理该学期和套件下相关小组、当前周次类型的旧记录，然后重新创建
            List<String> weekTimeStrings = weeks.stream()
                    .map(w -> "第" + w + "周")
                    .collect(Collectors.toList());
            if (!groupList.isEmpty()) {
                QueryWrapper<GroupExperiment> deleteWrapper = new QueryWrapper<>();
                deleteWrapper.eq("semester_id", semesterId)
                           .eq("suite_id", suiteId)
                           .in("group_name", groupList)
                           .in("experiment_time", weekTimeStrings);
                groupExperimentService.remove(deleteWrapper);
            }
            
            // 一次性预取实验地点，避免内层循环逐条查库
            Map<Long, String> locationMap = experimentService.listByIds(experimentIds).stream()
                    .filter(e -> e != null && e.getExperimentId() != null && e.getLocation() != null)
                    .collect(Collectors.toMap(Experiment::getExperimentId, Experiment::getLocation, (a, b) -> a));

            List<GroupExperiment> toInsert = new ArrayList<>();
            
            for (int groupIndex = 0; groupIndex < groupList.size(); groupIndex++) {
                String groupName = groupList.get(groupIndex);
                
                // 计算该组的起始实验索引（轮换逻辑）
                // 第一组从第0个实验开始（实验1-9），第二组从第1个实验开始（实验2-10），第三组从第2个实验开始（实验3-10,1），以此类推
                int startExperimentIndex = groupIndex % experimentIds.size();
                
                int count = Math.min(8, weeks.size());
                for (int expOffset = 0; expOffset < count; expOffset++) {
                    int week = weeks.get(expOffset);
                    
                    // 计算实验索引（轮换逻辑）
                    int experimentIndex = (startExperimentIndex + expOffset) % experimentIds.size();
                    Long experimentId = experimentIds.get(experimentIndex);
                    
                    // 创建新记录（已清理旧记录，直接创建新的）
                    GroupExperiment newRecord = new GroupExperiment();
                    newRecord.setGroupName(groupName);
                    newRecord.setSemesterId(semesterId);
                    newRecord.setSuiteId(suiteId);
                    newRecord.setExperimentId(experimentId);
                    newRecord.setExperimentTime("第" + week + "周");
                    newRecord.setTimeSlot(timeSlot);
                    newRecord.setIsIntroCourse(0); // 默认为普通实验，绪论课由用户在教师设置时手动勾选
                    newRecord.setLocation(locationMap.get(experimentId));
                    
                    toInsert.add(newRecord);
                }
            }
            
            // 4. 批量插入新记录
            if (!toInsert.isEmpty()) {
                boolean success = groupExperimentService.saveBatch(toInsert);
                log.info("为 {} 个小组创建了 {} 条实验安排记录", groupList.size(), toInsert.size());
                return success;
            } else {
                log.info("所有小组的实验安排已存在，无需创建");
                return true;
            }
            
        } catch (Exception e) {
            log.error("为小组创建实验安排失败", e);
            return false;
        }
    }

}
