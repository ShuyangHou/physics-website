package com.physics.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.physics.common.Result;
import com.physics.entity.ExperimentSchedule;
import com.physics.entity.GroupExperiment;
import com.physics.entity.User;
import com.physics.entity.Semester;
import com.physics.entity.Experiment;
import com.physics.service.ExperimentScheduleService;
import com.physics.service.GroupExperimentService;
import com.physics.service.SemesterService;
import com.physics.service.UserService;
import com.physics.service.ExperimentService;
import com.physics.dto.ScheduleDTO;
import com.physics.utils.WeekUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import com.physics.dto.GenerateScheduleRequest;
import com.physics.dto.TeacherAssignmentRequest;


@Slf4j
@RestController
@RequestMapping("/schedule")
public class ExperimentScheduleController {
    
    @Autowired
    private ExperimentScheduleService scheduleService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private SemesterService semesterService;

    
    @Autowired
    private GroupExperimentService groupExperimentService;

    @Autowired
    private ExperimentService experimentService;

    private int extractWeekNumberFromGroupExperimentTime(String experimentTime) {
        if (experimentTime == null) return 0;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("第(\\d+)周").matcher(experimentTime);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception ignore) {
        }
        return 0;
    }

    private int weekdayOffset(String experimentTime) {
        if (experimentTime == null) return -1;
        if (experimentTime.contains("周一")) return 0;
        if (experimentTime.contains("周二")) return 1;
        if (experimentTime.contains("周三")) return 2;
        if (experimentTime.contains("周四")) return 3;
        if (experimentTime.contains("周五")) return 4;
        if (experimentTime.contains("周六")) return 5;
        if (experimentTime.contains("周日") || experimentTime.contains("周天")) return 6;
        return -1;
    }

    private String weekdayLabel(String experimentTime) {
        if (experimentTime == null) return "";
        if (experimentTime.contains("周一")) return "周一";
        if (experimentTime.contains("周二")) return "周二";
        if (experimentTime.contains("周三")) return "周三";
        if (experimentTime.contains("周四")) return "周四";
        if (experimentTime.contains("周五")) return "周五";
        if (experimentTime.contains("周六")) return "周六";
        if (experimentTime.contains("周日")) return "周日";
        if (experimentTime.contains("周天")) return "周日";
        return "";
    }

    private String slotLabel(String experimentTime) {
        if (experimentTime == null) return "";
        if (experimentTime.contains("上午")) return "上午";
        if (experimentTime.contains("下午")) return "下午";
        return "";
    }

    private LocalDate calcDate(LocalDate week1Monday, int weekNumber, int weekdayOffset) {
        if (week1Monday == null || weekNumber <= 0 || weekdayOffset < 0) return null;
        return week1Monday.plusDays((long) (weekNumber - 1) * 7L + weekdayOffset);
    }

    private String buildWeekTypeLabelByWeekNumber(int weekNumber) {
        if (weekNumber <= 0) return "";
        return (weekNumber % 2 == 0) ? "双周" : "单周";
    }

    private List<String> parseCsvOrJsonLikeIds(String raw) {
        List<String> res = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return res;
        try {
            String clean = raw.replaceAll("[\\[\\]\"]", "");
            if (clean.trim().isEmpty()) return res;
            for (String p : clean.split(",")) {
                String v = p == null ? "" : p.trim();
                if (!v.isEmpty()) res.add(v);
            }
        } catch (Exception ignore) {
        }
        return res;
    }

    private Map<Long, String> loadTeacherNameMap(List<ExperimentSchedule> schedules) {
        Set<Long> teacherIds = new HashSet<>();
        if (schedules != null) {
            for (ExperimentSchedule schedule : schedules) {
                teacherIds.addAll(parseTeacherIds(schedule.getTeacherIds()));
            }
        }
        if (teacherIds.isEmpty()) {
            return new HashMap<>();
        }
        return userService.listByIds(teacherIds).stream()
                .filter(user -> user != null && user.getUserId() != null)
                .collect(Collectors.toMap(User::getUserId, User::getRealName, (a, b) -> a));
    }

    private List<String> resolveTeacherNames(String teacherIds, Map<Long, String> teacherNameMap) {
        List<String> teacherNames = new ArrayList<>();
        for (Long teacherId : parseTeacherIds(teacherIds)) {
            String teacherName = teacherNameMap.get(teacherId);
            if (teacherName != null) {
                teacherNames.add(teacherName);
            }
        }
        return teacherNames;
    }

    private String joinList(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join(",", list);
    }

    private String buildWeekTypeLabel(Integer weekType) {
        if (weekType == null) return "";
        return weekType == 1 ? "双周" : "单周";
    }

    private QueryWrapper<ExperimentSchedule> buildExportWrapper(User user, Long semesterId, Long suiteId, Integer weekType) {
        QueryWrapper<ExperimentSchedule> wrapper = new QueryWrapper<>();
        if (semesterId != null) wrapper.eq("semester_id", semesterId);
        if (suiteId != null) wrapper.eq("suite_id", suiteId);
        if (weekType != null) {
            if (weekType == 1) {
                wrapper.eq("week_type", 1);
            } else {
                wrapper.and(w -> w.isNull("week_type").or().eq("week_type", 0));
            }
        }
        if (user != null && "teacher".equalsIgnoreCase(user.getUserType())) {
            wrapper.and(w -> w.like("teacher_ids", user.getUserId().toString()).or().eq("intro_teacher_id", user.getUserId()));
        }
        wrapper.orderByAsc("week_type").orderByAsc("experiment_time");
        return wrapper;
    }

    private void fillSheet(Sheet sheet, List<ExperimentSchedule> schedules) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("周类型");
        header.createCell(1).setCellValue("时间段");
        header.createCell(2).setCellValue("任课老师");
        header.createCell(3).setCellValue("班级");
        header.createCell(4).setCellValue("小组");

        int rowIdx = 1;
        for (ExperimentSchedule s : schedules) {
            Row r = sheet.createRow(rowIdx++);
            r.createCell(0).setCellValue(buildWeekTypeLabel(s.getWeekType()));
            r.createCell(1).setCellValue(s.getExperimentTime() == null ? "" : s.getExperimentTime());

            List<String> teacherNames = new ArrayList<>();
            List<String> teacherIds = parseCsvOrJsonLikeIds(s.getTeacherIds());
            for (String idStr : teacherIds) {
                try {
                    Long tid = Long.parseLong(idStr);
                    User t = userService.getById(tid);
                    if (t != null && t.getRealName() != null) {
                        teacherNames.add(t.getRealName());
                    }
                } catch (Exception ignore) {
                }
            }
            if (s.getIntroTeacherId() != null) {
                try {
                    User intro = userService.getById(s.getIntroTeacherId());
                    if (intro != null && intro.getRealName() != null) {
                        String introName = intro.getRealName() + "(绪论课)";
                        if (!teacherNames.contains(introName)) {
                            teacherNames.add(introName);
                        }
                    }
                } catch (Exception ignore) {
                }
            }

            r.createCell(2).setCellValue(joinList(teacherNames));
            r.createCell(3).setCellValue(s.getClassIds() == null ? "" : s.getClassIds());
            r.createCell(4).setCellValue(joinList(parseCsvOrJsonLikeIds(s.getGroupIds())));
        }

        for (int i = 0; i < 5; i++) {
            try { sheet.autoSizeColumn(i); } catch (Exception ignore) {}
        }
    }

    @GetMapping("/list")
    public Result<Page<ScheduleDTO>> getScheduleList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long suiteId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Integer weekType) {
        
        Page<ExperimentSchedule> page = new Page<>(current, size);
        QueryWrapper<ExperimentSchedule> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        
        if (suiteId != null) {
            wrapper.eq("suite_id", suiteId);
        }
        if (semesterId != null) {
            wrapper.eq("semester_id", semesterId);
        }
        if (weekType != null) {
            wrapper.eq("week_type", weekType);
        }
        
        Page<ExperimentSchedule> result = scheduleService.page(page, wrapper);
        
        Map<Long, String> teacherNameMap = loadTeacherNameMap(result.getRecords());

        // 转换为DTO
        List<ScheduleDTO> dtoList = result.getRecords().stream().map(schedule -> {
            
            ScheduleDTO dto = new ScheduleDTO();
            dto.setScheduleId(schedule.getScheduleId());
            dto.setTeacherIds(schedule.getTeacherIds());
            dto.setGroupIds(schedule.getGroupIds());
            dto.setExperimentTime(schedule.getExperimentTime());
            try { dto.getClass(); } catch (Exception ignored) {}
            // 透传 weekType 到前端（如需）
            try {
                java.lang.reflect.Method m = dto.getClass().getMethod("setWeekType", Integer.class);
                m.invoke(dto, schedule.getWeekType());
            } catch (Exception ignored) {}
            dto.setCreateTime(schedule.getCreateTime());
            dto.setUpdateTime(schedule.getUpdateTime());
            
            dto.setTeacherNames(resolveTeacherNames(schedule.getTeacherIds(), teacherNameMap));
            
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

                dto.setGroupNames(groupNames);
            }
            
            return dto;
        }).collect(Collectors.toList());
        
        // 创建新的Page对象
        Page<ScheduleDTO> dtoPage = new Page<>(current, size);
        dtoPage.setRecords(dtoList);
        dtoPage.setTotal(result.getTotal());
        dtoPage.setCurrent(result.getCurrent());
        dtoPage.setSize(result.getSize());
        
        return Result.success(dtoPage);
    }

    @GetMapping("/student-trace")
    public Result<List<Map<String, Object>>> getStudentTrace(
            @CookieValue("userId") Long currentUserId,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long suiteId,
            @RequestParam(required = false) Integer weekType) {

        User current = userService.getById(currentUserId);
        if (current == null) return Result.error("用户不存在");
        boolean isAdmin = "admin".equalsIgnoreCase(current.getUserType());
        boolean isTeacher = "teacher".equalsIgnoreCase(current.getUserType());
        boolean isStudent = "student".equalsIgnoreCase(current.getUserType());

        if (!isAdmin && !isTeacher && !isStudent) {
            return Result.error("无权操作");
        }

        User target;
        if (isStudent) {
            target = current;
        } else {
            if (schoolId == null || schoolId.trim().isEmpty()) {
                return Result.error("学号不能为空");
            }
            target = userService.getOne(new QueryWrapper<User>()
                    .eq("user_type", "student")
                    .eq("school_id", schoolId.trim())
                    .last("LIMIT 1"));
        }

        if (target == null) return Result.error("未找到该学生");
        if (target.getClassId() == null && target.getGroupName() == null) {
            return Result.success(new ArrayList<>());
        }

        QueryWrapper<ExperimentSchedule> wrapper = new QueryWrapper<>();
        if (semesterId != null) wrapper.eq("semester_id", semesterId);
        if (suiteId != null) wrapper.eq("suite_id", suiteId);
        if (weekType != null) {
            if (weekType == 1) {
                wrapper.eq("week_type", 1);
            } else {
                wrapper.and(w -> w.isNull("week_type").or().eq("week_type", 0));
            }
        }
        wrapper.orderByAsc("semester_id").orderByAsc("week_type").orderByAsc("experiment_time");
        List<ExperimentSchedule> schedules = scheduleService.list(wrapper);

        List<Map<String, Object>> res = new ArrayList<>();
        for (ExperimentSchedule s : schedules) {
            List<String> groupIds = parseCsvOrJsonLikeIds(s.getGroupIds());
            List<String> classIds = parseCsvOrJsonLikeIds(s.getClassIds());
            boolean match = false;
            if (target.getGroupName() != null && groupIds.contains(target.getGroupName())) {
                match = true;
            }
            if (!match && target.getClassId() != null && classIds.contains(target.getClassId())) {
                match = true;
            }
            if (!match) continue;

            Map<String, Object> row = new HashMap<>();
            row.put("userId", target.getUserId());
            row.put("schoolId", target.getSchoolId());
            row.put("realName", target.getRealName());
            row.put("classId", target.getClassId());
            row.put("groupName", target.getGroupName());
            row.put("semesterId", s.getSemesterId());
            row.put("suiteId", s.getSuiteId());
            row.put("weekType", s.getWeekType());
            row.put("weekTypeLabel", buildWeekTypeLabel(s.getWeekType()));
            row.put("experimentTime", s.getExperimentTime());

            Semester sem = null;
            try { sem = s.getSemesterId() == null ? null : semesterService.getById(s.getSemesterId()); } catch (Exception ignore) {}
            row.put("semesterName", sem == null ? "" : sem.getSemesterName());

            List<String> teacherNames = new ArrayList<>();
            List<String> teacherIds = parseCsvOrJsonLikeIds(s.getTeacherIds());
            for (String idStr : teacherIds) {
                try {
                    Long tid = Long.parseLong(idStr);
                    User t = userService.getById(tid);
                    if (t != null && t.getRealName() != null) {
                        teacherNames.add(t.getRealName());
                    }
                } catch (Exception ignore) {
                }
            }
            if (s.getIntroTeacherId() != null) {
                try {
                    User intro = userService.getById(s.getIntroTeacherId());
                    if (intro != null && intro.getRealName() != null) {
                        String introName = intro.getRealName() + "(绪论课)";
                        if (!teacherNames.contains(introName)) {
                            teacherNames.add(introName);
                        }
                    }
                } catch (Exception ignore) {
                }
            }
            row.put("teacherNames", teacherNames);
            row.put("teachers", joinList(teacherNames));
            row.put("scheduleId", s.getScheduleId());

            res.add(row);
        }

        return Result.success(res);
    }
    
    @GetMapping("/detail")
    public Result<ExperimentSchedule> getScheduleDetail(@RequestParam Long scheduleId) {
        ExperimentSchedule schedule = scheduleService.getById(scheduleId);
        return Result.success(schedule);
    }
    
    @PostMapping("/add")
    public Result<Boolean> addSchedule(@RequestBody ExperimentSchedule schedule) {
        boolean result = scheduleService.save(schedule);
        return Result.success(result);
    }
    
    @PostMapping("/update")
    public Result<Boolean> updateSchedule(@RequestBody ExperimentSchedule schedule) {
        boolean result = scheduleService.updateById(schedule);
        return Result.success(result);
    }
    
    @PostMapping("/delete")
    public Result<Boolean> deleteSchedule(@RequestParam Long scheduleId) {
        boolean result = scheduleService.removeById(scheduleId);
        return Result.success(result);
    }
    
    @GetMapping("/teacherSchedule")
    public Result<Page<ScheduleDTO>> getTeacherSchedule(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam Long teacherId) {
        
        Page<ExperimentSchedule> page = new Page<>(current, size);
        QueryWrapper<ExperimentSchedule> wrapper = new QueryWrapper<>();
        wrapper.like("teacher_ids", teacherId.toString());
        wrapper.orderByDesc("create_time");
        
        Page<ExperimentSchedule> result = scheduleService.page(page, wrapper);
        
        // 转换为DTO
        List<ScheduleDTO> dtoList = result.getRecords().stream().map(schedule -> {
            ScheduleDTO dto = new ScheduleDTO();
            dto.setScheduleId(schedule.getScheduleId());
            dto.setTeacherIds(schedule.getTeacherIds());
            dto.setGroupIds(schedule.getGroupIds());
            dto.setExperimentTime(schedule.getExperimentTime());
            dto.setWeekType(schedule.getWeekType());
            dto.setSuiteId(schedule.getSuiteId());
            // 设置是否为绪论课：如果有绪论课教师ID，则为绪论课
            dto.setIsIntroCourse(schedule.getIntroTeacherId() != null ? 1 : 0);
            dto.setCreateTime(schedule.getCreateTime());
            dto.setUpdateTime(schedule.getUpdateTime());
            
            // 获取教师信息
            if (schedule.getTeacherIds() != null && !schedule.getTeacherIds().trim().isEmpty()) {
                String teacherIdsStr = schedule.getTeacherIds().replaceAll("[\\[\\]]", "");
                List<String> teacherNames = new ArrayList<>();
                if (!teacherIdsStr.isEmpty()) {
                    String[] ids = teacherIdsStr.split(",");
                    for (String idStr : ids) {
                        try {
                            Long id = Long.parseLong(idStr.trim());
                            User teacher = userService.getById(id);
                            if (teacher != null) {
                                teacherNames.add(teacher.getRealName());
                            }
                        } catch (NumberFormatException e) {
                            // 忽略无效的ID
                        }
                    }
                }
                dto.setTeacherNames(teacherNames);
            }
            
            // 获取小组信息
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
                dto.setGroupNames(groupNames);
            }
            
            return dto;
        }).collect(Collectors.toList());
        
        // 创建新的分页对象
        Page<ScheduleDTO> dtoPage = new Page<>(current, size);
        dtoPage.setRecords(dtoList);
        dtoPage.setTotal(result.getTotal());
        dtoPage.setPages(result.getPages());
        
        return Result.success(dtoPage);
    }
    
    @GetMapping("/studentSchedule")
    public Result<Page<ScheduleDTO>> getStudentSchedule(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        
        Page<ExperimentSchedule> page = new Page<>(current, size);
        QueryWrapper<ExperimentSchedule> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        
        Page<ExperimentSchedule> result = scheduleService.page(page, wrapper);
        
        // 转换为DTO
        List<ScheduleDTO> dtoList = result.getRecords().stream().map(schedule -> {
            ScheduleDTO dto = new ScheduleDTO();
            dto.setScheduleId(schedule.getScheduleId());
            dto.setTeacherIds(schedule.getTeacherIds());
            dto.setGroupIds(schedule.getGroupIds());
            dto.setExperimentTime(schedule.getExperimentTime());
            dto.setWeekType(schedule.getWeekType());
            dto.setSuiteId(schedule.getSuiteId());
            // 学生课表不显示绪论课信息，始终设为0
            dto.setIsIntroCourse(0);
            dto.setCreateTime(schedule.getCreateTime());
            dto.setUpdateTime(schedule.getUpdateTime());
            
            // 获取教师信息
            if (schedule.getTeacherIds() != null && !schedule.getTeacherIds().trim().isEmpty()) {
                String teacherIdsStr = schedule.getTeacherIds().replaceAll("[\\[\\]]", "");
                List<String> teacherNames = new ArrayList<>();
                if (!teacherIdsStr.isEmpty()) {
                    String[] ids = teacherIdsStr.split(",");
                    for (String idStr : ids) {
                        try {
                            Long id = Long.parseLong(idStr.trim());
                            User teacher = userService.getById(id);
                            if (teacher != null) {
                                teacherNames.add(teacher.getRealName());
                            }
                        } catch (NumberFormatException e) {
                            // 忽略无效的ID
                        }
                    }
                }
                dto.setTeacherNames(teacherNames);
            }
            
            // 获取小组信息
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
                dto.setGroupNames(groupNames);
            }
            
            return dto;
        }).collect(Collectors.toList());
        
        // 创建新的分页对象
        Page<ScheduleDTO> dtoPage = new Page<>(current, size);
        dtoPage.setRecords(dtoList);
        dtoPage.setTotal(result.getTotal());
        dtoPage.setPages(result.getPages());
        
        return Result.success(dtoPage);
    }
    
    /**
     * 获取课表显示数据 - 返回所有实验套的数据，用于课表显示
     */
    @GetMapping("/scheduleForDisplay")
    public Result<List<ScheduleDTO>> getScheduleForDisplay(
            @RequestParam(required = false) Long semesterId) {
        
        QueryWrapper<ExperimentSchedule> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("week_type", "experiment_time"); // 按周次类型和时间排序
        
        if (semesterId != null) {
            wrapper.eq("semester_id", semesterId);
        }
        
        // 不限制实验套，返回所有数据
        List<ExperimentSchedule> schedules = scheduleService.list(wrapper);
        
        Map<Long, String> teacherNameMap = loadTeacherNameMap(schedules);

        // 转换为DTO
        List<ScheduleDTO> dtoList = schedules.stream().map(schedule -> {
            ScheduleDTO dto = new ScheduleDTO();
            dto.setScheduleId(schedule.getScheduleId());
            dto.setTeacherIds(schedule.getTeacherIds());
            dto.setGroupIds(schedule.getGroupIds());
            dto.setExperimentTime(schedule.getExperimentTime());
            dto.setWeekType(schedule.getWeekType());
            dto.setSuiteId(schedule.getSuiteId()); // 添加实验套ID
            // 设置是否为绪论课：如果有绪论课教师ID，则为绪论课
            dto.setIsIntroCourse(schedule.getIntroTeacherId() != null ? 1 : 0);
            dto.setIntroTeacherId(schedule.getIntroTeacherId());
            dto.setCreateTime(schedule.getCreateTime());
            dto.setUpdateTime(schedule.getUpdateTime());
            
            dto.setTeacherNames(resolveTeacherNames(schedule.getTeacherIds(), teacherNameMap));
            
            // 获取小组信息
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
                dto.setGroupNames(groupNames);
            }
            
            return dto;
        }).collect(Collectors.toList());
        
        return Result.success(dtoList);
    }

    @GetMapping("/export-xlsx")
    public ResponseEntity<byte[]> exportScheduleXlsx(
            @CookieValue("userId") Long userId,
            @RequestParam Long semesterId,
            @RequestParam(required = false) Long suiteId,
            @RequestParam(required = false) Integer weekType) {
        try {
            User user = userService.getById(userId);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            if (!"admin".equalsIgnoreCase(user.getUserType()) && !"teacher".equalsIgnoreCase(user.getUserType())) {
                return ResponseEntity.status(403).build();
            }

            Semester sem = semesterService.getById(semesterId);
            if (sem == null || sem.getStartDate() == null) {
                return ResponseEntity.internalServerError().build();
            }
            LocalDate week1Monday = WeekUtils.getWeek1Monday(sem.getStartDate());
            if (week1Monday == null) {
                return ResponseEntity.internalServerError().build();
            }

            QueryWrapper<GroupExperiment> q = new QueryWrapper<>();
            q.eq("semester_id", semesterId);
            if (suiteId != null) q.eq("suite_id", suiteId);
            if (user != null && "teacher".equalsIgnoreCase(user.getUserType())) {
                q.eq("teacher_id", user.getUserId());
            }
            q.orderByAsc("experiment_time").orderByAsc("group_name").orderByAsc("experiment_id");
            List<GroupExperiment> rows = groupExperimentService.list(q);

            // weekType 过滤：0=单周(奇数周), 1=双周(偶数周)
            if (weekType != null && (weekType == 0 || weekType == 1)) {
                int desiredParity = (weekType == 1) ? 0 : 1;
                rows = rows.stream()
                        .filter(r -> {
                            int w = extractWeekNumberFromGroupExperimentTime(r == null ? null : r.getExperimentTime());
                            return w > 0 && (w % 2) == desiredParity;
                        })
                        .collect(Collectors.toList());
            }

            // 批量取 teacher/experiment/class
            Set<Long> teacherIds = new HashSet<>();
            Set<Long> experimentIds = new HashSet<>();
            Set<String> groupNames = new HashSet<>();
            for (GroupExperiment r : rows) {
                if (r == null) continue;
                if (r.getTeacherId() != null) teacherIds.add(r.getTeacherId());
                if (r.getExperimentId() != null) experimentIds.add(r.getExperimentId());
                if (r.getGroupName() != null && !r.getGroupName().trim().isEmpty()) groupNames.add(r.getGroupName().trim());
            }
            Map<Long, User> teacherMap = new HashMap<>();
            if (!teacherIds.isEmpty()) {
                List<User> teachers = userService.listByIds(teacherIds);
                teacherMap = teachers.stream().collect(Collectors.toMap(User::getUserId, t -> t));
            }
            Map<Long, Experiment> experimentMap = new HashMap<>();
            if (!experimentIds.isEmpty()) {
                List<Experiment> exps = experimentService.listByIds(experimentIds);
                experimentMap = exps.stream().collect(Collectors.toMap(Experiment::getExperimentId, e -> e));
            }

            Map<String, String> groupToClassIds = new HashMap<>();
            if (!groupNames.isEmpty()) {
                List<User> students = userService.list(new QueryWrapper<User>()
                        .eq("user_type", "student")
                        .in("group_name", groupNames));
                Map<String, Set<String>> clsSet = new HashMap<>();
                for (User stu : students) {
                    if (stu == null || stu.getGroupName() == null) continue;
                    String g = stu.getGroupName().trim();
                    if (g.isEmpty()) continue;
                    if (stu.getClassId() == null || stu.getClassId().trim().isEmpty()) continue;
                    clsSet.computeIfAbsent(g, k -> new HashSet<>()).add(stu.getClassId().trim());
                }
                for (Map.Entry<String, Set<String>> e : clsSet.entrySet()) {
                    List<String> sorted = new ArrayList<>(e.getValue());
                    sorted.sort(String::compareTo);
                    groupToClassIds.put(e.getKey(), String.join(",", sorted));
                }
            }

            // 按日期排序
            rows.sort(Comparator.comparing((GroupExperiment r) -> {
                        int week = extractWeekNumberFromGroupExperimentTime(r == null ? null : r.getExperimentTime());
                        int off = weekdayOffset(r == null ? null : r.getExperimentTime());
                        LocalDate d = calcDate(week1Monday, week, off);
                        return d == null ? LocalDate.of(2999, 1, 1) : d;
                    })
                    .thenComparing(r -> {
                        String s = slotLabel(r == null ? null : r.getExperimentTime());
                        return "上午".equals(s) ? 0 : 1;
                    })
                    .thenComparing(r -> r == null ? "" : (r.getGroupName() == null ? "" : r.getGroupName())));

            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("明细");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("日期");
            header.createCell(1).setCellValue("周次");
            header.createCell(2).setCellValue("周类型");
            header.createCell(3).setCellValue("星期");
            header.createCell(4).setCellValue("节次");
            header.createCell(5).setCellValue("地点");
            header.createCell(6).setCellValue("实验");
            header.createCell(7).setCellValue("任课老师");
            header.createCell(8).setCellValue("班级");
            header.createCell(9).setCellValue("小组");
            header.createCell(10).setCellValue("绪论课");

            int rowIdx = 1;
            for (GroupExperiment r : rows) {
                if (r == null) continue;
                int week = extractWeekNumberFromGroupExperimentTime(r.getExperimentTime());
                int off = weekdayOffset(r.getExperimentTime());
                LocalDate d = calcDate(week1Monday, week, off);

                Experiment exp = r.getExperimentId() == null ? null : experimentMap.get(r.getExperimentId());
                User teacher = r.getTeacherId() == null ? null : teacherMap.get(r.getTeacherId());
                String gName = r.getGroupName() == null ? "" : r.getGroupName();
                String classIds = groupToClassIds.getOrDefault(gName == null ? "" : gName.trim(), "");

                Row rr = sheet.createRow(rowIdx++);
                rr.createCell(0).setCellValue(d == null ? "" : String.valueOf(d));
                rr.createCell(1).setCellValue(week <= 0 ? "" : String.valueOf(week));
                rr.createCell(2).setCellValue(buildWeekTypeLabelByWeekNumber(week));
                rr.createCell(3).setCellValue(weekdayLabel(r.getExperimentTime()));
                rr.createCell(4).setCellValue(slotLabel(r.getExperimentTime()));
                rr.createCell(5).setCellValue(r.getLocation() == null ? "" : r.getLocation());
                rr.createCell(6).setCellValue(exp != null && exp.getExperimentName() != null ? exp.getExperimentName() : "");
                rr.createCell(7).setCellValue(teacher != null && teacher.getRealName() != null ? teacher.getRealName() : "");
                rr.createCell(8).setCellValue(classIds);
                rr.createCell(9).setCellValue(gName);
                rr.createCell(10).setCellValue(r.getIsIntroCourse() != null && r.getIsIntroCourse() == 1 ? "Y" : "");
            }

            for (int i = 0; i <= 10; i++) {
                try { sheet.autoSizeColumn(i); } catch (Exception ignore) {}
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            wb.close();

            String fileName = "schedule.xlsx";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + encoded)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(baos.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/updateTeachersByTime")
    public Result<Boolean> updateTeachersByTime(@RequestParam String experimentTime,
                                                @RequestParam String teacherIds,
                                                @RequestParam Long semesterId,
                                                @RequestParam(required = false) Long suiteId,
                                                @RequestParam Integer weekType,
                                                @RequestParam(required = false) Long introTeacherId) {
        try {
            if (experimentTime == null || experimentTime.trim().isEmpty()) {
                return Result.error("experimentTime 不能为空");
            }
            if (semesterId == null) {
                return Result.error("semesterId 不能为空");
            }

            // 规范化 experimentTime：若未带 单/双 前缀，则按 weekType 自动补齐
            String normalizedTime = experimentTime == null ? "" : experimentTime.trim();
            if (!normalizedTime.startsWith("单") && !normalizedTime.startsWith("双")) {
                String prefix = (weekType != null && weekType == 1) ? "双" : "单";
                normalizedTime = prefix + normalizedTime;
            }
            if (teacherIds == null || teacherIds.trim().isEmpty()) {
                return Result.error("teacherIds 不能为空");
            }

            // 1. 保存/更新 schedule 该时段的教师
            // 修复可能的 NullPointerException：避免对可能为 null 的 suiteId 调用 intValue()
            Integer suiteIdInt = suiteId != null ? suiteId.intValue() : null;
            ExperimentSchedule schedule = scheduleService.getByExperimentTimeAndSemesterAndWeekType(normalizedTime, semesterId, weekType, suiteIdInt);
            
            if (schedule == null) {
                schedule = new ExperimentSchedule();
                schedule.setExperimentTime(normalizedTime);
                schedule.setSemesterId(semesterId);
                schedule.setTeacherIds(teacherIds);
                schedule.setWeekType(weekType);
                schedule.setSuiteId(suiteId);
                schedule.setIntroTeacherId(introTeacherId); // 设置绪论课教师ID
                if (!scheduleService.save(schedule)) {
                    return Result.error("创建实验安排失败");
                }
            } else {
                schedule.setTeacherIds(teacherIds);
                if (suiteId != null) {
                    schedule.setSuiteId(suiteId);
                }
                schedule.setIntroTeacherId(introTeacherId); // 设置绪论课教师ID
                if (!scheduleService.updateById(schedule)) {
                    return Result.error("更新实验安排失败");
                }
            }

            // 2. 找到该时段下涉及的小组名称（解析 schedule.groupIds）
            List<String> targetGroupNames = new ArrayList<>();
            if (schedule.getGroupIds() != null && !schedule.getGroupIds().trim().isEmpty()) {
                String groupIdsStr = schedule.getGroupIds().replaceAll("[\\[\\]]", "");
                if (!groupIdsStr.isEmpty()) {
                    for (String g : groupIdsStr.split(",")) {
                        if (g != null && !g.trim().isEmpty()) {
                            targetGroupNames.add(g.trim());
                        }
                    }
                }
            }

            if (targetGroupNames.isEmpty()) {
                log.warn("时段 {} 未配置小组，跳过 group_experiment 教师更新", experimentTime);
                return Result.success(true);
            }

            // 3. 查询该学期/套件下、这些小组的所有 group_experiment 记录
            QueryWrapper<GroupExperiment> geQ = new QueryWrapper<>();
            geQ.in("group_name", targetGroupNames);
            geQ.eq(semesterId != null, "semester_id", semesterId);
            geQ.eq(suiteId != null, "suite_id", suiteId);
            List<GroupExperiment> groupExperiments = groupExperimentService.list(geQ);
            
            if (groupExperiments.isEmpty()) {
                log.warn("未找到 group_experiment 记录: groups={}, semesterId={}, suiteId={}", targetGroupNames, semesterId, suiteId);
                return Result.success(true);
            }

            // 4. 解析教师ID列表
            List<Long> selectedTeacherIds = parseTeacherIds(teacherIds);
            if (selectedTeacherIds.isEmpty()) {
                return Result.error("teacherIds 非法");
            }

            // 5. 按实验ID分派教师：1-2→teacher1, 3-4→teacher2, ...
            for (GroupExperiment ge : groupExperiments) {
                if (ge.getExperimentId() == null) {
                    continue;
                }
                try {
                    int idx = (ge.getExperimentId().intValue() - 1) / 2;
                    if (idx >= selectedTeacherIds.size()) {
                        idx = selectedTeacherIds.size() - 1;
                    }
                    Long tid = selectedTeacherIds.get(idx);
                    User t = userService.getById(tid);
                    if (t != null) {
                        ge.setTeacherId(t.getUserId());
                        ge.setTeacherName(t.getRealName()); // 同时更新教师姓名
                    }
                } catch (Exception e) {
                    log.error("处理实验ID: {} 时出错", ge.getExperimentId(), e);
                }
            }

            // 6. 批量更新
            boolean ok = groupExperimentService.updateBatchById(groupExperiments);
            if (ok) {
                log.info("成功更新时间段 {} 的教师信息，影响 {} 条记录", experimentTime, groupExperiments.size());
            } else {
                log.error("批量更新时间段 {} 的教师信息失败", experimentTime);
            }
            
            return ok ? Result.success(true) : Result.error("批量更新失败");
        } catch (Exception e) {
            log.error("更新教师失败", e);
            return Result.error("更新教师失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析教师ID字符串，支持 [1,2,3] 和 "1,2,3" 格式
     */
    private List<Long> parseTeacherIds(String teacherIds) {
        List<Long> result = new ArrayList<>();
        if (teacherIds == null || teacherIds.trim().isEmpty()) {
            return result;
        }
        
        try {
            // 移除方括号，按逗号分割
            String cleanIds = teacherIds.replaceAll("[\\[\\]]", "");
            String[] idArray = cleanIds.split(",");
            
            for (String idStr : idArray) {
                if (idStr != null && !idStr.trim().isEmpty()) {
                    try {
                        Long id = Long.parseLong(idStr.trim());
                        result.add(id);
                    } catch (NumberFormatException e) {
                        log.warn("无效的教师ID: {}", idStr);
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析教师ID失败: {}", teacherIds, e);
        }
        
        return result;
    }

    @PostMapping("/updateGroupsByTime")
    public Result<Boolean> updateGroupsByTime(@RequestParam String experimentTime,
                                              @RequestParam String groupNames,
                                              @RequestParam Long semesterId,
                                              @RequestParam(required = false) Long suiteId,
                                              @RequestParam Integer weekType) {
        try {
            log.info("updateGroupsByTime 调用 - experimentTime: {}, groupNames: {}, semesterId: {}, suiteId: {}, weekType: {}", 
                    experimentTime, groupNames, semesterId, suiteId, weekType);
            
            if (experimentTime == null || experimentTime.trim().isEmpty()) {
                return Result.error("experimentTime 不能为空");
            }
            if (semesterId == null) {
                return Result.error("semesterId 不能为空");
            }

            // 规范化 experimentTime：若未带 单/双 前缀，则按 weekType 自动补齐
            String normalizedTime = experimentTime == null ? "" : experimentTime.trim();
            if (!normalizedTime.startsWith("单") && !normalizedTime.startsWith("双")) {
                String prefix = (weekType != null && weekType == 1) ? "双" : "单";
                normalizedTime = prefix + normalizedTime;
            }

            // 1. 查找或创建该时段的 schedule
            Integer suiteIdInt = suiteId != null ? suiteId.intValue() : null;
            ExperimentSchedule schedule = scheduleService.getByExperimentTimeAndSemesterAndWeekType(normalizedTime, semesterId, weekType, suiteIdInt);
            log.info("查找现有记录结果 - schedule: {}", schedule != null ? "找到" : "未找到");
            
            if (schedule == null) {
                // 创建新记录
                log.info("创建新记录 - experimentTime: {}, weekType: {}", normalizedTime, weekType);
                schedule = new ExperimentSchedule();
                schedule.setExperimentTime(normalizedTime);
                schedule.setSemesterId(semesterId);
                schedule.setSuiteId(suiteId);
                if (weekType != null) schedule.setWeekType(weekType);
            } else {
                // 更新现有记录
                log.info("更新现有记录 - scheduleId: {}, 原weekType: {}", schedule.getScheduleId(), schedule.getWeekType());
                schedule.setSuiteId(suiteId);
            }

            // 2. 解析原有 groupIds，合并新分组，去重
            java.util.Set<String> set = new java.util.LinkedHashSet<>();
            if (schedule.getGroupIds() != null && !schedule.getGroupIds().trim().isEmpty()) {
                String raw = schedule.getGroupIds().replaceAll("[\\[\\]]", "");
                if (!raw.isEmpty()) {
                    for (String s : raw.split(",")) {
                        if (s != null && !s.trim().isEmpty()) set.add(s.trim());
                    }
                }
            }
            if (groupNames != null && !groupNames.trim().isEmpty()) {
                for (String s : groupNames.split(",")) {
                    if (s != null && !s.trim().isEmpty()) set.add(s.trim());
                }
            }
            // 存回字符串（保持与现有实现一致，例如 "[A,B]"）
            String stored = "[" + String.join(",", set) + "]";
            schedule.setGroupIds(stored);
            
            log.info("最终保存数据 - scheduleId: {}, experimentTime: {}, groupIds: {}, weekType: {}", 
                    schedule.getScheduleId(), schedule.getExperimentTime(), schedule.getGroupIds(), schedule.getWeekType());

            boolean ok = (schedule.getScheduleId() == null) ? scheduleService.save(schedule) : scheduleService.updateById(schedule);
            log.info("保存结果 - 成功: {}", ok);

            if (ok) {
                try {
                    // 将这些小组绑定到该时间段，回填 group_experiment.time_slot
                    if (suiteId != null && weekType != null && !set.isEmpty()) {
                        List<String> groups = new ArrayList<>(set);
                        groupExperimentService.bindGroupsToTimeSlot(semesterId, suiteId, weekType, normalizedTime, groups);
                    }
                } catch (Exception e2) {
                    log.warn("绑定小组到time_slot失败（不影响主流程）: {}", e2.getMessage());
                }
                return Result.success(true);
            }
            return Result.error("更新小组失败");
        } catch (Exception e) {
            log.error("更新分组失败", e);
            return Result.error("更新分组失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据分组信息自动生成课表
     */
    @PostMapping("/generateScheduleFromGroups")
    public Result<Boolean> generateScheduleFromGroups(@RequestBody GenerateScheduleRequest request) {
        try {
            if (request.getSemesterId() == null) {
                return Result.error("semesterId 不能为空");
            }
            if (request.getGroups() == null || request.getGroups().isEmpty()) {
                return Result.error("groups 不能为空");
            }
            
            boolean success = scheduleService.generateScheduleFromGroups(
                request.getSemesterId(),
                request.getSuiteId(),
                request.getGroups(),
                request.getWeekType()
            );
            
            if (success) {
                return Result.success(true);
            } else {
                return Result.error("课表生成失败");
            }
        } catch (Exception e) {
            log.error("生成课表失败", e);
            return Result.error("生成课表失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存教师分配
     */
    @PostMapping("/saveTeacherAssignment")
    public Result<Boolean> saveTeacherAssignment(@RequestBody TeacherAssignmentRequest request) {
        try {
            if (request.getSemesterId() == null) {
                return Result.error("semesterId 不能为空");
            }
            if (request.getSuiteId() == null) {
                return Result.error("suiteId 不能为空");
            }
            if (request.getTimeSlot() == null || request.getTimeSlot().trim().isEmpty()) {
                return Result.error("timeSlot 不能为空");
            }
            if (request.getAssignments() == null || request.getAssignments().isEmpty()) {
                return Result.error("assignments 不能为空");
            }
            
            boolean success = scheduleService.saveTeacherAssignment(request);
            
            if (success) {
                return Result.success(true);
            } else {
                return Result.error("教师分配保存失败");
            }
        } catch (Exception e) {
            log.error("保存教师分配失败", e);
            return Result.error("保存教师分配失败: " + e.getMessage());
        }
    }
    
}
