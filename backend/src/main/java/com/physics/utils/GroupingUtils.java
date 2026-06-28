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
        int totalRemainingStudents = 0;

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
        
        // 第一步：为每个班级生成A组和B组
        for (String className : classList) {
            // 获取当前班级的学生数
            Long classStudentCount = userService.countStudentsByClassName(className);
            
            // 每个班级的前12个学生 → A组
            if (classStudentCount > 0) {
                groups.add(className + "A");
            }
            
            // 每个班级的第13-24个学生 → B组
            if (classStudentCount > 12) {
                groups.add(className + "B");
            }
            
            // 计算剩余学生数（超过24人的部分）
            if (classStudentCount > 24) {
                totalRemainingStudents += (int) (classStudentCount - 24);
            }
        }
        
        // 第二步：所有班级的剩余学生共同组成C组和D组
        // 注意：如果只有2个班级，只生成C组（不生成D组）
        if (totalRemainingStudents > 0) {
            // 使用第一个班级的名称作为C组和D组的标识
            String firstClassName = classList.get(0);
            
            // 根据班级数量和剩余学生数量决定分配多少个组
            if (classList.size() == 2) {
                // 两个班级：只生成C组（即使剩余学生超过12人，也不生成D组）
                groups.add(firstClassName + "C");
                log.info("两个班级，剩余学生总数: {}，只分配1个组: {}", totalRemainingStudents, firstClassName + "C");
            } else if (totalRemainingStudents <= 12) {
                // 剩余学生数 ≤ 12，只分配一个组（C组）
                groups.add(firstClassName + "C");
                log.info("剩余学生总数: {} (≤12)，分配1个组: {}", totalRemainingStudents, firstClassName + "C");
            } else if (totalRemainingStudents <= 24) {
                // 剩余学生数 13-24，分配两个组（C组和D组）
                groups.add(firstClassName + "C");
                groups.add(firstClassName + "D");
                log.info("剩余学生总数: {} (13-24)，分配2个组: {}, {}", totalRemainingStudents, firstClassName + "C", firstClassName + "D");
            } else {
                // 剩余学生数 > 24，平均分配到C组和D组
                int cGroupStudents = totalRemainingStudents / 2;
                int dGroupStudents = totalRemainingStudents - cGroupStudents;
                
                groups.add(firstClassName + "C");
                groups.add(firstClassName + "D");
                
                log.info("剩余学生总数: {} (>24)，C组人数: {}, D组人数: {}", 
                        totalRemainingStudents, cGroupStudents, dGroupStudents);
            }
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
