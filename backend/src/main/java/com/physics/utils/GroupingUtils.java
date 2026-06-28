package com.physics.utils;

import com.physics.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 自动分组工具类
 */
@Slf4j
@Component
public class GroupingUtils {
    
    @Autowired
    private UserService userService;
    
    /**
     * 默认每组人数
     */
    private static final int DEFAULT_STUDENTS_PER_GROUP = 12;
    
    /**
     * 根据班级ID列表生成分组
     * 
     * @param classIds 班级ID列表，如 "240191,240192,240193,240194"
     * @return 分组结果字符串，如 "240191A,240191B,240192A,240192B"
     */
    public String generateGroups(String classIds) {
        if (classIds == null || classIds.trim().isEmpty()) {
            return "";
        }
        
        // 解析班级列表
        List<String> classList = parseClassIds(classIds);
        
        if (classList.isEmpty()) {
            return "";
        }
        
        // 计算总学生数
        int totalStudents = calculateTotalStudents(classList);
        
        // 计算分组数
        int groupCount = calculateGroupCount(classList.size(), totalStudents);
        
        // 生成分组
        List<String> groups = generateGroupList(classList, groupCount);
        
        // 转换为字符串
        return String.join(",", groups);
    }
    
    /**
     * 解析班级ID字符串
     * 
     * @param classIds 班级ID字符串，如 "240191,240192,240193,240194"
     * @return 班级列表
     */
    public List<String> parseClassIds(String classIds) {
        if (classIds == null || classIds.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String[] classes = classIds.split(",");
        List<String> classList = new ArrayList<>();
        
        for (String className : classes) {
            String trimmed = className.trim();
            if (!trimmed.isEmpty()) {
                classList.add(trimmed);
            }
        }
        
        return classList;
    }
    
    /**
     * 计算分组数
     * 
     * @param classCount 班级数
     * @param totalStudents 总学生数
     * @return 分组数
     */
    private int calculateGroupCount(int classCount, int totalStudents) {
        // 根据班级数确定分组数
        if (classCount == 2) {
            // 2班 → 固定4组（每班A/B）
            return 4;
        } else if (classCount == 3) {
            // 3班 → 固定6组（每班A/B）
            return 6;
        } else if (classCount == 4) {
            // 4班 → 10组
            return 10;
        } else {
            // 其他情况，根据总人数计算
            return Math.max(4, (int) Math.ceil((double) totalStudents / DEFAULT_STUDENTS_PER_GROUP));
        }
    }
    
    /**
     * 生成分组列表
     * 
     * @param classList 班级列表
     * @param groupCount 分组数
     * @return 分组列表
     */
    private List<String> generateGroupList(List<String> classList, int groupCount) {
        List<String> groups = new ArrayList<>();

        // 固定规则：2/3班仅分A/B；4班固定10组（4*2 + C/D）
        if (classList.size() == 2 || classList.size() == 3) {
            for (String className : classList) {
                Long classStudentCount = userService.countStudentsByClassName(className);
                if (classStudentCount != null && classStudentCount > 0) {
                    groups.add(className + "A");
                    groups.add(className + "B");
                }
            }
            return groups;
        }

        if (classList.size() == 4) {
            for (String className : classList) {
                Long classStudentCount = userService.countStudentsByClassName(className);
                if (classStudentCount != null && classStudentCount > 0) {
                    groups.add(className + "A");
                    groups.add(className + "B");
                }
            }
            String firstClassName = classList.get(0);
            groups.add(firstClassName + "C");
            groups.add(firstClassName + "D");
            return groups;
        }
        
        // 其他班级数（单班或≥5班）：每班独立按每组约 DEFAULT_STUDENTS_PER_GROUP 人均分为若干组，
        // 组名为 班级+字母（A、B、C…），组内不跨班；组数与 AutoGroupingServiceImpl.assignEvenlyPerClass 保持一致。
        for (String className : classList) {
            Long classStudentCount = userService.countStudentsByClassName(className);
            long size = (classStudentCount == null ? 0 : classStudentCount);
            if (size <= 0) {
                continue;
            }
            int n = (int) Math.ceil(size / (double) DEFAULT_STUDENTS_PER_GROUP);
            if (n < 1) n = 1;
            if (n > 26) n = 26;
            for (int i = 0; i < n; i++) {
                groups.add(className + (char) ('A' + i));
            }
            log.info("班级 {} 共 {} 人，生成 {} 个组", className, size, n);
        }

        return groups;
    }
    
    /**
     * 获取分组后缀
     * 
     * @param groupIndex 分组索引
     * @return 分组后缀，如 "A", "B", "C" 等
     */
    private String getGroupSuffix(int groupIndex) {
        return String.valueOf((char) ('A' + (groupIndex - 1) % 26));
    }
    
    /**
     * 计算总学生数
     * 
     * @param classList 班级列表
     * @return 总学生数
     */
    private int calculateTotalStudents(List<String> classList) {
        int totalStudents = 0;
        for (String className : classList) {
            // 从数据库查询每个班级的实际学生数
            Long classStudentCount = userService.countStudentsByClassName(className);
            totalStudents += classStudentCount;
        }
        return totalStudents;
    }
    
    /**
     * 获取分组统计信息
     * 
     * @param classIds 班级ID字符串
     * @return 统计信息
     */
    public Map<String, Object> getGroupingStatistics(String classIds) {
        Map<String, Object> stats = new HashMap<>();
        
        List<String> classList = parseClassIds(classIds);
        int totalStudents = calculateTotalStudents(classList);
        int groupCount = calculateGroupCount(classList.size(), totalStudents);
        
        stats.put("classCount", classList.size());
        stats.put("totalStudents", totalStudents);
        stats.put("groupCount", groupCount);
        stats.put("studentsPerGroup", (int) Math.ceil((double) totalStudents / groupCount));
        
        return stats;
    }
}
