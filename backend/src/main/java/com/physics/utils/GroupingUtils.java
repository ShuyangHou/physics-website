package com.physics.utils;

import com.physics.entity.User;
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
        
        // 单一真值源：组名直接取自分组方案（buildGroupingPlan）的键，
        // 确保「生成的组名」与「实际学生分配」永远一致。
        LinkedHashMap<String, List<User>> plan = buildGroupingPlan(classList);
        return String.join(",", plan.keySet());
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
     * 【单一真值源】构建分组方案：组名 → 该组学生（已按学号排序）。
     * 组名生成与学生分配都由本方法产出，避免「组名」与「实际分配」两处各写一套、互相对不上的隐患。
     *
     * 规则（与原逻辑一致，结果不变）：
     * - 2/3 班：每班按学号对半分 A/B；
     * - 4 班：总人数 ÷10 均分，每班 A/B 只装本班，溢出汇入「首班 C/D」池，余数优先补 C/D；
     * - 其他（单班或 ≥5 班）：每班独立按每组约 {@link #DEFAULT_STUDENTS_PER_GROUP} 人均分为 ceil(人数/每组) 个组。
     *
     * @param classList 班级列表
     * @return 有序的「组名 → 学生列表」；组名顺序即排课轮换顺序
     */
    public LinkedHashMap<String, List<User>> buildGroupingPlan(List<String> classList) {
        LinkedHashMap<String, List<User>> plan = new LinkedHashMap<>();
        if (classList == null || classList.isEmpty()) {
            return plan;
        }

        // 读取并按学号排序每个班级的学生（一次读取，供组名与分配共用）
        LinkedHashMap<String, List<User>> classToStudents = new LinkedHashMap<>();
        for (String className : classList) {
            if (className == null || className.trim().isEmpty()) continue;
            List<User> students = userService.getStudentsByClassName(className.trim());
            if (students == null) students = new ArrayList<>();
            sortStudentsBySchoolId(students);
            classToStudents.put(className.trim(), students);
        }

        int classCount = classList.size();

        // 2/3 班：每班按学号对半分 A/B
        if (classCount == 2 || classCount == 3) {
            for (Map.Entry<String, List<User>> e : classToStudents.entrySet()) {
                List<User> students = e.getValue();
                if (students.isEmpty()) continue;
                int n = students.size();
                int aCount = (n + 1) / 2;
                plan.put(e.getKey() + "A", new ArrayList<>(students.subList(0, aCount)));
                plan.put(e.getKey() + "B", new ArrayList<>(students.subList(aCount, n)));
            }
            return plan;
        }

        // 4 班：10 组均分（每班 A/B + 首班 C/D 溢出池）
        if (classCount == 4) {
            return buildPlanFor4Classes(classList, classToStudents);
        }

        // 其他（单班/≥5 班）：每班独立按每组约 DEFAULT_STUDENTS_PER_GROUP 人均分
        for (Map.Entry<String, List<User>> e : classToStudents.entrySet()) {
            String cls = e.getKey();
            List<User> students = e.getValue();
            int size = students.size();
            if (size <= 0) continue;
            int groups = (int) Math.ceil(size / (double) DEFAULT_STUDENTS_PER_GROUP);
            if (groups < 1) groups = 1;
            if (groups > 26) groups = 26;
            int base = size / groups;
            int remainder = size % groups;
            int idx = 0;
            for (int g = 0; g < groups; g++) {
                int count = base + (g < remainder ? 1 : 0);
                List<User> members = new ArrayList<>();
                for (int i = 0; i < count && idx < size; i++) {
                    members.add(students.get(idx++));
                }
                plan.put(cls + (char) ('A' + g), members);
            }
            log.info("班级 {} 共 {} 人，生成 {} 个组", cls, size, groups);
        }
        return plan;
    }

    /**
     * 4 班 → 10 组的分组方案：总人数 ÷10 均分；每班 A/B 只装本班，溢出学生汇入「首班 C/D」池，
     * 余数优先补 C/D 再补各班 A/B。组名顺序：各班 A、B（按 classList），最后首班 C、D。
     */
    private LinkedHashMap<String, List<User>> buildPlanFor4Classes(List<String> classList,
                                                                   LinkedHashMap<String, List<User>> classToStudents) {
        LinkedHashMap<String, List<User>> plan = new LinkedHashMap<>();
        String firstClass = classList.get(0);

        List<String> orderedGroups = new ArrayList<>();
        for (String cls : classList) {
            if (cls == null || cls.trim().isEmpty()) continue;
            orderedGroups.add(cls.trim() + "A");
            orderedGroups.add(cls.trim() + "B");
        }
        orderedGroups.add(firstClass + "C");
        orderedGroups.add(firstClass + "D");

        int total = 0;
        for (List<User> stus : classToStudents.values()) total += stus.size();
        if (total == 0) {
            log.warn("四个班总人数为0，返回空分组方案");
            return plan;
        }

        int base = total / 10;
        int r = total % 10;

        Map<String, Integer> desiredSize = new LinkedHashMap<>();
        for (String g : orderedGroups) desiredSize.put(g, base);
        if (r > 0) { desiredSize.put(firstClass + "C", desiredSize.get(firstClass + "C") + 1); r--; }
        if (r > 0) { desiredSize.put(firstClass + "D", desiredSize.get(firstClass + "D") + 1); r--; }
        for (String g : orderedGroups) {
            if (r <= 0) break;
            if (g.endsWith("C") || g.endsWith("D")) continue;
            desiredSize.put(g, desiredSize.get(g) + 1);
            r--;
        }

        // 分配各班 A/B（只取本班），溢出进 pool
        List<User> remainingPool = new ArrayList<>();
        for (String cls : classList) {
            if (cls == null || cls.trim().isEmpty()) continue;
            List<User> stus = classToStudents.getOrDefault(cls.trim(), new ArrayList<>());
            int idx = 0;
            String gA = cls.trim() + "A";
            String gB = cls.trim() + "B";
            int capA = desiredSize.getOrDefault(gA, 0);
            int capB = desiredSize.getOrDefault(gB, 0);
            List<User> aList = new ArrayList<>();
            for (int i = 0; i < capA && idx < stus.size(); i++) aList.add(stus.get(idx++));
            List<User> bList = new ArrayList<>();
            for (int i = 0; i < capB && idx < stus.size(); i++) bList.add(stus.get(idx++));
            plan.put(gA, aList);
            plan.put(gB, bList);
            while (idx < stus.size()) remainingPool.add(stus.get(idx++));
        }

        // pool 按学号再排一次（跨班合并后）
        sortStudentsBySchoolId(remainingPool);

        String gC = firstClass + "C";
        String gD = firstClass + "D";
        int capC = desiredSize.getOrDefault(gC, 0);
        int capD = desiredSize.getOrDefault(gD, 0);
        List<User> cList = new ArrayList<>();
        List<User> dList = new ArrayList<>();
        if (remainingPool.size() == (capC + capD)) {
            int idx = 0;
            for (int i = 0; i < capC && idx < remainingPool.size(); i++) cList.add(remainingPool.get(idx++));
            for (int i = 0; i < capD && idx < remainingPool.size(); i++) dList.add(remainingPool.get(idx++));
        } else {
            // 极端情况下（AB 受限导致池子人数与 cap 不符）退化为对池子对半分
            int poolN = remainingPool.size();
            int cCnt = (poolN + 1) / 2;
            for (int i = 0; i < poolN; i++) {
                if (i < cCnt) cList.add(remainingPool.get(i));
                else dList.add(remainingPool.get(i));
            }
        }
        plan.put(gC, cList);
        plan.put(gD, dList);

        return plan;
    }

    /**
     * 按学号升序排序学生（学号可解析为数字则按数值，否则按字符串；空学号排末尾）。
     */
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
                return Long.compare(Long.parseLong(sa), Long.parseLong(sb));
            } catch (Exception ignore) {
                return sa.compareTo(sb);
            }
        });
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
