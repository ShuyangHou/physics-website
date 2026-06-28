package com.physics.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.physics.common.Result;
import com.physics.entity.*;
import com.physics.service.*;
// GroupService import removed - Group entity deleted
import com.physics.dto.GradeDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import java.util.ArrayList;

import com.physics.entity.ExtraFile;
import com.physics.service.ExtraFileService;
import com.physics.utils.FileUtil;

@RestController
@RequestMapping("/grade")
public class ExperimentGradeController {
    
    @Autowired
    private ExperimentGradeService gradeService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ExperimentService experimentService;
    
    // GroupService removed - Group entity deleted

    @Autowired
    private ExperimentScheduleService experimentScheduleService;
    
    
    @Autowired
    private GroupExperimentService groupExperimentService;

    private void applyGroupIdsContains(QueryWrapper<ExperimentSchedule> wrapper, String groupName) {
        wrapper.apply("FIND_IN_SET({0}, REPLACE(REPLACE(REPLACE(group_ids, '[', ''), ']', ''), '\"', '')) > 0", groupName);
    }

    @Autowired
    private ExtraFileService extraFileService;

    @Autowired
    private FileUtil fileUtil;

    /**
     * 轻量查询：按小组 + 实验 获取该小组学生已录入的成绩
     * 可选参数 semesterId/suiteId 仅用于前端校验，不参与查询（成绩表无学期/套件字段）
     * 返回 [{ userId, score }]
     */
    @GetMapping("/by-group-experiment")
    public Result<List<Map<String, Object>>> getGradesByGroupAndExperiment(
            @CookieValue("userId") Long currentUserId,
            @RequestParam String groupName,
            @RequestParam Long experimentId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long suiteId
    ) {
        if (groupName == null || groupName.trim().isEmpty() || experimentId == null) {
            return Result.error("参数不完整");
        }

        // 找到该小组的所有学生ID
        List<User> students = userService.list(new QueryWrapper<User>().eq("group_name", groupName));
        if (students == null || students.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        List<Long> userIds = students.stream().map(User::getUserId).collect(Collectors.toList());

        User current = userService.getById(currentUserId);
        if (current == null) {
            return Result.error("用户不存在");
        }
        boolean isTeacher = "teacher".equalsIgnoreCase(current.getUserType());
        boolean isAdmin = "admin".equalsIgnoreCase(current.getUserType());
        if (!isTeacher && !isAdmin) {
            return Result.error("无权查看小组成绩");
        }

        // 查询这些学生在指定实验下的成绩
        QueryWrapper<ExperimentGrade> q = new QueryWrapper<>();
        q.eq("experiment_id", experimentId).in("user_id", userIds);
        if (semesterId != null) {
            q.eq("semester_id", semesterId);
        }
        if (suiteId != null) {
            q.eq("suite_id", suiteId);
        }
        if (isTeacher) {
            q.and(w -> w.isNull("teacher_id").or().eq("teacher_id", currentUserId));
        }
        q.orderByDesc("update_time");
        List<ExperimentGrade> grades = gradeService.list(q);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ExperimentGrade g : grades) {
            Map<String, Object> item = new HashMap<>();
            item.put("userId", g.getUserId());
            item.put("score", g.getScore());
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 一次获取小组在当前学期、实验套下的全部成绩，避免前端按实验逐个请求。
     */
    @GetMapping("/group-suite-grades")
    public Result<List<Map<String, Object>>> getGroupSuiteGrades(
            @CookieValue("userId") Long currentUserId,
            @RequestParam String groupName,
            @RequestParam Long semesterId,
            @RequestParam Long suiteId) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return Result.error("小组名称不能为空");
        }

        User current = userService.getById(currentUserId);
        if (current == null) {
            return Result.error("用户不存在");
        }
        boolean isTeacher = "teacher".equalsIgnoreCase(current.getUserType());
        boolean isAdmin = "admin".equalsIgnoreCase(current.getUserType());
        if (!isTeacher && !isAdmin) {
            return Result.error("无权查看小组成绩");
        }

        QueryWrapper<ExperimentGrade> wrapper = new QueryWrapper<ExperimentGrade>()
                .eq("group_name", groupName)
                .eq("semester_id", semesterId)
                .eq("suite_id", suiteId)
                .orderByDesc("update_time");
        List<ExperimentGrade> grades = gradeService.list(wrapper);

        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExperimentGrade grade : grades) {
            String key = grade.getUserId() + "_" + grade.getExperimentId();
            if (!seen.add(key)) {
                continue;
            }
            boolean otherTeacher = isTeacher
                    && grade.getTeacherId() != null
                    && !currentUserId.equals(grade.getTeacherId());
            Map<String, Object> item = new HashMap<>();
            item.put("userId", grade.getUserId());
            item.put("experimentId", grade.getExperimentId());
            item.put("score", otherTeacher ? null : grade.getScore());
            item.put("isReadOnly", otherTeacher);
            item.put("isLocked", grade.getIsLocked());
            result.add(item);
        }
        return Result.success(result);
    }

    private List<String> parseScheduleGroupIds(String groupIds) {
        List<String> result = new ArrayList<>();
        if (groupIds == null || groupIds.trim().isEmpty()) {
            return result;
        }
        try {
            String clean = groupIds.replaceAll("[\\[\\]\"]", "");
            if (clean.trim().isEmpty()) {
                return result;
            }
            String[] parts = clean.split(",");
            for (String p : parts) {
                String v = p == null ? "" : p.trim();
                if (!v.isEmpty()) {
                    result.add(v);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    /**
     * 新增：按小组获取学生列表（用于成绩录入）- 优化横屏显示
     * 支持从user表直接查询，如果查不到则从experiment_schedule表获取班级信息后查询
     * 支持按学期、实验套、周类型区分单周和双周的分组
     */
    @GetMapping("/students-by-group")
    public Result<Map<String, Object>> getStudentsByGroup(
            @RequestParam String groupName,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long suiteId,
            @RequestParam(required = false) Integer weekType) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return Result.error("小组名称不能为空");
        }

        List<String> scheduleClassList = new ArrayList<>();
        if (semesterId != null && suiteId != null && weekType != null) {
            QueryWrapper<ExperimentSchedule> scheduleWrapper = new QueryWrapper<>();
            applyGroupIdsContains(scheduleWrapper, groupName);
            scheduleWrapper.eq("semester_id", semesterId);
            scheduleWrapper.eq("suite_id", suiteId);
            if (weekType == 1) {
                scheduleWrapper.eq("week_type", 1);
            } else {
                scheduleWrapper.and(w -> w.isNull("week_type").or().eq("week_type", 0));
            }

            List<ExperimentSchedule> schedules = experimentScheduleService.list(scheduleWrapper);
            if (schedules == null || schedules.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("groupName", groupName);
                empty.put("totalCount", 0);
                empty.put("students", new ArrayList<>());
                empty.put("scheduleMatched", false);
                empty.put("scheduleClassIds", scheduleClassList);
                empty.put("weekType", weekType);
                empty.put("reason", "课表未安排该小组");
                empty.put("columns", buildBasicStudentColumns());
                return Result.success(empty);
            }

            for (ExperimentSchedule schedule : schedules) {
                if (schedule.getClassIds() == null || schedule.getClassIds().trim().isEmpty()) continue;
                String[] classes = schedule.getClassIds().split(",");
                for (String cls : classes) {
                    String trimmed = cls == null ? "" : cls.trim();
                    if (!trimmed.isEmpty() && !scheduleClassList.contains(trimmed)) {
                        scheduleClassList.add(trimmed);
                    }
                }
            }
        }

        // 方案1：直接根据小组名称从user表获取学生列表
        // 如果提供了 semesterId、suiteId、weekType，则使用这些条件精确查询（区分单周和双周）
        QueryWrapper<User> userWrapper = new QueryWrapper<>();
        userWrapper.eq("group_name", groupName)
                   .eq("user_type", "student");
        
        if (!scheduleClassList.isEmpty()) {
            userWrapper.in("class_id", scheduleClassList);
        }

        if (semesterId != null && suiteId != null && weekType != null) {
            userWrapper.eq("semester_id", semesterId);
            userWrapper.eq("suite_id", suiteId);
            userWrapper.eq("week_type", weekType);
        }
        
        userWrapper.orderBy(true, true, "school_id"); // 按学号排序
        
        List<User> students = userService.list(userWrapper);

        if ((students == null || students.isEmpty()) && !scheduleClassList.isEmpty()) {
            QueryWrapper<User> fallback = new QueryWrapper<>();
            fallback.eq("group_name", groupName)
                    .eq("user_type", "student")
                    .in("class_id", scheduleClassList)
                    .orderBy(true, true, "school_id");
            students = userService.list(fallback);
        }

        // 方案2：如果直接从user表查不到学生，从experiment_schedule表获取班级信息
        if (students == null || students.isEmpty()) {
            // 从experiment_schedule表查找包含该小组的记录
            QueryWrapper<ExperimentSchedule> scheduleWrapper = new QueryWrapper<>();
            applyGroupIdsContains(scheduleWrapper, groupName);
            
            // 如果提供了参数，则按这些条件过滤（区分单周和双周）
            if (semesterId != null) {
                scheduleWrapper.eq("semester_id", semesterId);
            }
            if (suiteId != null) {
                scheduleWrapper.eq("suite_id", suiteId);
            }
            if (weekType != null) {
                if (weekType == 1) {
                    scheduleWrapper.eq("week_type", 1);
                } else {
                    scheduleWrapper.and(w -> w.isNull("week_type").or().eq("week_type", 0));
                }
            }
            
            List<ExperimentSchedule> schedules = experimentScheduleService.list(scheduleWrapper);
            
            if (schedules != null && !schedules.isEmpty()) {
                // 获取所有相关的班级列表（从class_ids字段解析）
                List<String> classList = new ArrayList<>();
                for (ExperimentSchedule schedule : schedules) {
                    if (schedule.getClassIds() != null && !schedule.getClassIds().trim().isEmpty()) {
                        String classIdsStr = schedule.getClassIds();
                        // 解析班级ID列表（格式：231011,231012,231013,231014）
                        String[] classes = classIdsStr.split(",");
                        for (String cls : classes) {
                            String trimmedClass = cls.trim();
                            if (!trimmedClass.isEmpty() && !classList.contains(trimmedClass)) {
                                classList.add(trimmedClass);
                            }
                        }
                    }
                }
                
                if (!classList.isEmpty()) {
                    // 判断是A/B组还是C/D组
                    char groupType = groupName.length() > 0 ? groupName.charAt(groupName.length() - 1) : ' ';
                    
                    if (groupType == 'C' || groupType == 'c' || groupType == 'D' || groupType == 'd') {
                        // C组和D组：需要所有班级的剩余学生
                        students = getStudentsForCDGroup(classList, groupName);
                    } else {
                        // A组和B组：从小组名提取班级前缀，只查询该班级的学生
                        String className = extractClassNameFromGroupName(groupName);
                        if (className != null && !className.isEmpty() && classList.contains(className)) {
                            List<User> classStudents = userService.getStudentsByClassName(className);
                            if (classStudents != null && !classStudents.isEmpty()) {
                                students = filterStudentsByGroupType(classStudents, groupName);
                            }
                        }
                    }
                }
            }
        }

        if (!scheduleClassList.isEmpty() && students != null && !students.isEmpty()) {
            List<User> filtered = new ArrayList<>();
            for (User s : students) {
                if (s != null && s.getClassId() != null && scheduleClassList.contains(String.valueOf(s.getClassId()).trim())) {
                    filtered.add(s);
                }
            }
            students = filtered;
        }

        List<Map<String, Object>> studentList = new ArrayList<>();
        int index = 1;
        for (User student : students) {
            Map<String, Object> item = new HashMap<>();
            item.put("userId", student.getUserId());
            item.put("schoolId", student.getSchoolId());
            item.put("studentName", student.getRealName());
            item.put("classId", student.getClassId());
            item.put("groupName", groupName); // 使用传入的groupName，而不是数据库中的
            item.put("score", null); // 初始成绩为空
            item.put("gradeId", null);
            item.put("status", "待录入");
            item.put("index", index++);
            studentList.add(item);
        }

        // 构建适合横屏显示的响应结构
        Map<String, Object> response = new HashMap<>();
        response.put("groupName", groupName);
        response.put("totalCount", studentList.size());
        response.put("students", studentList);
        if (semesterId != null && suiteId != null && weekType != null) {
            response.put("scheduleMatched", true);
            response.put("scheduleClassIds", scheduleClassList);
            response.put("weekType", weekType);
            if (studentList.isEmpty()) {
                response.put("reason", "课表已安排但未匹配到学生（请检查学生班级信息或分组信息）");
            }
        }
        
        // 添加表格列配置，便于前端横屏布局
        response.put("columns", buildBasicStudentColumns());

        return Result.success(response);
    }
    
    /**
     * 从小组名提取班级名（如 231011A -> 231011）
     */
    private String extractClassNameFromGroupName(String groupName) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return null;
        }
        String trimmed = groupName.trim();
        // 小组名格式通常是：班级名+字母（如 231011A, 231011B）
        // 去除最后一个字符（A/B/C/D等）
        if (trimmed.length() > 1) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
    
    /**
     * 获取C组或D组的学生（包含所有班级的剩余学生）
     * 规则：
     * - 两个班级：只有一个C组，包含所有剩余学生
     * - 四个班级：有C组和D组，每个组12个学生
     */
    private List<User> getStudentsForCDGroup(List<String> classList, String groupName) {
        if (classList == null || classList.isEmpty() || groupName == null || groupName.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // 收集所有班级中未分配到A组和B组的学生
        List<User> remainingStudents = new ArrayList<>();
        
        for (String className : classList) {
            List<User> classStudents = userService.getStudentsByClassName(className);
            if (classStudents == null || classStudents.isEmpty()) {
                continue;
            }
            
            // 排除A组（前12个）和B组（第13-24个）的学生
            // 剩余学生 = 从第25个开始的所有学生
            int remainingStart = 24; // 跳过前24个学生（A组12人 + B组12人）
            for (int i = remainingStart; i < classStudents.size(); i++) {
                remainingStudents.add(classStudents.get(i));
            }
        }
        
        if (remainingStudents.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 确定是C组还是D组
        char groupType = groupName.charAt(groupName.length() - 1);
        
        // 判断班级数量
        if (classList.size() == 2) {
            // 两个班级：只有一个C组，包含所有剩余学生
            if (groupType == 'C' || groupType == 'c') {
                return remainingStudents; // C组包含所有剩余学生
            } else {
                // 两个班级时没有D组
                return new ArrayList<>();
            }
        } else {
            // 四个班级：有C组和D组，每个组12个学生
            int studentsPerGroup = 12;
            int startIndex;
            
            if (groupType == 'C' || groupType == 'c') {
                // C组：从剩余学生的第1个开始，分配12个
                startIndex = 0;
            } else if (groupType == 'D' || groupType == 'd') {
                // D组：从剩余学生的第13个开始，分配12个
                startIndex = 12;
            } else {
                return new ArrayList<>();
            }
            
            // 返回当前组的学生（最多12个）
            List<User> filtered = new ArrayList<>();
            int endIndex = Math.min(startIndex + studentsPerGroup, remainingStudents.size());
            for (int i = startIndex; i < endIndex; i++) {
                filtered.add(remainingStudents.get(i));
            }
            
            return filtered;
        }
    }
    
    /**
     * 根据小组类型筛选学生（用于A组和B组）
     * A组：前12个学生，B组：第13-24个学生
     */
    private List<User> filterStudentsByGroupType(List<User> allStudents, String groupName) {
        if (allStudents == null || allStudents.isEmpty() || groupName == null || groupName.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String trimmedGroupName = groupName.trim();
        if (trimmedGroupName.length() < 2) {
            return new ArrayList<>();
        }
        
        char groupType = trimmedGroupName.charAt(trimmedGroupName.length() - 1);
        
        List<User> filtered = new ArrayList<>();
        
        switch (groupType) {
            case 'A':
            case 'a':
                // A组：前12个学生
                int aCount = Math.min(12, allStudents.size());
                for (int i = 0; i < aCount; i++) {
                    filtered.add(allStudents.get(i));
                }
                break;
            case 'B':
            case 'b':
                // B组：第13-24个学生
                int bStart = 12;
                int bCount = Math.min(12, allStudents.size() - bStart);
                for (int i = bStart; i < bStart + bCount && i < allStudents.size(); i++) {
                    filtered.add(allStudents.get(i));
                }
                break;
            case 'C':
            case 'c':
                // C组：从第25个开始，平均分配剩余学生
                int remainingStart = 24;
                int remainingCount = allStudents.size() - remainingStart;
                if (remainingCount > 0) {
                    int cCount = (remainingCount + 1) / 2; // C组和D组平均分配，向上取整
                    for (int i = remainingStart; i < remainingStart + cCount && i < allStudents.size(); i++) {
                        filtered.add(allStudents.get(i));
                    }
                }
                break;
            case 'D':
            case 'd':
                // D组：剩余的后面一半学生
                int dRemainingStart = 24;
                int dRemainingCount = allStudents.size() - dRemainingStart;
                if (dRemainingCount > 0) {
                    int dStart = dRemainingStart + (dRemainingCount + 1) / 2; // C组之后开始
                    for (int i = dStart; i < allStudents.size(); i++) {
                        filtered.add(allStudents.get(i));
                    }
                }
                break;
            default:
                // 未知类型，返回空列表
                return new ArrayList<>();
        }
        
        return filtered;
    }
    
    /**
     * 创建表格列配置的辅助方法
     */
    private Map<String, Object> createColumn(String key, String title, String width, String align) {
        Map<String, Object> column = new HashMap<>();
        column.put("key", key);
        column.put("title", title);
        column.put("width", width);
        column.put("align", align);
        column.put("fixed", false);
        return column;
    }

    /**
     * 成绩录入表格的基础列配置（序号/学号/姓名/班级/小组/成绩/状态）。
     */
    private List<Map<String, Object>> buildBasicStudentColumns() {
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(createColumn("index", "序号", "60px", "center"));
        columns.add(createColumn("schoolId", "学号", "120px", "center"));
        columns.add(createColumn("studentName", "姓名", "100px", "center"));
        columns.add(createColumn("classId", "班级", "100px", "center"));
        columns.add(createColumn("groupName", "小组", "100px", "center"));
        columns.add(createColumn("score", "成绩", "120px", "center"));
        columns.add(createColumn("status", "状态", "80px", "center"));
        return columns;
    }

    /**
     * 批量按ID预取用户，返回 userId -> User 映射，避免循环内逐条查库。
     */
    private Map<Long, User> loadUserMapByIds(java.util.Collection<Long> ids) {
        List<Long> distinct = ids == null ? new ArrayList<>() : ids.stream()
                .filter(id -> id != null).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return new HashMap<>();
        }
        return userService.listByIds(distinct).stream()
                .filter(u -> u != null && u.getUserId() != null)
                .collect(Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));
    }

    /**
     * 批量按ID预取实验，返回 experimentId -> Experiment 映射，避免循环内逐条查库。
     */
    private Map<Long, Experiment> loadExperimentMapByIds(java.util.Collection<Long> ids) {
        List<Long> distinct = ids == null ? new ArrayList<>() : ids.stream()
                .filter(id -> id != null).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return new HashMap<>();
        }
        return experimentService.listByIds(distinct).stream()
                .filter(e -> e != null && e.getExperimentId() != null)
                .collect(Collectors.toMap(Experiment::getExperimentId, e -> e, (a, b) -> a));
    }
    
    /**
     * 获取小组学生成绩详情 - 专为横屏显示优化
     */
    @GetMapping("/group-students-detail")
    public Result<Map<String, Object>> getGroupStudentsDetail(
            @CookieValue("userId") Long currentUserId,
            @RequestParam String groupName,
            @RequestParam(required = false) Long experimentId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long suiteId) {
        
        if (groupName == null || groupName.trim().isEmpty()) {
            return Result.error("小组名称不能为空");
        }

        User current = userService.getById(currentUserId);
        if (current == null) {
            return Result.error("用户不存在");
        }
        boolean isTeacher = "teacher".equalsIgnoreCase(current.getUserType());

        // 根据小组名称获取学生列表
        List<User> students = userService.list(new QueryWrapper<User>()
                .eq("group_name", groupName)
                .eq("user_type", "student")
                .orderBy(true, true, "school_id")); // 按学号排序

        Map<Long, List<ExperimentGrade>> gradesByUser = new HashMap<>();
        if (experimentId != null && students != null && !students.isEmpty()) {
            List<Long> studentIds = students.stream()
                    .map(User::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            QueryWrapper<ExperimentGrade> gradeWrapper = new QueryWrapper<ExperimentGrade>()
                    .in("user_id", studentIds)
                    .eq("experiment_id", experimentId);
            if (semesterId != null) {
                gradeWrapper.and(w -> w.eq("semester_id", semesterId).or().isNull("semester_id"));
            }
            if (suiteId != null) {
                gradeWrapper.and(w -> w.eq("suite_id", suiteId).or().isNull("suite_id"));
            }
            gradeWrapper.orderByDesc("update_time");
            gradesByUser = gradeService.list(gradeWrapper).stream()
                    .collect(Collectors.groupingBy(ExperimentGrade::getUserId, LinkedHashMap::new, Collectors.toList()));
        }

        List<Map<String, Object>> studentList = new ArrayList<>();
        int index = 1;
        
        for (User student : students) {
            Map<String, Object> item = new HashMap<>();
            item.put("userId", student.getUserId());
            item.put("schoolId", student.getSchoolId());
            item.put("studentName", student.getRealName());
            item.put("classId", student.getClassId());
            item.put("groupName", student.getGroupName());
            item.put("index", index++);
            
            if (experimentId != null) {
                List<ExperimentGrade> userGrades = gradesByUser.getOrDefault(student.getUserId(), new ArrayList<>());
                ExperimentGrade grade = null;
                ExperimentGrade otherTeacherGrade = null;
                for (ExperimentGrade candidate : userGrades) {
                    if (!isTeacher || candidate.getTeacherId() == null || currentUserId.equals(candidate.getTeacherId())) {
                        grade = candidate;
                        break;
                    }
                    if (otherTeacherGrade == null) {
                        otherTeacherGrade = candidate;
                    }
                }

                if (grade == null && otherTeacherGrade != null) {
                    item.put("score", null);
                    item.put("remark", null);
                    item.put("gradeId", null);
                    item.put("isLocked", otherTeacherGrade.getIsLocked());
                    item.put("isReadOnly", true);
                    item.put("status", "已由其他教师录入");
                    item.put("createTime", null);
                    item.put("updateTime", null);
                    item.put("isModified", false);
                    studentList.add(item);
                    continue;
                }

                if (grade != null) {
                    boolean isOtherTeacherGrade = isTeacher
                            && grade.getTeacherId() != null
                            && !grade.getTeacherId().equals(currentUserId);

                    item.put("isLocked", grade.getIsLocked());
                    item.put("isReadOnly", isOtherTeacherGrade);

                    if (isOtherTeacherGrade) {
                        item.put("score", null);
                        item.put("remark", null);
                        item.put("gradeId", null);
                        item.put("status", "已由其他教师录入");
                        item.put("createTime", null);
                        item.put("updateTime", null);
                        item.put("isModified", false);
                        studentList.add(item);
                        continue;
                    }

                    // 处理score的类型转换
                    Object scoreObj = grade.getScore();
                    Object scoreValue = null;
                    if (scoreObj != null) {
                        if (scoreObj instanceof Number) {
                            scoreValue = ((Number) scoreObj).doubleValue();
                        } else if (scoreObj instanceof String) {
                            try {
                                scoreValue = Double.parseDouble((String) scoreObj);
                            } catch (NumberFormatException e) {
                                String s = ((String) scoreObj).trim();
                                scoreValue = s.isEmpty() ? null : s;
                            }
                        }
                    }
                    item.put("score", scoreValue);
                    item.put("remark", grade.getRemark());
                    item.put("gradeId", grade.getGradeId());
                    item.put("status", "已录入");
                    item.put("createTime", grade.getCreateTime());
                    item.put("updateTime", grade.getUpdateTime());
                    boolean isModified = false;
                    try {
                        if (grade.getCreateTime() != null && grade.getUpdateTime() != null) {
                            isModified = grade.getUpdateTime().isAfter(grade.getCreateTime());
                        }
                    } catch (Exception ignore) {
                    }
                    item.put("isModified", isModified);
                } else {
                    item.put("score", null);
                    item.put("remark", null);
                    item.put("gradeId", null);
                    item.put("isLocked", false);
                    item.put("isReadOnly", false);
                    item.put("status", "待录入");
                    item.put("createTime", null);
                    item.put("updateTime", null);
                    item.put("isModified", false);
                }
            } else {
                item.put("score", null);
                item.put("remark", null);
                item.put("gradeId", null);
                item.put("isLocked", false);
                item.put("isReadOnly", false);
                item.put("status", "待录入");
                item.put("createTime", null);
                item.put("updateTime", null);
                item.put("isModified", false);
            }
            
            studentList.add(item);
        }

        // 构建横屏显示优化的响应结构
        Map<String, Object> response = new HashMap<>();
        response.put("groupName", groupName);
        response.put("experimentId", experimentId);
        response.put("totalCount", studentList.size());
        response.put("students", studentList);
        
        // 优化的表格列配置 - 适合横屏显示，列宽更合理分配
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(createColumn("index", "序号", "8%", "center"));
        columns.add(createColumn("schoolId", "学号", "18%", "center"));
        columns.add(createColumn("studentName", "姓名", "15%", "center"));
        columns.add(createColumn("classId", "班级", "15%", "center"));
        columns.add(createColumn("groupName", "小组", "15%", "center"));
        columns.add(createColumn("score", "成绩", "18%", "center"));
        columns.add(createColumn("remark", "备注", "18%", "center"));
        columns.add(createColumn("status", "状态", "11%", "center"));
        response.put("columns", columns);
        
        // 添加布局提示信息
        Map<String, Object> layoutConfig = new HashMap<>();
        layoutConfig.put("tableWidth", "100%");
        layoutConfig.put("responsive", true);
        layoutConfig.put("horizontalScroll", false);
        layoutConfig.put("fixedHeader", true);
        response.put("layoutConfig", layoutConfig);

        return Result.success(response);
    }

    /**
     * 新增：批量录入/覆盖成绩（唯一键 userId+experimentId）
     */
    @PostMapping("/batch-upsert")
    public Result<Boolean> batchUpsertGrades(@CookieValue("userId") Long currentUserId,
                                             @RequestBody BatchUpsertRequest req) {
        // 基础校验
        if (req == null || req.getExperimentId() == null ||
                req.getSemesterId() == null || req.getSuiteId() == null ||
                req.getGroupName() == null || req.getGroupName().trim().isEmpty() ||
                req.getItems() == null || req.getItems().isEmpty()) {
            return Result.error("参数不完整");
        }

        // 权限：教师或管理员
        User current = userService.getById(currentUserId);
        if (current == null) return Result.error("用户不存在");
        boolean isTeacher = "teacher".equalsIgnoreCase(current.getUserType());
        boolean isAdmin = "admin".equalsIgnoreCase(current.getUserType());
        if (!isTeacher && !isAdmin) return Result.error("无权操作");

        // 教师权限验证：检查是否有权限操作指定的实验和小组
        if (isTeacher) {
            // 通过GroupExperiment表检查教师是否有权限操作指定的实验和小组
            // 先查询是否存在匹配的记录（不考虑teacher_id）
            QueryWrapper<GroupExperiment> groupExpWrapper = new QueryWrapper<>();
            groupExpWrapper.eq("group_name", req.getGroupName())
                          .eq("experiment_id", req.getExperimentId())
                          .eq("semester_id", req.getSemesterId())
                          .eq("suite_id", req.getSuiteId());
            
            List<GroupExperiment> groupExps = groupExperimentService.list(groupExpWrapper);
            if (groupExps.isEmpty()) {
                return Result.error("您没有权限操作该实验的成绩：未找到匹配的实验配置");
            }
            
            // 检查teacher_id权限：
            // 1. 如果teacher_id为null，允许操作（自动分组时可能未分配教师）
            // 2. 如果teacher_id不为null，必须与当前用户匹配
            boolean hasPermission = false;
            for (GroupExperiment ge : groupExps) {
                if (ge.getTeacherId() == null) {
                    // teacher_id为null，允许任何教师操作
                    hasPermission = true;
                    break;
                } else if (ge.getTeacherId().equals(currentUserId)) {
                    // teacher_id匹配当前用户
                    hasPermission = true;
                    break;
                }
            }
            
            if (!hasPermission) {
                return Result.error("您没有权限操作该实验的成绩：该实验已分配给其他教师");
            }
        }

        Map<Long, BatchUpsertItem> itemsByUserId = new LinkedHashMap<>();
        for (BatchUpsertItem it : req.getItems()) {
            if (it != null && it.getUserId() != null) {
                itemsByUserId.put(it.getUserId(), it);
            }
        }

        Map<Long, ExperimentGrade> existingByUserId = new HashMap<>();
        if (!itemsByUserId.isEmpty()) {
            List<ExperimentGrade> existingGrades = gradeService.list(new QueryWrapper<ExperimentGrade>()
                    .in("user_id", itemsByUserId.keySet())
                    .eq("experiment_id", req.getExperimentId())
                    .eq("semester_id", req.getSemesterId())
                    .eq("suite_id", req.getSuiteId())
                    .orderByDesc("update_time"));
            for (ExperimentGrade grade : existingGrades) {
                if (grade != null && grade.getUserId() != null) {
                    existingByUserId.putIfAbsent(grade.getUserId(), grade);
                }
            }
        }

        List<ExperimentGrade> toCreate = new ArrayList<>();

        // 批量预查后逐条 upsert（按 userId+experimentId+semesterId+suiteId）
        for (BatchUpsertItem it : itemsByUserId.values()) {
            if (it.getUserId() == null) continue;
            ExperimentGrade existing = existingByUserId.get(it.getUserId());
            if (existing == null) {
                ExperimentGrade g = new ExperimentGrade();
                g.setUserId(it.getUserId());
                g.setExperimentId(req.getExperimentId());
                g.setGroupName(req.getGroupName());
                g.setTeacherId(isTeacher ? currentUserId : req.getTeacherId());
                g.setSemesterId(req.getSemesterId());
                g.setSuiteId(req.getSuiteId());
                g.setScore(it.getScore());
                g.setRemark(it.getRemark());
                g.setIsLocked(Boolean.FALSE);
                toCreate.add(g);
            } else {
                if (Boolean.TRUE.equals(existing.getIsLocked())) {
                    // 跳过锁定成绩
                    continue;
                }
                if (isTeacher && existing.getTeacherId() != null && !existing.getTeacherId().equals(currentUserId)) {
                    return Result.error("该成绩已由其他教师录入，您无权覆盖");
                }

                // 只在“成绩(score)”发生变动时才更新 update_time；备注等其它字段变化不触发“修改色”
                Object oldScore = existing.getScore();
                Object newScore = it.getScore();
                String oldScoreStr = oldScore == null ? "" : String.valueOf(oldScore).trim();
                String newScoreStr = newScore == null ? "" : String.valueOf(newScore).trim();
                boolean scoreChanged = !oldScoreStr.equalsIgnoreCase(newScoreStr);

                String oldRemark = existing.getRemark() == null ? "" : existing.getRemark().trim();
                String newRemark = it.getRemark() == null ? "" : it.getRemark().trim();
                boolean remarkChanged = !oldRemark.equals(newRemark);

                boolean groupChanged = req.getGroupName() != null && !req.getGroupName().equals(existing.getGroupName());
                Long nextTeacherId = isTeacher ? currentUserId : req.getTeacherId();
                boolean teacherChanged = (nextTeacherId != null && !nextTeacherId.equals(existing.getTeacherId())) || (nextTeacherId == null && existing.getTeacherId() != null);

                if (!scoreChanged && !remarkChanged && !groupChanged && !teacherChanged) {
                    continue;
                }

                UpdateWrapper<ExperimentGrade> uw = new UpdateWrapper<>();
                uw.eq("grade_id", existing.getGradeId());
                uw.set("group_name", req.getGroupName());
                uw.set("teacher_id", nextTeacherId);
                uw.set("semester_id", req.getSemesterId());
                uw.set("suite_id", req.getSuiteId());
                uw.set("score", it.getScore());
                uw.set("remark", it.getRemark());

                if (scoreChanged) {
                    // 保证 update_time 严格晚于 create_time（避免数据库时间精度导致 update_time == create_time）
                    try {
                        LocalDateTime now = LocalDateTime.now();
                        if (existing.getCreateTime() != null && !now.isAfter(existing.getCreateTime())) {
                            now = existing.getCreateTime().plusSeconds(1);
                        }
                        uw.set("update_time", now);
                    } catch (Exception ignore) {
                    }
                }

                gradeService.update(uw);
            }
        }
        if (!toCreate.isEmpty()) {
            gradeService.saveBatch(toCreate);
        }
        return Result.success(true);
    }

    public static class BatchUpsertRequest {
        private Long experimentId;
        private String groupName;
        private Long semesterId;
        private Long suiteId;
        private Long teacherId; // 管理员可指定；教师忽略此字段，用 cookie 中的本人
        private List<BatchUpsertItem> items;
        public Long getExperimentId() { return experimentId; }
        public void setExperimentId(Long experimentId) { this.experimentId = experimentId; }
        public String getGroupName() { return groupName; }
        public void setGroupName(String groupName) { this.groupName = groupName; }
        public Long getSemesterId() { return semesterId; }
        public void setSemesterId(Long semesterId) { this.semesterId = semesterId; }
        public Long getSuiteId() { return suiteId; }
        public void setSuiteId(Long suiteId) { this.suiteId = suiteId; }
        public Long getTeacherId() { return teacherId; }
        public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
        public List<BatchUpsertItem> getItems() { return items; }
        public void setItems(List<BatchUpsertItem> items) { this.items = items; }
    }
    public static class BatchUpsertItem {
        private Long userId;
        private Object score; // 支持Double和String("N")
        private String remark;
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Object getScore() { return score; }
        public void setScore(Object score) { this.score = score; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
    
    @GetMapping("/list")
    public Result<Page<GradeDTO>> getGradeList(
            @CookieValue("userId") Long userId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) Long experimentId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long semesterId) {
        Page<ExperimentGrade> page = new Page<>(current, size);
        QueryWrapper<ExperimentGrade> wrapper = new QueryWrapper<>();

        // 获取当前用户身份
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        String userType = user.getUserType();

        if ("student".equals(userType)) {
            // 学生只能查自己
            wrapper.eq("user_id", userId);
        } else if ("teacher".equals(userType)) {
            // 老师只能查自己录入的成绩
            wrapper.eq("teacher_id", userId);
        } // 管理员不加限制

        // 学期过滤
        if (semesterId != null) {
            wrapper.eq("semester_id", semesterId);
        }

        // 其它筛选条件
        if (userName != null && !userName.isEmpty()) {
            List<User> users = userService.list(new QueryWrapper<User>().like("real_name", userName));
            if (!users.isEmpty()) {
                List<Long> userIds = users.stream().map(User::getUserId).collect(Collectors.toList());
                wrapper.in("user_id", userIds);
            } else {
                Page<GradeDTO> emptyPage = new Page<>(current, size);
                return Result.success(emptyPage);
            }
        }
        if (experimentId != null) {
            wrapper.eq("experiment_id", experimentId);
        }
        if (groupId != null) {
            // 由于删除了Group实体，这里改为按groupName过滤
            // 需要先获取groupName，然后按groupName过滤
            // 暂时注释掉，因为需要重新设计分组查询逻辑
            // wrapper.eq("group_name", groupName);
        }
        wrapper.orderByDesc("create_time");
        Page<ExperimentGrade> result = gradeService.page(page, wrapper);

        // 批量查询用户和实验信息，避免N+1查询问题
        List<ExperimentGrade> grades = result.getRecords();

        Map<Long, User> finalUserMap = loadUserMapByIds(grades.stream()
                .map(ExperimentGrade::getUserId).collect(Collectors.toList()));
        Map<Long, Experiment> finalExperimentMap = loadExperimentMapByIds(grades.stream()
                .map(ExperimentGrade::getExperimentId).collect(Collectors.toList()));

        // 转换为DTO
        List<GradeDTO> dtoList = grades.stream().map(grade -> {
            GradeDTO dto = new GradeDTO();
            dto.setGradeId(grade.getGradeId());
            dto.setUserId(grade.getUserId());
            dto.setExperimentId(grade.getExperimentId());
            dto.setScore(grade.getScore());
            dto.setCreateTime(grade.getCreateTime());
            dto.setUpdateTime(grade.getUpdateTime());
            
            // 从缓存的用户Map中获取信息
            User student = finalUserMap.get(grade.getUserId());
            if (student != null) {
                dto.setStudentName(student.getRealName());
                dto.setGroupName(student.getGroupName());
            }
            
            // 从缓存的实验Map中获取信息
            Experiment experiment = finalExperimentMap.get(grade.getExperimentId());
            if (experiment != null) {
                dto.setExperimentName(experiment.getExperimentName());
            }
            
            return dto;
        }).collect(Collectors.toList());
        // 创建新的Page对象
        Page<GradeDTO> dtoPage = new Page<>(current, size);
        dtoPage.setRecords(dtoList);
        dtoPage.setTotal(result.getTotal());
        dtoPage.setCurrent(result.getCurrent());
        dtoPage.setSize(result.getSize());
        return Result.success(dtoPage);
    }
    
    @PostMapping("/add")
    public Result<Boolean> addGrade(@CookieValue("userId") Long userId, @RequestBody ExperimentGrade grade) {
        // 权限检查
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        
        if ("teacher".equals(user.getUserType())) {
            // 教师权限检查 - 暂时允许所有教师添加成绩
            // TODO: 实现具体的教师权限验证逻辑
        }
        
        boolean result = gradeService.save(grade);
        return Result.success(result);
    }
    
    @PostMapping("/update")
    public Result<Boolean> updateGrade(@CookieValue("userId") Long userId, @RequestBody ExperimentGrade grade) {
        // 权限检查
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        
        if ("teacher".equals(user.getUserType())) {
            // 教师权限检查 - 暂时允许所有教师修改成绩
            // TODO: 实现具体的教师权限验证逻辑
        }
        
        // 检查是否已锁定
        ExperimentGrade existingGrade = gradeService.getById(grade.getGradeId());
        if (existingGrade != null && Boolean.TRUE.equals(existingGrade.getIsLocked())) {
            return Result.error("该成绩已锁定，无法修改");
        }
        
        boolean result = gradeService.updateById(grade);
        return Result.success(result);
    }
    
    @GetMapping("/teacherSchedule")
    public Result<List<Map<String, Object>>> getTeacherSchedule(@CookieValue("userId") Long userId) {
        User user = userService.getById(userId);
        if (user == null || !"teacher".equals(user.getUserType())) {
            return Result.error("用户不存在或不是教师");
        }
        
        // 获取教师负责的课程安排
        List<ExperimentSchedule> schedules = experimentScheduleService.list(
            new QueryWrapper<ExperimentSchedule>().like("teacher_ids", userId.toString())
        );
        
        List<Map<String, Object>> result = schedules.stream().map(schedule -> {
            Map<String, Object> item = new HashMap<>();
            
            item.put("scheduleId", schedule.getScheduleId());
            item.put("experimentTime", schedule.getExperimentTime());
            
            // 获取小组信息 - 直接从groupIds字符串解析
            if (schedule.getGroupIds() != null && !schedule.getGroupIds().trim().isEmpty()) {
                String groupIdsStr = schedule.getGroupIds().replaceAll("[\\[\\]]", "");
                List<String> groupNames = new ArrayList<>();
                if (!groupIdsStr.isEmpty()) {
                    String[] groupNamesArray = groupIdsStr.split(",");
                    for (String groupName : groupNamesArray) {
                        if (groupName != null && !groupName.trim().isEmpty()) {
                            groupNames.add(groupName.trim());
                        }
                    }
                }
                item.put("groupNames", groupNames);
            }
            
            // 统计该课程的学生数量和成绩录入情况
            // 由于没有schedule_id，暂时设置为0
            item.put("studentCount", 0);
            item.put("recordedCount", 0);

            
            return item;
        }).collect(Collectors.toList());
        
        return Result.success(result);
    }
    
    @GetMapping("/scheduleStudents")
    public Result<List<Map<String, Object>>> getScheduleStudents(
            @CookieValue("userId") Long userId,
            @RequestParam Long scheduleId) {
        
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        
        // 权限检查
        if ("teacher".equals(user.getUserType())) {
            List<ExperimentSchedule> schedules = experimentScheduleService.list(
                new QueryWrapper<ExperimentSchedule>().like("teacher_ids", userId.toString())
            );
            List<Long> scheduleIds = schedules.stream().map(ExperimentSchedule::getScheduleId).collect(Collectors.toList());
            if (!scheduleIds.contains(scheduleId)) {
                return Result.error("您没有权限查看该课程的学生信息");
            }
        }
        
        // 获取课程安排信息
        ExperimentSchedule schedule = experimentScheduleService.getById(scheduleId);
        if (schedule == null) {
            return Result.error("课程安排不存在");
        }
        
        // 获取该课程安排下的小组学生
        List<Map<String, Object>> students = new ArrayList<>();
        
        if (schedule.getGroupIds() != null && !schedule.getGroupIds().trim().isEmpty()) {
            String groupIdsStr = schedule.getGroupIds().replaceAll("[\\[\\]]", "");
            if (!groupIdsStr.isEmpty()) {
                String[] groupNames = groupIdsStr.split(",");
                for (String groupName : groupNames) {
                    if (groupName != null && !groupName.trim().isEmpty()) {
                        groupName = groupName.trim();
                        // 获取该小组的所有学生
                        List<User> groupStudents = userService.list(
                            new QueryWrapper<User>().eq("group_name", groupName)
                        );
                        
                        for (User student : groupStudents) {
                            Map<String, Object> studentInfo = new HashMap<>();
                            studentInfo.put("userId", student.getUserId());
                            studentInfo.put("studentName", student.getRealName());
                            studentInfo.put("schoolId", student.getSchoolId());
                            studentInfo.put("classId", student.getClassId());
                            studentInfo.put("groupName", groupName);
                            
                            // 获取该学生的成绩
                            // 由于没有schedule_id，暂时不查询成绩
                            studentInfo.put("score", null);
                            studentInfo.put("gradeId", null);
                            studentInfo.put("status", "待录入");
                            
                            students.add(studentInfo);
                        }
                    }
                }
            }
        }
        
        return Result.success(students);
    }
    
    @GetMapping("/stats")
    public Result<Map<String, Object>> getGradeStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 统计总成绩数
        long total = gradeService.count();
        stats.put("total", total);
        
        // 计算平均分
        QueryWrapper<ExperimentGrade> wrapper = new QueryWrapper<>();
        wrapper.select("AVG(score) as avg_score");
        Map<String, Object> avgResult = gradeService.getMap(wrapper);
        double avgScore = avgResult != null ? (Double) avgResult.get("avg_score") : 0.0;
        stats.put("averageScore", Math.round(avgScore * 10.0) / 10.0);
        
        // 计算及格率
        QueryWrapper<ExperimentGrade> passWrapper = new QueryWrapper<>();
        passWrapper.ge("score", 60);
        long passCount = gradeService.count(passWrapper);
        double passRate = total > 0 ? (passCount * 100.0 / total) : 0.0;
        stats.put("passRate", Math.round(passRate));
        
        // 计算优秀率
        QueryWrapper<ExperimentGrade> excellentWrapper = new QueryWrapper<>();
        excellentWrapper.ge("score", 90);
        long excellentCount = gradeService.count(excellentWrapper);
        double excellentRate = total > 0 ? (excellentCount * 100.0 / total) : 0.0;
        stats.put("excellentRate", Math.round(excellentRate));
        
        return Result.success(stats);
    }
    
    /**
     * 锁定成绩（管理员功能）
     */
    @PostMapping("/lock/{gradeId}")
    public Result<Boolean> lockGrade(@PathVariable Long gradeId, @CookieValue("userId") Long userId) {
        User user = userService.getById(userId);
        if (user == null || !"admin".equals(user.getUserType())) {
            return Result.error("只有管理员可以锁定成绩");
        }
        
        ExperimentGrade grade = gradeService.getById(gradeId);
        if (grade == null) {
            return Result.error("成绩不存在");
        }
        
        grade.setIsLocked(true);
        
        boolean success = gradeService.updateById(grade);
        if (success) {
            // 自动归档：冻结成功后生成一份Excel（失败不影响主流程）
            try {
                QueryWrapper<ExperimentGrade> q = new QueryWrapper<>();
                q.eq("semester_id", grade.getSemesterId())
                        .eq("suite_id", grade.getSuiteId())
                        .eq("group_name", grade.getGroupName())
                        .eq("experiment_id", grade.getExperimentId());
                if (grade.getTeacherId() != null) {
                    q.eq("teacher_id", grade.getTeacherId());
                }
                q.orderByAsc("user_id");
                List<ExperimentGrade> gradesToArchive = gradeService.list(q);
                archiveGradesAsExtraFile(userId, gradesToArchive, "freeze");
            } catch (Exception ignore) {
            }
            return Result.success(true);
        } else {
            return Result.error("锁定成绩失败");
        }
    }
    
    /**
     * 解锁成绩（管理员功能，需要密码验证）
     */
    @PostMapping("/unlock/{gradeId}")
    public Result<Boolean> unlockGrade(
            @PathVariable Long gradeId, 
            @CookieValue("userId") Long userId,
            @RequestParam String password) {
        
        User user = userService.getById(userId);
        if (user == null || !"admin".equals(user.getUserType())) {
            return Result.error("只有管理员可以解锁成绩");
        }
        
        // 验证管理员密码
        if (password == null || password.trim().isEmpty()) {
            return Result.error("请输入管理员密码");
        }
        
        // 验证密码是否正确
        if (!password.equals(user.getPassword())) {
            return Result.error("管理员密码错误");
        }
        
        ExperimentGrade grade = gradeService.getById(gradeId);
        if (grade == null) {
            return Result.error("成绩不存在");
        }
        
        grade.setIsLocked(false);
        
        boolean success = gradeService.updateById(grade);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("解锁成绩失败");
        }
    }
    
    /**
     * 批量锁定成绩（管理员功能）
     */
    @PostMapping("/batch-lock")
    public Result<Map<String, Object>> batchLockGrades(
            @RequestBody List<Long> gradeIds, 
            @CookieValue("userId") Long userId) {
        
        User user = userService.getById(userId);
        if (user == null || !"admin".equals(user.getUserType())) {
            return Result.error("只有管理员可以批量锁定成绩");
        }
        
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;

        // 一次性预取待锁定成绩，避免逐条查库
        Map<Long, ExperimentGrade> gradeMap = gradeService.listByIds(gradeIds).stream()
                .filter(g -> g != null && g.getGradeId() != null)
                .collect(Collectors.toMap(ExperimentGrade::getGradeId, g -> g, (a, b) -> a));

        for (Long gradeId : gradeIds) {
            try {
                ExperimentGrade grade = gradeMap.get(gradeId);
                if (grade != null) {
                    grade.setIsLocked(true);
                    if (gradeService.updateById(grade)) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                failCount++;
            }
        }
        
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("totalCount", gradeIds.size());

        // 自动归档：将本次批量冻结涉及的成绩导出成一份Excel归档（失败不影响主流程）
        try {
            if (successCount > 0) {
                QueryWrapper<ExperimentGrade> q = new QueryWrapper<>();
                q.in("grade_id", gradeIds);
                q.orderByAsc("group_name").orderByAsc("user_id").orderByAsc("experiment_id");
                List<ExperimentGrade> gradesToArchive = gradeService.list(q);
                ExtraFile archived = archiveGradesAsExtraFile(userId, gradesToArchive, "batch-freeze");
                if (archived != null) {
                    result.put("archiveFileId", archived.getFileId());
                    result.put("archiveFileName", archived.getFileName());
                }
            }
        } catch (Exception ignore) {
        }
        
        return Result.success(result);
    }

    private ExtraFile archiveGradesAsExtraFile(Long uploaderId, List<ExperimentGrade> grades, String action) {
        if (uploaderId == null || grades == null || grades.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = buildGradesXlsxBytes(grades);
            if (bytes == null || bytes.length == 0) {
                return null;
            }

            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String originalName = "grades_" + (action == null ? "archive" : action) + "_" + ts + ".xlsx";
            String filePath = fileUtil.saveExtraBytes(bytes, originalName);

            ExtraFile f = new ExtraFile();
            f.setFileName(originalName);
            f.setFileSize((long) bytes.length);
            f.setFileType("xlsx");
            f.setFilePath(filePath);
            f.setUploadTime(LocalDateTime.now());
            f.setUploaderId(uploaderId);
            f.setCreateTime(LocalDateTime.now());
            extraFileService.save(f);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] buildGradesXlsxBytes(List<ExperimentGrade> grades) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("冻结归档");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("学号");
        header.createCell(1).setCellValue("姓名");
        header.createCell(2).setCellValue("班级");
        header.createCell(3).setCellValue("小组");
        header.createCell(4).setCellValue("实验");
        header.createCell(5).setCellValue("成绩");
        header.createCell(6).setCellValue("备注");
        header.createCell(7).setCellValue("教师");
        header.createCell(8).setCellValue("学期ID");
        header.createCell(9).setCellValue("套件ID");
        header.createCell(10).setCellValue("是否冻结");

        Set<Long> userIds = new HashSet<>();
        for (ExperimentGrade g : grades) {
            if (g.getUserId() != null) userIds.add(g.getUserId());
            if (g.getTeacherId() != null) userIds.add(g.getTeacherId());
        }
        Map<Long, User> userMap = loadUserMapByIds(userIds);
        Map<Long, Experiment> experimentMap = loadExperimentMapByIds(grades.stream()
                .map(ExperimentGrade::getExperimentId).collect(Collectors.toList()));

        int rowIdx = 1;
        for (ExperimentGrade g : grades) {
            Row r = sheet.createRow(rowIdx++);
            User stu = g.getUserId() == null ? null : userMap.get(g.getUserId());
            Experiment exp = g.getExperimentId() == null ? null : experimentMap.get(g.getExperimentId());
            User teacher = g.getTeacherId() == null ? null : userMap.get(g.getTeacherId());

            r.createCell(0).setCellValue(stu != null && stu.getSchoolId() != null ? stu.getSchoolId() : "");
            r.createCell(1).setCellValue(stu != null && stu.getRealName() != null ? stu.getRealName() : "");
            r.createCell(2).setCellValue(stu != null && stu.getClassId() != null ? stu.getClassId() : "");
            r.createCell(3).setCellValue(g.getGroupName() != null ? g.getGroupName() : "");
            r.createCell(4).setCellValue(exp != null && exp.getExperimentName() != null ? exp.getExperimentName() : "");
            r.createCell(5).setCellValue(g.getScore() == null ? "" : String.valueOf(g.getScore()));
            r.createCell(6).setCellValue(g.getRemark() == null ? "" : g.getRemark());
            r.createCell(7).setCellValue(teacher != null && teacher.getRealName() != null ? teacher.getRealName() : "");
            r.createCell(8).setCellValue(g.getSemesterId() == null ? "" : String.valueOf(g.getSemesterId()));
            r.createCell(9).setCellValue(g.getSuiteId() == null ? "" : String.valueOf(g.getSuiteId()));
            r.createCell(10).setCellValue(g.getIsLocked() != null && g.getIsLocked() ? "Y" : "");
        }

        for (int i = 0; i < 11; i++) {
            try { sheet.autoSizeColumn(i); } catch (Exception ignore) {}
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        wb.close();
        return baos.toByteArray();
    }

    @GetMapping("/export-xlsx")
    public ResponseEntity<byte[]> exportGradesXlsx(
            @CookieValue("userId") Long userId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long suiteId,
            @RequestParam(required = false) Integer weekType,
            @RequestParam(required = false) Long teacherId) {

        try {
            User user = userService.getById(userId);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            QueryWrapper<ExperimentGrade> wrapper = new QueryWrapper<>();
            if (semesterId != null) {
                wrapper.eq("semester_id", semesterId);
            }
            if (suiteId != null) {
                wrapper.eq("suite_id", suiteId);
            }

            if (semesterId != null && suiteId != null) {
                QueryWrapper<ExperimentSchedule> scheduleQ = new QueryWrapper<>();
                scheduleQ.eq("semester_id", semesterId);
                scheduleQ.eq("suite_id", suiteId);
                if (weekType != null) {
                    if (weekType == 1) {
                        scheduleQ.eq("week_type", 1);
                    } else {
                        scheduleQ.and(w -> w.isNull("week_type").or().eq("week_type", 0));
                    }
                }

                List<ExperimentSchedule> schedules = experimentScheduleService.list(scheduleQ);
                Set<String> scheduledGroups = new HashSet<>();
                for (ExperimentSchedule s : schedules) {
                    if (s == null) continue;
                    scheduledGroups.addAll(parseScheduleGroupIds(s.getGroupIds()));
                }

                if (!scheduledGroups.isEmpty()) {
                    wrapper.in("group_name", scheduledGroups);
                } else {
                    // 保证仍返回可打开的xlsx：通过设置不可能条件让结果为空
                    wrapper.eq("grade_id", -1);
                }
            }

            String userType = user.getUserType();
            if ("student".equals(userType)) {
                wrapper.eq("user_id", userId);
            } else if ("teacher".equals(userType)) {
                if (teacherId != null && !teacherId.equals(userId)) {
                    return ResponseEntity.status(403).build();
                }
                wrapper.eq("teacher_id", userId);
            } else if ("admin".equals(userType)) {
                if (teacherId != null) {
                    wrapper.eq("teacher_id", teacherId);
                }
            }

            wrapper.orderByAsc("group_name").orderByAsc("user_id").orderByAsc("experiment_id");
            List<ExperimentGrade> grades = gradeService.list(wrapper);

            // 批量取学生/教师/实验信息，避免N+1
            List<Long> studentIds = grades.stream()
                    .map(ExperimentGrade::getUserId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, User> studentMap = new HashMap<>();
            if (!studentIds.isEmpty()) {
                List<User> students = userService.listByIds(studentIds);
                studentMap = students.stream().collect(Collectors.toMap(User::getUserId, u -> u));
            }

            List<Long> teacherIds = grades.stream()
                    .map(ExperimentGrade::getTeacherId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, User> teacherMap = new HashMap<>();
            if (!teacherIds.isEmpty()) {
                List<User> teachers = userService.listByIds(teacherIds);
                teacherMap = teachers.stream().collect(Collectors.toMap(User::getUserId, u -> u));
            }

            List<Long> experimentIds = grades.stream()
                    .map(ExperimentGrade::getExperimentId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, Experiment> experimentMap = new HashMap<>();
            if (!experimentIds.isEmpty()) {
                List<Experiment> experiments = experimentService.listByIds(experimentIds);
                experimentMap = experiments.stream().collect(Collectors.toMap(Experiment::getExperimentId, e -> e));
            }

            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("成绩");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("班级");
            header.createCell(3).setCellValue("小组");
            header.createCell(4).setCellValue("实验");
            header.createCell(5).setCellValue("成绩");
            header.createCell(6).setCellValue("备注");
            header.createCell(7).setCellValue("教师");

            int rowIdx = 1;
            for (ExperimentGrade g : grades) {
                Row r = sheet.createRow(rowIdx++);
                User stu = g.getUserId() == null ? null : studentMap.get(g.getUserId());
                Experiment exp = g.getExperimentId() == null ? null : experimentMap.get(g.getExperimentId());
                User teacher = g.getTeacherId() == null ? null : teacherMap.get(g.getTeacherId());

                r.createCell(0).setCellValue(stu != null && stu.getSchoolId() != null ? stu.getSchoolId() : "");
                r.createCell(1).setCellValue(stu != null && stu.getRealName() != null ? stu.getRealName() : "");
                r.createCell(2).setCellValue(stu != null && stu.getClassId() != null ? stu.getClassId() : "");
                r.createCell(3).setCellValue(g.getGroupName() != null ? g.getGroupName() : (stu != null ? (stu.getGroupName() == null ? "" : stu.getGroupName()) : ""));
                r.createCell(4).setCellValue(exp != null && exp.getExperimentName() != null ? exp.getExperimentName() : "");
                r.createCell(5).setCellValue(g.getScore() == null ? "" : String.valueOf(g.getScore()));
                r.createCell(6).setCellValue(g.getRemark() == null ? "" : g.getRemark());
                r.createCell(7).setCellValue(teacher != null && teacher.getRealName() != null ? teacher.getRealName() : "");
            }

            // 自动列宽（限制前8列）
            for (int i = 0; i < 8; i++) {
                try { sheet.autoSizeColumn(i); } catch (Exception ignore) {}
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            wb.close();

            String fileName = "grades.xlsx";
            if ("student".equals(userType)) {
                fileName = "my-grades.xlsx";
            } else if ("teacher".equals(userType)) {
                fileName = "teacher-grades.xlsx";
            }

            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + encoded)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(baos.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/template-xlsx")
    public ResponseEntity<byte[]> downloadGradeTemplateXlsx(
            @CookieValue("userId") Long currentUserId,
            @RequestParam String groupName,
            @RequestParam Long experimentId,
            @RequestParam Long semesterId,
            @RequestParam Long suiteId,
            @RequestParam Integer weekType) {
        try {
            User current = userService.getById(currentUserId);
            if (current == null) {
                return ResponseEntity.notFound().build();
            }
            boolean isTeacher = "teacher".equalsIgnoreCase(current.getUserType());
            boolean isAdmin = "admin".equalsIgnoreCase(current.getUserType());
            if (!isTeacher && !isAdmin) {
                return ResponseEntity.status(403).build();
            }

            Result<Map<String, Object>> stuRes = getStudentsByGroup(groupName, semesterId, suiteId, weekType);
            if (stuRes == null || stuRes.getCode() == null || !stuRes.getCode().equals(200) || stuRes.getData() == null) {
                return ResponseEntity.internalServerError().build();
            }

            Object stuObj = stuRes.getData().get("students");
            List<Map<String, Object>> students = (stuObj instanceof List) ? (List<Map<String, Object>>) stuObj : new ArrayList<>();

            Map<Long, ExperimentGrade> myGradeMap = new HashMap<>();
            if (experimentId != null && !students.isEmpty()) {
                List<Long> studentIds = new ArrayList<>();
                for (Map<String, Object> s : students) {
                    Object uidObj = s.get("userId");
                    if (uidObj instanceof Number) {
                        studentIds.add(((Number) uidObj).longValue());
                    } else if (uidObj != null) {
                        try {
                            studentIds.add(Long.parseLong(String.valueOf(uidObj)));
                        } catch (Exception ignore) {
                        }
                    }
                }
                if (!studentIds.isEmpty()) {
                    QueryWrapper<ExperimentGrade> q = new QueryWrapper<>();
                    q.eq("experiment_id", experimentId);
                    q.eq("semester_id", semesterId);
                    q.eq("suite_id", suiteId);
                    q.in("user_id", studentIds);
                    if (isTeacher) {
                        q.eq("teacher_id", currentUserId);
                    }
                    q.orderByDesc("update_time");
                    List<ExperimentGrade> grades = gradeService.list(q);
                    for (ExperimentGrade g : grades) {
                        if (g.getUserId() != null && !myGradeMap.containsKey(g.getUserId())) {
                            myGradeMap.put(g.getUserId(), g);
                        }
                    }
                }
            }

            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("模板");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("班级");
            header.createCell(3).setCellValue("成绩");
            header.createCell(4).setCellValue("备注");

            int rowIdx = 1;
            for (Map<String, Object> s : students) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(s.get("schoolId") == null ? "" : String.valueOf(s.get("schoolId")));
                r.createCell(1).setCellValue(s.get("studentName") == null ? "" : String.valueOf(s.get("studentName")));
                r.createCell(2).setCellValue(s.get("classId") == null ? "" : String.valueOf(s.get("classId")));

                Long uid = null;
                Object uidObj = s.get("userId");
                if (uidObj instanceof Number) {
                    uid = ((Number) uidObj).longValue();
                } else if (uidObj != null) {
                    try { uid = Long.parseLong(String.valueOf(uidObj)); } catch (Exception ignore) {}
                }
                ExperimentGrade g = uid == null ? null : myGradeMap.get(uid);
                r.createCell(3).setCellValue(g == null || g.getScore() == null ? "" : String.valueOf(g.getScore()));
                r.createCell(4).setCellValue(g == null || g.getRemark() == null ? "" : g.getRemark());
            }

            for (int i = 0; i < 5; i++) {
                try { sheet.autoSizeColumn(i); } catch (Exception ignore) {}
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            wb.close();

            String fileName = "grade-template.xlsx";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + encoded)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(baos.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/import-xlsx")
    public Result<Map<String, Object>> importGradeTemplateXlsx(
            @CookieValue("userId") Long currentUserId,
            @RequestParam("file") MultipartFile file,
            @RequestParam String groupName,
            @RequestParam Long experimentId,
            @RequestParam Long semesterId,
            @RequestParam Long suiteId,
            @RequestParam Integer weekType) {
        if (file == null || file.isEmpty()) {
            return Result.error("文件为空");
        }

        int successCount = 0;
        int failCount = 0;
        List<String> errorMessages = new ArrayList<>();

        try {
            // 用同一个 students-by-group 口径作为“允许导入的学生集合”
            Result<Map<String, Object>> stuRes = getStudentsByGroup(groupName, semesterId, suiteId, weekType);
            if (stuRes == null || stuRes.getCode() == null || !stuRes.getCode().equals(200) || stuRes.getData() == null) {
                return Result.error("获取学生列表失败");
            }

            Object stuObj = stuRes.getData().get("students");
            List<Map<String, Object>> students = (stuObj instanceof List) ? (List<Map<String, Object>>) stuObj : new ArrayList<>();
            Map<String, Long> schoolIdToUserId = new HashMap<>();
            for (Map<String, Object> s : students) {
                String sid = s.get("schoolId") == null ? null : String.valueOf(s.get("schoolId")).trim();
                if (sid == null || sid.isEmpty()) continue;
                Object uidObj = s.get("userId");
                Long uid = null;
                if (uidObj instanceof Number) uid = ((Number) uidObj).longValue();
                else if (uidObj != null) {
                    try { uid = Long.parseLong(String.valueOf(uidObj)); } catch (Exception ignore) {}
                }
                if (uid != null) {
                    schoolIdToUserId.put(sid, uid);
                }
            }

            List<BatchUpsertItem> items = new ArrayList<>();
            try (InputStream is = file.getInputStream(); Workbook wb = new XSSFWorkbook(is)) {
                Sheet sheet = wb.getSheetAt(0);
                int lastRow = sheet.getLastRowNum();
                for (int i = 1; i <= lastRow; i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    String schoolId = getCellString(row.getCell(0)).trim();
                    if (schoolId.isEmpty()) continue;

                    Long uid = schoolIdToUserId.get(schoolId);
                    if (uid == null) {
                        failCount++;
                        errorMessages.add("第" + (i + 1) + "行 学号不在该小组可录入范围: " + schoolId);
                        continue;
                    }

                    String scoreStr = getCellString(row.getCell(3)).trim();
                    String remark = getCellString(row.getCell(4)).trim();

                    Object scoreObj = null;
                    if (!scoreStr.isEmpty()) {
                        if ("N".equalsIgnoreCase(scoreStr)) {
                            scoreObj = "N";
                        } else {
                            try {
                                scoreObj = Double.parseDouble(scoreStr);
                            } catch (Exception ex) {
                                failCount++;
                                errorMessages.add("第" + (i + 1) + "行 成绩格式错误: " + scoreStr);
                                continue;
                            }
                        }
                    }

                    BatchUpsertItem it = new BatchUpsertItem();
                    it.setUserId(uid);
                    it.setScore(scoreObj);
                    it.setRemark(remark.isEmpty() ? null : remark);
                    items.add(it);
                }
            }

            if (items.isEmpty()) {
                Map<String, Object> res = new HashMap<>();
                res.put("successCount", 0);
                res.put("failCount", failCount);
                res.put("errorMessages", errorMessages);
                return Result.success(res);
            }

            BatchUpsertRequest req = new BatchUpsertRequest();
            req.setExperimentId(experimentId);
            req.setGroupName(groupName);
            req.setSemesterId(semesterId);
            req.setSuiteId(suiteId);
            req.setTeacherId(currentUserId);
            req.setItems(items);

            Result<Boolean> saveRes = batchUpsertGrades(currentUserId, req);
            if (saveRes != null && saveRes.getCode() != null && saveRes.getCode().equals(200) && Boolean.TRUE.equals(saveRes.getData())) {
                successCount = items.size();
            } else {
                failCount += items.size();
                errorMessages.add(saveRes == null ? "保存失败" : saveRes.getMessage());
            }

            Map<String, Object> res = new HashMap<>();
            res.put("successCount", successCount);
            res.put("failCount", failCount);
            res.put("errorMessages", errorMessages);
            return Result.success(res);
        } catch (Exception e) {
            return Result.error("导入失败: " + e.getMessage());
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue() == null ? "" : cell.getStringCellValue();
                case NUMERIC:
                    double v = cell.getNumericCellValue();
                    if (v == (long) v) {
                        return String.valueOf((long) v);
                    }
                    return String.valueOf(v);
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        String s = cell.getStringCellValue();
                        return s == null ? "" : s;
                    } catch (Exception ignore) {
                        try {
                            double fv = cell.getNumericCellValue();
                            if (fv == (long) fv) return String.valueOf((long) fv);
                            return String.valueOf(fv);
                        } catch (Exception ignore2) {
                            return "";
                        }
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 导出成绩（支持按教师、学期等条件导出）
     */
    @GetMapping("/export")
    public Result<Map<String, Object>> exportGrades(
            @CookieValue("userId") Long userId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long suiteId,
            @RequestParam(required = false) Integer weekType,
            @RequestParam(required = false) Long teacherId) {
        
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        
        QueryWrapper<ExperimentGrade> wrapper = new QueryWrapper<>();
        
        // 根据用户类型和参数过滤
        if ("teacher".equals(user.getUserType())) {
            // 教师只能导出自己的成绩
            wrapper.eq("teacher_id", userId);
        } else if (teacherId != null) {
            // 管理员可以按教师导出
            wrapper.eq("teacher_id", teacherId);
        } else if ("student".equals(user.getUserType())) {
            wrapper.eq("user_id", userId);
        }
        
        // 学期过滤
        if (semesterId != null) {
            wrapper.eq("semester_id", semesterId);
        }

        // 套件过滤
        if (suiteId != null) {
            wrapper.eq("suite_id", suiteId);
        }

        if (semesterId != null && suiteId != null) {
            QueryWrapper<ExperimentSchedule> scheduleQ = new QueryWrapper<>();
            scheduleQ.eq("semester_id", semesterId);
            scheduleQ.eq("suite_id", suiteId);
            if (weekType != null) {
                if (weekType == 1) {
                    scheduleQ.eq("week_type", 1);
                } else {
                    scheduleQ.and(w -> w.isNull("week_type").or().eq("week_type", 0));
                }
            }
            List<ExperimentSchedule> schedules = experimentScheduleService.list(scheduleQ);
            Set<String> scheduledGroups = new HashSet<>();
            for (ExperimentSchedule s : schedules) {
                if (s == null) continue;
                scheduledGroups.addAll(parseScheduleGroupIds(s.getGroupIds()));
            }
            if (scheduledGroups.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("data", new ArrayList<>());
                result.put("total", 0);
                return Result.success(result);
            }
            wrapper.in("group_name", scheduledGroups);
        }
        
        List<ExperimentGrade> grades = gradeService.list(wrapper);

        // 批量预取学生/教师/实验，避免逐条查库（N+1）
        Set<Long> userIds = new HashSet<>();
        for (ExperimentGrade g : grades) {
            if (g.getUserId() != null) userIds.add(g.getUserId());
            if (g.getTeacherId() != null) userIds.add(g.getTeacherId());
        }
        Map<Long, User> userMap = loadUserMapByIds(userIds);
        Map<Long, Experiment> experimentMap = loadExperimentMapByIds(grades.stream()
                .map(ExperimentGrade::getExperimentId).collect(Collectors.toList()));

        // 转换为导出格式
        List<Map<String, Object>> exportData = grades.stream().map(grade -> {
            Map<String, Object> item = new HashMap<>();
            
            // 获取学生信息
            User student = grade.getUserId() == null ? null : userMap.get(grade.getUserId());
            if (student != null) {
                item.put("studentName", student.getRealName());
                item.put("schoolId", student.getSchoolId());
                item.put("classId", student.getClassId());
                // 获取小组信息 - 直接从User实体获取groupName
                if (student.getGroupName() != null) {
                    item.put("groupName", student.getGroupName());
                }
            }
            
            // 获取实验信息
            Experiment experiment = grade.getExperimentId() == null ? null : experimentMap.get(grade.getExperimentId());
            if (experiment != null) {
                item.put("experimentName", experiment.getExperimentName());
            }

            // 获取教师信息
            if (grade.getTeacherId() != null) {
                User teacher = userMap.get(grade.getTeacherId());
                if (teacher != null) {
                    item.put("teacherName", teacher.getRealName());
                }
            }
            
            item.put("score", grade.getScore());
            item.put("remark", grade.getRemark());
            item.put("isLocked", grade.getIsLocked());
            item.put("createTime", grade.getCreateTime());
            
            return item;
        }).collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("data", exportData);
        result.put("total", exportData.size());
        
        return Result.success(result);
    }
}
