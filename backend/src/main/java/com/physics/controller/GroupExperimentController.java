package com.physics.controller;

import com.physics.common.Result;
import com.physics.entity.GroupExperiment;
import com.physics.entity.User;
import com.physics.entity.Experiment;
import com.physics.entity.ExperimentSuite;
import com.physics.entity.ExperimentSchedule;
import com.physics.service.GroupExperimentService;
import com.physics.service.UserService;
import com.physics.service.ExperimentService;
import com.physics.service.ExperimentSuiteService;
import com.physics.service.ExperimentScheduleService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;

@Slf4j
@RestController
@RequestMapping("/group-experiment")
public class GroupExperimentController {
    
    @Autowired
    private GroupExperimentService groupExperimentService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ExperimentService experimentService;
    
    @Autowired
    private ExperimentSuiteService experimentSuiteService;
    
    @Autowired
    private ExperimentScheduleService experimentScheduleService;
    
    /**
     * 根据小组名称获取实验安排，如果没数据就返回空
     */
    @GetMapping("/by-group")
    public Result<List<GroupExperiment>> getByGroupName(@RequestParam String groupName) {
        try {
            if (groupName == null || groupName.trim().isEmpty()) {
                return Result.success(new java.util.ArrayList<>());
            }
            List<GroupExperiment> list = groupExperimentService.getByGroupName(groupName);
            return Result.success(list != null ? list : new java.util.ArrayList<>());
        } catch (Exception e) {
            log.error("根据小组名称获取实验安排失败, groupName={}", groupName, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 批量（逐周明细）设置：一个小组 + 多个周次，每周可不同实验/教师
     */
    @PostMapping("/batch-assign-items")
    public Result<Boolean> batchAssignItems(@RequestBody BatchAssignItemsRequest req) {
        try {
            if (req.getSemesterId() == null || req.getSuiteId() == null
                    || req.getGroupName() == null || req.getGroupName().trim().isEmpty()
                    || req.getTimeSlot() == null || req.getTimeSlot().trim().isEmpty()
                    || req.getItems() == null || req.getItems().isEmpty()) {
                return Result.error("参数不完整");
            }
            log.info("batch-assign-items 入参: semesterId={}, suiteId={}, groupName={}, items={}条",
                    req.getSemesterId(), req.getSuiteId(), req.getGroupName(), req.getItems().size());
            boolean ok = groupExperimentService.batchAssignItems(req.getSemesterId(), req.getSuiteId(), req.getGroupName(), req.getTimeSlot(), req.getItems());
            return ok ? Result.success(true) : Result.error("批量更新失败");
        } catch (Exception e) {
            log.error("batch-assign-items 处理异常", e);
            return Result.error("批量更新异常: " + e.getMessage());
        }
    }

    /**
     * 获取指定学期/套件下全部小组实验安排（用于工作量统计等）
     * 优化版本：批量查询避免N+1问题
     */
    @GetMapping("/all")
    public Result<List<GroupExperiment>> getAll(@RequestParam(required = false) Long semesterId,
                                                @RequestParam(required = false) Long suiteId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GroupExperiment> q = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (semesterId != null) q.eq("semester_id", semesterId);
        if (suiteId != null) q.eq("suite_id", suiteId);
        List<GroupExperiment> list = groupExperimentService.list(q);
        
        // 批量查询教师信息，避免N+1查询
        List<Long> teacherIds = list.stream()
            .map(GroupExperiment::getTeacherId)
            .filter(id -> id != null)
            .distinct()
            .collect(java.util.stream.Collectors.toList());
        
        Map<Long, String> teacherNameMap = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            List<User> teachers = userService.listByIds(teacherIds);
            teacherNameMap = teachers.stream()
                .collect(java.util.stream.Collectors.toMap(User::getUserId, User::getRealName));
        }
        
        // 批量查询实验信息
        List<Long> experimentIds = list.stream()
            .map(GroupExperiment::getExperimentId)
            .filter(id -> id != null)
            .distinct()
            .collect(java.util.stream.Collectors.toList());
        
        Map<Long, String> experimentNameMap = new HashMap<>();
        if (!experimentIds.isEmpty()) {
            List<Experiment> experiments = experimentService.listByIds(experimentIds);
            experimentNameMap = experiments.stream()
                .collect(java.util.stream.Collectors.toMap(Experiment::getExperimentId, Experiment::getExperimentName));
        }
        
        // 填充教师名称和实验名称
        final Map<Long, String> finalTeacherNameMap = teacherNameMap;
        final Map<Long, String> finalExperimentNameMap = experimentNameMap;
        
        list.forEach(ge -> {
            if (ge.getTeacherId() != null) {
                ge.setTeacherName(finalTeacherNameMap.get(ge.getTeacherId()));
            }
            if (ge.getExperimentId() != null) {
                ge.setExperimentName(finalExperimentNameMap.get(ge.getExperimentId()));
            }
        });
        
        return Result.success(list);
    }

    /**
     * 获取指定时间段的小组实验数据（用于课表显示）
     * 优化版本：批量查询避免N+1问题
     */
    @GetMapping("/by-time")
    public Result<List<GroupExperiment>> getByTime(@RequestParam String experimentTime,
                                                   @RequestParam(required = false) Long semesterId,
                                                   @RequestParam(required = false) Long suiteId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GroupExperiment> q = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        q.eq("experiment_time", experimentTime);
        if (semesterId != null) q.eq("semester_id", semesterId);
        if (suiteId != null) q.eq("suite_id", suiteId);
        q.orderByAsc("group_name"); // 按小组名称排序，确保第一个小组是固定的
        List<GroupExperiment> list = groupExperimentService.list(q);
        
        // 批量查询教师信息，避免N+1查询
        List<Long> teacherIds = list.stream()
            .map(GroupExperiment::getTeacherId)
            .filter(id -> id != null)
            .distinct()
            .collect(java.util.stream.Collectors.toList());
        
        Map<Long, String> teacherNameMap = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            List<User> teachers = userService.listByIds(teacherIds);
            teacherNameMap = teachers.stream()
                .collect(java.util.stream.Collectors.toMap(User::getUserId, User::getRealName));
        }
        
        // 填充教师名称
        final Map<Long, String> finalTeacherNameMap = teacherNameMap;
        list.forEach(ge -> {
            if (ge.getTeacherId() != null) {
                ge.setTeacherName(finalTeacherNameMap.get(ge.getTeacherId()));
            }
        });
        
        return Result.success(list);
    }

    /**
     * 获取指定学期下所有实验套的单双周实验安排（用于学生课表显示）
     */
    @GetMapping("/student-schedule")
    public Result<Map<String, Object>> getStudentSchedule(@RequestParam Long semesterId) {
        if (semesterId == null) {
            return Result.error("学期ID不能为空");
        }
        
        try {
            // 获取该学期下的所有实验套
            List<ExperimentSuite> suites = experimentSuiteService.list(null);
            log.info("获取到 {} 个实验套", suites.size());
            
            Map<String, Object> result = new HashMap<>();
            
            for (ExperimentSuite suite : suites) {
                log.debug("处理实验套: {} - {}", suite.getSuiteId(), suite.getSuiteName());
                
                // 获取单周和双周的数据
                Map<String, Object> suiteData = new HashMap<>();
                
                // 单周数据 (weekType=0)
                List<Map<String, Object>> oddWeekData = getWeekTypeScheduleData(semesterId, suite.getSuiteId(), 0);
                // 双周数据 (weekType=1)
                List<Map<String, Object>> evenWeekData = getWeekTypeScheduleData(semesterId, suite.getSuiteId(), 1);
                
                log.debug("实验套 {} 单周数据: {} 条, 双周数据: {} 条", 
                         suite.getSuiteId(), oddWeekData.size(), evenWeekData.size());
                
                suiteData.put("suiteId", suite.getSuiteId());
                suiteData.put("suiteName", suite.getSuiteName());
                suiteData.put("oddWeek", oddWeekData);
                suiteData.put("evenWeek", evenWeekData);
                
                result.put("suite_" + suite.getSuiteId(), suiteData);
            }
            
            log.info("成功获取学生课表数据，包含 {} 个实验套", result.size());
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取学生课表失败", e);
            return Result.error("获取学生课表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取指定周次类型的课表数据
     * 优先级：优先使用专用行(week_type=指定值)，否则使用通用行(week_type IS NULL)
     */
    private List<Map<String, Object>> getWeekTypeScheduleData(Long semesterId, Long suiteId, Integer weekType) {
        log.info("查询课表数据 - semesterId: {}, suiteId: {}, weekType: {}", semesterId, suiteId, weekType);
        
        // 1. 先查询该时段是否有专用行(week_type=指定值)
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ExperimentSchedule> specificQuery = 
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        specificQuery.eq("semester_id", semesterId);
        specificQuery.eq("suite_id", suiteId);
        specificQuery.eq("week_type", weekType);
        
        List<ExperimentSchedule> specificSchedules = experimentScheduleService.list(specificQuery);
        log.info("专用行查询结果 - weekType: {}, 数量: {}", weekType, specificSchedules.size());
        
        // 打印专用行的详细信息
        for (ExperimentSchedule schedule : specificSchedules) {
            log.info("专用行详情 - scheduleId: {}, experimentTime: {}, groupIds: {}, weekType: {}", 
                    schedule.getScheduleId(), schedule.getExperimentTime(), schedule.getGroupIds(), schedule.getWeekType());
        }
        
        // 2. 如果存在专用行，只使用专用行
        if (!specificSchedules.isEmpty()) {
            log.info("使用专用行数据，weekType={}, 数量={}", weekType, specificSchedules.size());
            return processSchedules(specificSchedules, semesterId, suiteId, weekType);
        }
        
        // 3. 如果没有专用行，查询通用行(week_type IS NULL)
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ExperimentSchedule> generalQuery = 
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        generalQuery.eq("semester_id", semesterId);
        generalQuery.eq("suite_id", suiteId);
        generalQuery.isNull("week_type");
        
        List<ExperimentSchedule> generalSchedules = experimentScheduleService.list(generalQuery);
        log.info("通用行查询结果 - weekType: {}, 数量: {}", weekType, generalSchedules.size());
        
        // 打印通用行的详细信息
        for (ExperimentSchedule schedule : generalSchedules) {
            log.info("通用行详情 - scheduleId: {}, experimentTime: {}, groupIds: {}, weekType: {}", 
                    schedule.getScheduleId(), schedule.getExperimentTime(), schedule.getGroupIds(), schedule.getWeekType());
        }
        
        log.info("使用通用行数据，weekType={}, 数量={}", weekType, generalSchedules.size());
        return processSchedules(generalSchedules, semesterId, suiteId, weekType);
    }
    
    /**
     * 处理课表数据
     */
    private List<Map<String, Object>> processSchedules(List<ExperimentSchedule> schedules, Long semesterId, Long suiteId, Integer weekType) {
        List<Map<String, Object>> result = new ArrayList<>();

        // 预取1：一次性收集所有小组名，批量查 group_experiment（避免每个小组查一次库）
        Set<String> allGroupNames = new LinkedHashSet<>();
        for (ExperimentSchedule schedule : schedules) {
            allGroupNames.addAll(parseGroupIds(schedule.getGroupIds()));
        }
        if (allGroupNames.isEmpty()) {
            return result;
        }

        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GroupExperiment> geQuery =
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        geQuery.eq("semester_id", semesterId);
        geQuery.eq("suite_id", suiteId);
        geQuery.in("group_name", allGroupNames);
        List<GroupExperiment> allGroupExperiments = groupExperimentService.list(geQuery);

        // 按小组名归集，循环内 O(1) 取用
        Map<String, List<GroupExperiment>> geByGroup = new HashMap<>();
        for (GroupExperiment ge : allGroupExperiments) {
            geByGroup.computeIfAbsent(ge.getGroupName(), k -> new ArrayList<>()).add(ge);
        }

        // 预取2：批量取实验名，避免循环内逐条 getById
        Set<Long> experimentIds = new HashSet<>();
        for (GroupExperiment ge : allGroupExperiments) {
            if (ge.getExperimentId() != null) {
                experimentIds.add(ge.getExperimentId());
            }
        }
        Map<Long, String> experimentNameById = new HashMap<>();
        if (!experimentIds.isEmpty()) {
            for (Experiment experiment : experimentService.listByIds(experimentIds)) {
                experimentNameById.put(experiment.getExperimentId(), experiment.getExperimentName());
            }
        }

        for (ExperimentSchedule schedule : schedules) {
            List<String> groupNames = parseGroupIds(schedule.getGroupIds());

            for (String groupName : groupNames) {
                List<GroupExperiment> groupExperiments = geByGroup.get(groupName);
                if (groupExperiments == null) {
                    continue;
                }

                for (GroupExperiment ge : groupExperiments) {
                    // 检查周次是否匹配（单周/双周）
                    if (isWeekTypeMatch(ge.getExperimentTime(), weekType)) {
                        Map<String, Object> scheduleItem = new HashMap<>();
                        scheduleItem.put("groupName", groupName);

                        // 解析 weekday / timeSlot 自 experiment_time，例如 "周一上午" / "周二下午"
                        String et = schedule.getExperimentTime() == null ? "" : schedule.getExperimentTime().trim();
                        String weekday = extractWeekday(et);
                        String timeSlot = et.contains("下午") ? "下午" : "上午";

                        // 结构化字段
                        scheduleItem.put("suiteId", suiteId);
                        scheduleItem.put("weekType", weekType); // 0=单,1=双
                        scheduleItem.put("weekday", weekday);
                        scheduleItem.put("timeSlot", timeSlot);

                        // 兼容旧前端：带前缀的 scheduleTime：例如 "单周一下午"
                        String prefix = weekType != null && weekType == 1 ? "双" : "单";
                        scheduleItem.put("scheduleTime", prefix + weekday + timeSlot);

                        // 周次（例如 "第3周"）
                        scheduleItem.put("weekNumber", ge.getExperimentTime());

                        // 实验名称（来自预取 Map）
                        String experimentName = ge.getExperimentId() != null
                            ? experimentNameById.get(ge.getExperimentId())
                            : null;
                        scheduleItem.put("experimentName", experimentName != null ? experimentName : "未知实验");

                        result.add(scheduleItem);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 从 "周一上午" / "星期一下午" 字符串中提取标准 weekday（周一..周日）
     */
    private String extractWeekday(String experimentTime) {
        if (experimentTime == null) return "";
        try {
            String s = experimentTime.replaceAll("\\s+", "");
            // 优先匹配 "周一..周日"
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(周[一二三四五六日])").matcher(s);
            if (m.find()) return m.group(1);
            // 兼容 "星期一..星期日"
            m = java.util.regex.Pattern.compile("(星期[一二三四五六日])").matcher(s);
            if (m.find()) return m.group(1).replace("星期", "周");
            // 兼容仅有数字或汉字的形式，简单映射
            if (s.contains("一")) return "周一";
            if (s.contains("二")) return "周二";
            if (s.contains("三")) return "周三";
            if (s.contains("四")) return "周四";
            if (s.contains("五")) return "周五";
            if (s.contains("六")) return "周六";
            if (s.contains("日") || s.contains("天")) return "周日";
        } catch (Exception ignore) {}
        return "";
    }
    
    /**
     * 解析group_ids JSON字符串为小组名称列表
     */
    private List<String> parseGroupIds(String groupIds) {
        List<String> result = new ArrayList<>();
        if (groupIds == null || groupIds.trim().isEmpty()) {
            return result;
        }
        
        try {
            // 移除方括号并分割
            String cleanStr = groupIds.replaceAll("[\\[\\]\"]", "");
            if (!cleanStr.isEmpty()) {
                String[] groups = cleanStr.split(",");
                for (String group : groups) {
                    if (!group.trim().isEmpty()) {
                        result.add(group.trim());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析group_ids失败: {}", groupIds, e);
        }
        
        return result;
    }
    
    /**
     * 检查实验时间是否匹配指定的周次类型
     */
    private boolean isWeekTypeMatch(String experimentTime, Integer weekType) {
        if (experimentTime == null) {
            return false;
        }
        
        // 从"第X周"中提取周次数字
        if (experimentTime.contains("第") && experimentTime.contains("周")) {
            try {
                String weekNumStr = experimentTime.replaceAll("[^0-9]", "");
                if (!weekNumStr.isEmpty()) {
                    int weekNum = Integer.parseInt(weekNumStr);
                    // weekType: 0=单周(奇数周), 1=双周(偶数周)
                    return (weekType == 0 && weekNum % 2 == 1) || (weekType == 1 && weekNum % 2 == 0);
                }
            } catch (NumberFormatException e) {
                log.warn("无法解析实验时间格式: {}", experimentTime);
            }
        }
        
        return false;
    }


    @Data
    public static class BatchAssignItemsRequest {
        private Long semesterId;
        private Long suiteId;
        private String groupName;
        private String timeSlot;
        private java.util.List<com.physics.service.GroupExperimentService.Item> items;
    }
}
