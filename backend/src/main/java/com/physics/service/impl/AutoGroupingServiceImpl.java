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
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 自动分组服务实现类
 */
@Slf4j
@Service
@Transactional
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

    private void sortStudentsBySchoolId(List<User> students) {
        if (students == null || students.size() <= 1) return;
        students.sort((a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            String sa = a.getSchoolId() == null ? "" : a.getSchoolId().trim();
            String sb = b.getSchoolId() == null ? "" : b.getSchoolId().trim();
            if (sa.isEmpty() && sb.isEmpty()) return 0;
            if (sa.isEmpty()) return 1;
            if (sb.isEmpty()) return -1;
            try {
                long la = Long.parseLong(sa);
                long lb = Long.parseLong(sb);
                return Long.compare(la, lb);
            } catch (Exception ignore) {
                return sa.compareTo(sb);
            }
        });
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

    private boolean assignABByClassEvenly(List<String> classList, Long semesterId, Long suiteId, Integer weekType) {
        try {
            List<User> toUpdate = new ArrayList<>();
            for (String className : classList) {
                if (className == null || className.trim().isEmpty()) continue;
                List<User> students = userService.getStudentsByClassName(className.trim());
                if (students == null || students.isEmpty()) {
                    log.warn("班级 {} 没有学生", className);
                    continue;
                }
                sortStudentsBySchoolId(students);
                int n = students.size();
                int aCount = (n + 1) / 2;
                for (int i = 0; i < n; i++) {
                    User stu = students.get(i);
                    String gn = (i < aCount) ? (className + "A") : (className + "B");
                    applyGroupAssignment(stu, gn, semesterId, suiteId, weekType);
                    toUpdate.add(stu);
                }
                log.info("班级 {} 平分为A/B完成: A={}, B={}", className, aCount, n - aCount);
            }
            if (!toUpdate.isEmpty()) {
                userService.updateBatchById(toUpdate);
            }
            return true;
        } catch (Exception e) {
            log.error("按班级平分A/B失败", e);
            return false;
        }
    }

    private boolean assign10GroupsEvenlyFor4Classes(List<String> classList, List<String> groupList,
                                                    Long semesterId, Long suiteId, Integer weekType) {
        try {
            // 期望组名：每班A/B + 第一个班级C/D
            String firstClass = classList.get(0);
            Map<String, Integer> desiredSize = new LinkedHashMap<>();
            List<String> orderedGroups = new ArrayList<>();

            for (String cls : classList) {
                if (cls == null || cls.trim().isEmpty()) continue;
                orderedGroups.add(cls + "A");
                orderedGroups.add(cls + "B");
            }
            orderedGroups.add(firstClass + "C");
            orderedGroups.add(firstClass + "D");

            // 统计总人数
            Map<String, List<User>> classToStudents = new HashMap<>();
            int total = 0;
            for (String cls : classList) {
                List<User> stus = userService.getStudentsByClassName(cls);
                if (stus == null) stus = new ArrayList<>();
                sortStudentsBySchoolId(stus);
                classToStudents.put(cls, stus);
                total += stus.size();
            }
            if (total == 0) {
                log.warn("四个班总人数为0，跳过分组");
                return true;
            }

            int base = total / 10;
            int r = total % 10;

            // B方案：余数优先给C/D，再给AB（按orderedGroups顺序）
            for (String g : orderedGroups) {
                desiredSize.put(g, base);
            }
            if (r > 0) {
                desiredSize.put(firstClass + "C", desiredSize.get(firstClass + "C") + 1);
                r--;
            }
            if (r > 0) {
                desiredSize.put(firstClass + "D", desiredSize.get(firstClass + "D") + 1);
                r--;
            }
            for (String g : orderedGroups) {
                if (r <= 0) break;
                if (g.endsWith("C") || g.endsWith("D")) continue;
                desiredSize.put(g, desiredSize.get(g) + 1);
                r--;
            }

            // 分配AB（只取本班），剩余进入pool
            Map<String, List<User>> groupToStudents = new LinkedHashMap<>();
            List<User> remainingPool = new ArrayList<>();

            for (String cls : classList) {
                List<User> stus = classToStudents.getOrDefault(cls, new ArrayList<>());
                int idx = 0;
                String gA = cls + "A";
                String gB = cls + "B";
                int capA = desiredSize.getOrDefault(gA, 0);
                int capB = desiredSize.getOrDefault(gB, 0);

                List<User> aList = new ArrayList<>();
                for (int i = 0; i < capA && idx < stus.size(); i++) {
                    aList.add(stus.get(idx++));
                }
                List<User> bList = new ArrayList<>();
                for (int i = 0; i < capB && idx < stus.size(); i++) {
                    bList.add(stus.get(idx++));
                }
                groupToStudents.put(gA, aList);
                groupToStudents.put(gB, bList);

                while (idx < stus.size()) {
                    remainingPool.add(stus.get(idx++));
                }
            }

            // remainingPool 需要尽量保持“学号按着”：按 schoolId 再排一次（不同班合并后）
            sortStudentsBySchoolId(remainingPool);

            String gC = firstClass + "C";
            String gD = firstClass + "D";
            int capC = desiredSize.getOrDefault(gC, 0);
            int capD = desiredSize.getOrDefault(gD, 0);

            List<User> cList = new ArrayList<>();
            List<User> dList = new ArrayList<>();

            // 若池子人数与cap不一致（极端情况下AB受限导致池子更多），则退化为对池子进行平分
            if (remainingPool.size() == (capC + capD)) {
                int idx = 0;
                for (int i = 0; i < capC && idx < remainingPool.size(); i++) cList.add(remainingPool.get(idx++));
                for (int i = 0; i < capD && idx < remainingPool.size(); i++) dList.add(remainingPool.get(idx++));
            } else {
                int poolN = remainingPool.size();
                int cCnt = (poolN + 1) / 2;
                for (int i = 0; i < poolN; i++) {
                    if (i < cCnt) cList.add(remainingPool.get(i));
                    else dList.add(remainingPool.get(i));
                }
            }

            groupToStudents.put(gC, cList);
            groupToStudents.put(gD, dList);

            // 写回DB（批量）
            List<User> toUpdate = new ArrayList<>();
            for (Map.Entry<String, List<User>> e : groupToStudents.entrySet()) {
                String groupName = e.getKey();
                List<User> stus = e.getValue();
                if (stus == null) continue;
                for (User stu : stus) {
                    applyGroupAssignment(stu, groupName, semesterId, suiteId, weekType);
                    toUpdate.add(stu);
                }
                log.info("4班平分：组 {} 分配人数 {}", groupName, stus.size());
            }
            if (!toUpdate.isEmpty()) {
                userService.updateBatchById(toUpdate);
            }

            return true;
        } catch (Exception e) {
            log.error("4班10组平分失败", e);
            return false;
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
                    
                    // 更新学生的 groupName 字段
                    boolean updateStudentsSuccess = updateStudentGroups(classIds, groupIds, semesterId, suiteId, weekType);
                    
                    // 自动创建实验安排（轮换逻辑）
                    if (updateStudentsSuccess && weekType != null) {
                        String[] groups = groupIds.split(",");
                        List<String> groupList = Arrays.asList(groups);
                        boolean createExperimentsSuccess = createGroupExperimentsForAllGroups(groupList, semesterId, suiteId, weekType, experimentTime);
                        if (!createExperimentsSuccess) {
                            log.warn("自动创建实验安排失败，但学生分组已完成");
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
                    
                    // 更新学生的 groupName 字段
                    boolean updateStudentsSuccess = updateStudentGroups(classIds, groupIds, semesterId, suiteId, weekType);
                    
                    // 自动创建实验安排（轮换逻辑）
                    if (updateStudentsSuccess && weekType != null) {
                        String[] groups = groupIds.split(",");
                        List<String> groupList = Arrays.asList(groups);
                        boolean createExperimentsSuccess = createGroupExperimentsForAllGroups(groupList, semesterId, suiteId, weekType, experimentTime);
                        if (!createExperimentsSuccess) {
                            log.warn("自动创建实验安排失败，但学生分组已完成");
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
            return Result.error("更新实验安排失败: " + e.getMessage());
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
            
            // 重要：现在支持按学期、实验套、周类型分别存储分组信息
            // 单周和双周可以有不同的分组，不会相互覆盖
            // 通过 (semester_id, suite_id, week_type, group_name) 唯一区分
            log.info("更新学生分组，学期ID: {}, 实验套ID: {}, 周类型: {}", semesterId, suiteId, weekType);
            
            // 解析班级列表
            List<String> classList = groupingUtils.parseClassIds(classIds);

            // 解析分组列表
            String[] groups = groupIds.split(",");
            List<String> groupList = Arrays.stream(groups)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            if (classList == null || classList.isEmpty()) {
                log.warn("班级列表为空，跳过分组更新");
                return true;
            }

            if (classList.size() == 2 || classList.size() == 3) {
                return assignABByClassEvenly(classList, semesterId, suiteId, weekType);
            }

            if (classList.size() == 4) {
                return assign10GroupsEvenlyFor4Classes(classList, groupList, semesterId, suiteId, weekType);
            }

            // 其他班级数：保持旧逻辑（12/12 + C/D 平均）
            int totalStudentsInTimeSlot = 0;
            for (String className : classList) {
                List<User> classStudents = userService.getStudentsByClassName(className);
                totalStudentsInTimeSlot += (classStudents == null ? 0 : classStudents.size());
            }
            log.info("该时间段总学生数: {}", totalStudentsInTimeSlot);

            for (String className : classList) {
                boolean ok = assignABGroupsToClass(className, groupList, totalStudentsInTimeSlot, semesterId, suiteId, weekType);
                if (!ok) {
                    log.error("班级 {} 的A组B组分配失败", className);
                    return false;
                }
            }

            boolean ok = assignCDGroupsToRemainingStudents(classList, groupList, semesterId, suiteId, weekType);
            if (!ok) {
                log.error("C组D组分配失败");
                return false;
            }
            
            log.info("学生分组更新完成，weekType: {}", weekType);
            return true;
            
        } catch (Exception e) {
            log.error("更新学生分组失败", e);
            return false;
        }
    }
    
    /**
     * 为指定班级的学生分配A组和B组
     * 
     * @param className 班级名称
     * @param groupList 分组列表
     * @param totalStudentsInTimeSlot 该时间段的总学生数（此参数不再使用，保留用于兼容）
     * @param semesterId 学期ID
     * @param suiteId 实验套ID
     * @param weekType 周类型：0-单周，1-双周
     * @return 是否分配成功
     */
    private boolean assignABGroupsToClass(String className, List<String> groupList, int totalStudentsInTimeSlot, 
                                          Long semesterId, Long suiteId, Integer weekType) {
        try {
            // 获取该班级的所有学生
            List<User> students = userService.getStudentsByClassName(className);
            
            if (students.isEmpty()) {
                log.warn("班级 {} 没有学生", className);
                return true;
            }
            
            // 找到属于该班级的A组和B组
            List<String> classABGroups = groupList.stream()
                    .filter(group -> group.equals(className + "A") || group.equals(className + "B"))
                    .collect(Collectors.toList());
            
            if (classABGroups.isEmpty()) {
                log.warn("班级 {} 没有对应的A组B组", className);
                return true;
            }
            
            int classStudentCount = students.size();
            log.info("班级 {} 共有 {} 个学生", className, classStudentCount);
            
            int studentIndex = 0;
            List<User> toUpdate = new ArrayList<>();
            
            // 分配A组：前12个学生（第1-12个）
            if (classABGroups.contains(className + "A")) {
                int aGroupCount = Math.min(12, classStudentCount); // 最多12人
                log.info("班级 {} 的A组分配 {} 个学生", className, aGroupCount);
                
                for (int i = 0; i < aGroupCount && studentIndex < students.size(); i++) {
                    User student = students.get(studentIndex);
                    applyGroupAssignment(student, className + "A", semesterId, suiteId, weekType);
                    toUpdate.add(student);
                    studentIndex++;
                }
            }
            
            // 分配B组：第13-24个学生
            if (classABGroups.contains(className + "B") && studentIndex < classStudentCount) {
                int bGroupStart = 12; // 从第13个开始（索引12）
                int bGroupCount = Math.min(12, classStudentCount - bGroupStart); // 最多12人
                log.info("班级 {} 的B组分配 {} 个学生", className, bGroupCount);
                
                for (int i = 0; i < bGroupCount && studentIndex < students.size(); i++) {
                    User student = students.get(studentIndex);
                    applyGroupAssignment(student, className + "B", semesterId, suiteId, weekType);
                    toUpdate.add(student);
                    studentIndex++;
                }
            }
            
            if (!toUpdate.isEmpty()) {
                userService.updateBatchById(toUpdate);
            }
            
            // 超过24人的学生不在这里分配，留给C组D组分配
            if (studentIndex < classStudentCount) {
                log.info("班级 {} 还有 {} 个学生（超过24人）将分配给C组D组", className, classStudentCount - studentIndex);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("为班级 {} 分配A组B组失败", className, e);
            return false;
        }
    }
    
    /**
     * 为剩余学生分配C组和D组
     * 
     * @param classList 班级列表
     * @param groupList 分组列表
     * @param semesterId 学期ID
     * @param suiteId 实验套ID
     * @param weekType 周类型：0-单周，1-双周
     * @return 是否分配成功
     */
    private boolean assignCDGroupsToRemainingStudents(List<String> classList, List<String> groupList, 
                                                      Long semesterId, Long suiteId, Integer weekType) {
        try {
            // 找到C组和D组
            List<String> cdGroups = groupList.stream()
                    .filter(group -> group.endsWith("C") || group.endsWith("D"))
                    .collect(Collectors.toList());
            
            if (cdGroups.isEmpty()) {
                log.info("没有C组D组需要分配");
                return true;
            }
            
            // 收集所有班级中未分配分组的学生（即groupName为null或空的学生）
            List<User> remainingStudents = new ArrayList<>();
            for (String className : classList) {
                List<User> classStudents = userService.getStudentsByClassName(className);
                for (User student : classStudents) {
                    if (student.getGroupName() == null || student.getGroupName().trim().isEmpty()) {
                        remainingStudents.add(student);
                    }
                }
            }
            
            if (remainingStudents.isEmpty()) {
                log.info("没有剩余学生需要分配到C组D组");
                return true;
            }
            
            log.info("剩余学生数量: {}, C组D组: {}", remainingStudents.size(), cdGroups);
            
            // 分配剩余学生到C组和D组 - 平均分配
            int totalRemainingStudents = remainingStudents.size();
            int cdGroupsCount = cdGroups.size();
            
            if (cdGroupsCount > 0) {
                int studentsPerGroup = totalRemainingStudents / cdGroupsCount;
                int extraStudents = totalRemainingStudents % cdGroupsCount;
                
                int studentIndex = 0;
                List<User> toUpdate = new ArrayList<>();
                for (int groupIndex = 0; groupIndex < cdGroups.size(); groupIndex++) {
                    String groupName = cdGroups.get(groupIndex);
                    
                    // 计算当前组应该分配的学生数量
                    int currentGroupStudents = studentsPerGroup;
                    if (groupIndex < extraStudents) {
                        currentGroupStudents++; // 前几个组多分配一个学生
                    }
                    
                    // 为当前组分配学生
                    for (int i = 0; i < currentGroupStudents && studentIndex < remainingStudents.size(); i++) {
                        User student = remainingStudents.get(studentIndex);
                        applyGroupAssignment(student, groupName, semesterId, suiteId, weekType);
                        toUpdate.add(student);
                        studentIndex++;
                    }
                }
                if (!toUpdate.isEmpty()) {
                    userService.updateBatchById(toUpdate);
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("分配C组D组失败", e);
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
