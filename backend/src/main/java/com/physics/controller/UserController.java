package com.physics.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.physics.common.Result;
import com.physics.dto.ImportResult;
import com.physics.dto.StudentImportDTO;
import com.physics.entity.User;
import com.physics.entity.ExperimentGrade;
import com.physics.service.ExperimentGradeService;
import com.physics.service.UserService;
import com.physics.utils.ExcelUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.physics.dto.StudentListResponse;
import com.physics.entity.GroupExperiment;
import com.physics.service.GroupExperimentService;

@RestController
@RequestMapping("/user")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private GroupExperimentService groupExperimentService;

    @Autowired
    private ExperimentGradeService experimentGradeService;
    
    @GetMapping("/list")
    public Result<Page<User>> getUserList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String groupName) {
        
        Page<User> page = new Page<>(current, size);
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        
        if (userType != null && !userType.isEmpty()) {
            wrapper.eq("user_type", userType);
        }
        if (classId != null && !classId.isEmpty()) {
            wrapper.eq("class_id", classId);
        }
        if (realName != null && !realName.isEmpty()) {
            wrapper.like("real_name", realName);
        }
        if (groupName != null && !groupName.isEmpty()) {
            wrapper.eq("group_name", groupName);
        }
        
        wrapper.orderByDesc("create_time");
        
        Page<User> result = userService.page(page, wrapper);
        return Result.success(result);
    }

    @GetMapping("/check-duplicate-student")
    public Result<Map<String, Object>> checkDuplicateStudent(
            @RequestParam String schoolId,
            @RequestParam(required = false) Long excludeUserId) {
        if (schoolId == null || schoolId.trim().isEmpty()) {
            return Result.error("学号不能为空");
        }

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("user_type", "student")
                .eq("school_id", schoolId.trim());
        if (excludeUserId != null) {
            wrapper.ne("user_id", excludeUserId);
        }
        User dup = userService.getOne(wrapper);
        Map<String, Object> res = new HashMap<>();
        if (dup == null) {
            res.put("duplicate", false);
        } else {
            res.put("duplicate", true);
            res.put("userId", dup.getUserId());
            res.put("realName", dup.getRealName());
            res.put("classId", dup.getClassId());
            res.put("groupName", dup.getGroupName());
        }
        return Result.success(res);
    }

    @PostMapping("/merge-student-grades")
    public Result<Map<String, Object>> mergeStudentGrades(
            @CookieValue("userId") Long currentUserId,
            @RequestParam Long fromUserId,
            @RequestParam Long toUserId) {

        User current = userService.getById(currentUserId);
        if (current == null || !"admin".equalsIgnoreCase(current.getUserType())) {
            return Result.error("只有管理员可以合并学生成绩");
        }
        if (fromUserId == null || toUserId == null || fromUserId.equals(toUserId)) {
            return Result.error("参数不合法");
        }

        User fromUser = userService.getById(fromUserId);
        User toUser = userService.getById(toUserId);
        if (fromUser == null || toUser == null) {
            return Result.error("学生不存在");
        }
        if (!"student".equalsIgnoreCase(fromUser.getUserType()) || !"student".equalsIgnoreCase(toUser.getUserType())) {
            return Result.error("只能合并学生账号");
        }

        List<ExperimentGrade> fromGrades = experimentGradeService.list(new QueryWrapper<ExperimentGrade>()
                .eq("user_id", fromUserId));

        int migrated = 0;
        int skipped = 0;

        for (ExperimentGrade g : fromGrades) {
            if (g == null) continue;
            // 避免合并后出现同一实验/学期/套件多条记录
            ExperimentGrade existing = experimentGradeService.getOne(new QueryWrapper<ExperimentGrade>()
                    .eq("user_id", toUserId)
                    .eq("experiment_id", g.getExperimentId())
                    .eq("semester_id", g.getSemesterId())
                    .eq("suite_id", g.getSuiteId())
                    .orderByDesc("update_time")
                    .last("LIMIT 1"));
            if (existing != null) {
                skipped++;
                continue;
            }

            ExperimentGrade upd = new ExperimentGrade();
            upd.setGradeId(g.getGradeId());
            upd.setUserId(toUserId);
            experimentGradeService.updateById(upd);
            migrated++;
        }

        Map<String, Object> res = new HashMap<>();
        res.put("migrated", migrated);
        res.put("skipped", skipped);
        res.put("fromUserId", fromUserId);
        res.put("toUserId", toUserId);
        return Result.success(res);
    }
    
    @PostMapping("/add")
    public Result<Boolean> addUser(@CookieValue("userId") Long currentUserId,
                                  @RequestBody User user) {
        User current = userService.getById(currentUserId);
        if (current == null) {
            return Result.error("用户不存在");
        }
        boolean isAdmin = "admin".equalsIgnoreCase(current.getUserType());
        boolean isTeacher = "teacher".equalsIgnoreCase(current.getUserType());
        if (!isAdmin && !isTeacher) {
            return Result.error("权限不足");
        }
        if (user == null) {
            return Result.error("参数不合法");
        }
        // 教师只能新增学生
        if (isTeacher) {
            User sanitized = new User();
            sanitized.setUserType("student");
            sanitized.setSchoolId(user.getSchoolId());
            sanitized.setRealName(user.getRealName());
            sanitized.setClassId(user.getClassId());
            sanitized.setGroupName(user.getGroupName());
            user = sanitized;
        }
        // 若为学生，自动为重名情况生成带后缀的不重复姓名
        try {
            if (user != null && "student".equalsIgnoreCase(user.getUserType())) {
                String baseName = user.getRealName();
                String unique = userService.generateUniqueStudentRealName(baseName);
                user.setRealName(unique);
                // 生成不重复用户名（基础为传入用户名或学号），防止与现有账户冲突
                String baseUsername = (user.getUsername() != null && !user.getUsername().trim().isEmpty())
                        ? user.getUsername().trim()
                        : (user.getSchoolId() != null && !user.getSchoolId().trim().isEmpty() ? user.getSchoolId().trim() : "user");
                String candidate = baseUsername;
                int suffix = 1;
                while (userService.count(new QueryWrapper<User>().eq("username", candidate)) > 0) {
                    suffix++;
                    candidate = baseUsername + "(" + suffix + ")";
                }
                user.setUsername(candidate);
                if (user.getPassword() == null || user.getPassword().isEmpty()) {
                    user.setPassword("0000");
                }
            }
        } catch (Exception ignored) {}
        boolean result = userService.save(user);
        return Result.success(result);
    }
    
    @PostMapping("/update")
    public Result<Boolean> updateUser(@CookieValue("userId") Long currentUserId,
                                      @RequestBody User user) {
        User current = userService.getById(currentUserId);
        if (current == null) {
            return Result.error("用户不存在");
        }
        boolean isAdmin = "admin".equalsIgnoreCase(current.getUserType());
        boolean isTeacher = "teacher".equalsIgnoreCase(current.getUserType());
        if (!isAdmin && !isTeacher) {
            return Result.error("权限不足");
        }
        if (user == null || user.getUserId() == null) {
            return Result.error("参数不合法");
        }
        // 获取更新前的用户信息
        User oldUser = userService.getById(user.getUserId());
        if (oldUser == null) {
            return Result.error("用户不存在");
        }

        // 教师只能修改学生，且只允许修改学生基础字段
        if (isTeacher) {
            if (!"student".equalsIgnoreCase(oldUser.getUserType())) {
                return Result.error("教师只能修改学生信息");
            }
            User sanitized = new User();
            sanitized.setUserId(oldUser.getUserId());
            sanitized.setUserType("student");
            sanitized.setSchoolId(user.getSchoolId());
            sanitized.setRealName(user.getRealName());
            sanitized.setClassId(user.getClassId());
            sanitized.setGroupName(user.getGroupName());
            user = sanitized;
        }
        
        // 检查小组是否发生变化
        String oldGroupName = oldUser.getGroupName();
        String newGroupName = user.getGroupName();
        
        boolean result = userService.updateById(user);
        
        // 如果小组发生变化且用户是学生，同步更新成绩记录
        if (result && "student".equals(oldUser.getUserType()) && 
            !java.util.Objects.equals(oldGroupName, newGroupName)) {
            
            try {
                // 同步更新该学生的所有成绩记录中的 group_name 字段。
                // 备注：成绩查看/导出可能会按 group_name 过滤；若不同步，会出现“改了小组后成绩不显示”。
                if (newGroupName != null && !newGroupName.trim().isEmpty()) {
                    UpdateWrapper<ExperimentGrade> uw = new UpdateWrapper<>();
                    uw.eq("user_id", user.getUserId());
                    uw.set("group_name", newGroupName.trim());
                    experimentGradeService.update(uw);
                }
                
                // 记录日志
                System.out.println("学生 " + user.getUserId() + " 的小组从 " + oldGroupName + " 更改为 " + newGroupName);
                
            } catch (Exception e) {
                // 即使同步失败也不影响用户信息更新
                System.err.println("同步更新成绩小组信息失败: " + e.getMessage());
            }
        }
        
        return Result.success(result);
    }
    
    @PostMapping("/delete")
    public Result<Boolean> deleteUser(@CookieValue("userId") Long currentUserId,
                                      @RequestParam Long userId) {
        User current = userService.getById(currentUserId);
        if (current == null || !"admin".equalsIgnoreCase(current.getUserType())) {
            return Result.error("权限不足");
        }
        boolean result = userService.removeById(userId);
        return Result.success(result);
    }
    
    @PostMapping("/resetPassword")
    public Result<Boolean> resetPassword(@CookieValue("userId") Long currentUserId,
                                         @RequestParam Long userId) {
        User current = userService.getById(currentUserId);
        if (current == null) {
            return Result.error("用户不存在");
        }
        boolean isAdmin = "admin".equalsIgnoreCase(current.getUserType());
        boolean isTeacher = "teacher".equalsIgnoreCase(current.getUserType());
        if (!isAdmin && !isTeacher) {
            return Result.error("权限不足");
        }
        if (isTeacher) {
            User target = userService.getById(userId);
            if (target == null || !"student".equalsIgnoreCase(target.getUserType())) {
                return Result.error("教师只能重置学生密码");
            }
        }

        User upd = new User();
        upd.setUserId(userId);
        upd.setPassword("0000");
        boolean result = userService.updateById(upd);
        return Result.success(result);
    }
    
    @PostMapping("/changeAdminPassword")
    public Result<Boolean> changeAdminPassword(
            @CookieValue("userId") Long currentUserId,
            @RequestParam Long targetUserId,
            @RequestParam String newPassword) {
        
        // 验证当前用户是否为管理员
        User currentUser = userService.getById(currentUserId);
        if (currentUser == null || !"admin".equals(currentUser.getUserType())) {
            return Result.error("只有管理员可以修改管理员密码");
        }
        
        // 验证目标用户是否为管理员
        User targetUser = userService.getById(targetUserId);
        if (targetUser == null) {
            return Result.error("目标用户不存在");
        }
        if (!"admin".equals(targetUser.getUserType())) {
            return Result.error("只能修改管理员用户的密码");
        }
        
        // 验证新密码
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return Result.error("新密码不能为空");
        }
        if (newPassword.length() < 4) {
            return Result.error("密码长度不能少于4位");
        }
        
        // 修改密码
        try {
            User updateUser = new User();
            updateUser.setUserId(targetUserId);
            updateUser.setPassword(newPassword);
            boolean result = userService.updateById(updateUser);
            
            if (result) {
                return Result.success(true);
            } else {
                return Result.error("修改密码失败");
            }
        } catch (Exception e) {
            return Result.error("修改密码失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/studentList")
    public Result<StudentListResponse> getStudentList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) Long suiteId,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        
        Page<User> page = new Page<>(current, size);
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("user_type", "student");
        
        if (classId != null && !classId.isEmpty()) {
            String raw = classId.trim();
            // 支持多班：逗号/空格/中文逗号分隔
            String[] parts = raw.split("[，,\\s]+");
            if (parts.length > 1) {
                wrapper.and(w -> {
                    for (int i = 0; i < parts.length; i++) {
                        String p = parts[i].trim();
                        if (p.isEmpty()) continue;
                        if (i == 0) {
                            w.eq("class_id", p);
                        } else {
                            w.or(q -> q.eq("class_id", p));
                        }
                    }
                });
            } else {
                // 单个条件：class_id 精确 或 class_name 模糊
                wrapper.and(w -> w.eq("class_id", raw));
            }
        }
        if (realName != null && !realName.isEmpty()) {
            wrapper.like("real_name", realName);
        }
        if (schoolId != null && !schoolId.isEmpty()) {
            wrapper.like("school_id", schoolId.trim());
        }
        if (groupName != null && !groupName.isEmpty()) {
            wrapper.eq("group_name", groupName);
        }
        
        // 按实验套筛选：通过实验套找到相关的小组，然后筛选这些小组的学生
        if (suiteId != null) {
            // 查询该实验套下的所有小组
            List<String> suiteGroupNames = groupExperimentService.list(
                new QueryWrapper<GroupExperiment>().eq("suite_id", suiteId)
            ).stream()
            .map(GroupExperiment::getGroupName)
            .distinct()
            .collect(Collectors.toList());
            
            if (!suiteGroupNames.isEmpty()) {
                wrapper.in("group_name", suiteGroupNames);
            } else {
                // 如果该实验套下没有小组，返回空结果
                wrapper.eq("user_id", -1); // 不存在的用户ID，确保返回空结果
            }
        }
        
        // 处理排序
        if (sortField != null && !sortField.isEmpty()) {
            // 验证排序字段是否合法
            if (isValidSortField(sortField)) {
                if ("desc".equalsIgnoreCase(sortOrder)) {
                    wrapper.orderByDesc(sortField);
                } else {
                    wrapper.orderByAsc(sortField);
                }
            } else {
                // 如果排序字段不合法，使用默认排序
                wrapper.orderByAsc("school_id");
            }
        } else {
            // 默认按学号升序排序
            wrapper.orderByAsc("school_id");
        }
        
        Page<User> result = userService.page(page, wrapper);
        
        // 获取所有班级和小组列表
        List<String> classList = userService.getAllClassNames();
        List<String> groupList = userService.getAllGroupNames();
        
        StudentListResponse response = new StudentListResponse(result, classList, groupList);
        return Result.success(response);
    }
    
    @GetMapping("/teacherList")
    public Result<Page<User>> getTeacherList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String realName) {
        
        Page<User> page = new Page<>(current, size);
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("user_type", "teacher");
        
        if (realName != null && !realName.isEmpty()) {
            wrapper.like("real_name", realName);
        }
        
        wrapper.orderByDesc("create_time");
        
        Page<User> result = userService.page(page, wrapper);
        return Result.success(result);
    }
    
    @PostMapping("/importStudents")
    public Result<ImportResult> importStudents(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return Result.error("请选择要上传的Excel文件");
            }

            // 校验文件大小：不超过10MB
            long maxSize = 10L * 1024 * 1024;
            if (file.getSize() > maxSize) {
                return Result.error("只能上传xlsx/xls文件，且不超过10MB");
            }

            // 校验文件类型：仅允许 xls/xlsx
            String originalFilename = file.getOriginalFilename();
            String lower = originalFilename == null ? "" : originalFilename.toLowerCase();
            boolean isExcel = lower.endsWith(".xlsx") || lower.endsWith(".xls");
            if (!isExcel) {
                return Result.error("只能上传xlsx/xls文件，且不超过10MB");
            }

            // 解析Excel文件
            List<StudentImportDTO> students = ExcelUtils.parseStudentExcel(file);
            
            // 导入学生数据
            ImportResult result = userService.importStudents(students);
            
            return Result.success(result);
        } catch (IOException e) {
            return Result.error("文件解析失败：" + e.getMessage());
        } catch (Exception e) {
            return Result.error("导入失败：" + e.getMessage());
        }
    }


    
    /**
     * 验证排序字段是否合法
     * @param field 排序字段
     * @return 是否合法
     */
    private boolean isValidSortField(String field) {
        // 允许排序的字段列表
        String[] allowedFields = {
            "school_id", "real_name", "class_id", "group_name", "create_time"
        };
        
        for (String allowedField : allowedFields) {
            if (allowedField.equals(field)) {
                return true;
            }
        }
        return false;
    }
}
