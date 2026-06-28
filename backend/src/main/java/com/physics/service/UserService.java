package com.physics.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.physics.dto.LoginRequest;
import com.physics.dto.LoginResponse;
import com.physics.dto.ImportResult;
import com.physics.dto.StudentImportDTO;
import com.physics.entity.User;

import java.util.List;

public interface UserService extends IService<User> {
    LoginResponse login(LoginRequest request);
    User getCurrentUser();
    void logout(Long userId);
    
    /**
     * 批量导入学生
     * @param students 学生数据列表
     * @return 导入结果
     */
    ImportResult importStudents(List<StudentImportDTO> students);
    
    /**
     * 根据班级名称统计学生数量
     * @param className 班级名称
     * @return 学生数量
     */
    Long countStudentsByClassName(String className);
    
    /**
     * 根据班级名称获取学生列表
     * @param className 班级名称
     * @return 学生列表
     */
    List<User> getStudentsByClassName(String className);
    
    /**
     * 获取所有教师
     * @return 教师列表
     */
    List<User> getTeachers();
    
    /**
     * 获取所有班级名称
     * @return 班级名称列表
     */
    List<String> getAllClassNames();
    
    /**
     * 获取所有小组名称
     * @return 小组名称列表
     */
    List<String> getAllGroupNames();

    /**
     * 修改当前登录用户密码
     */
    boolean changePassword(Long userId, String oldPassword, String newPassword);
    
    /**
     * 检查用户密码是否为默认密码
     */
    boolean isDefaultPassword(Long userId);

    /**
     * 为学生姓名生成不与现有学生重复的姓名（必要时追加后缀）。
     * 如：张三 -> 张三(2) -> 张三(3)...
     */
    String generateUniqueStudentRealName(String baseRealName);

    /**
     * 生成不与任何现有用户名重复的用户名（必要时追加后缀）。
     * 内部只查询一次现有用户名集合，避免循环逐次查库。
     * 如：2021001 -> 2021001(2) -> 2021001(3)...
     */
    String generateUniqueUsername(String baseUsername);

    /**
     * 批量更新一组学生的分组信息（只更新 group_name/semester_id/suite_id/week_type 四列），
     * 通过单条 UPDATE ... WHERE user_id IN (...) 完成，避免整行重写与逐行往返。
     * @return 受影响行数
     */
    int updateGroupingByIds(List<Long> userIds, String groupName, Long semesterId, Long suiteId, Integer weekType);
} 