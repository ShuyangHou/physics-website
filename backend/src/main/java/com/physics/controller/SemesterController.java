package com.physics.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.physics.common.Result;
import com.physics.entity.Semester;
import com.physics.service.SemesterService;
import com.physics.utils.WeekUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/semester")
public class SemesterController {
    
    @Autowired
    private SemesterService semesterService;
    
    @GetMapping("/list")
    public Result<Page<Semester>> getSemesterList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        
        Page<Semester> page = new Page<>(current, size);
        QueryWrapper<Semester> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        
        Page<Semester> result = semesterService.page(page, wrapper);
        return Result.success(result);
    }
    
    @PostMapping("/add")
    public Result<Boolean> addSemester(@RequestBody Semester semester) {
        boolean result = semesterService.save(semester);
        return Result.success(result);
    }
    
    @PostMapping("/update")
    public Result<Boolean> updateSemester(@RequestBody Semester semester) {
        boolean result = semesterService.updateById(semester);
        return Result.success(result);
    }
    
    @PostMapping("/delete")
    public Result<Boolean> deleteSemester(@RequestParam Long semesterId) {
        boolean result = semesterService.removeById(semesterId);
        return Result.success(result);
    }
    
    @GetMapping("/current")
    public Result<Semester> getCurrentSemester() {
        QueryWrapper<Semester> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "active");
        Semester semester = semesterService.getOne(wrapper);
        return Result.success(semester);
    }

    @GetMapping("/week-info")
    public Result<Map<String, Object>> getWeekInfo(
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) String date) {
        Semester semester;
        if (semesterId != null) {
            semester = semesterService.getById(semesterId);
        } else {
            QueryWrapper<Semester> wrapper = new QueryWrapper<>();
            wrapper.eq("status", "active");
            semester = semesterService.getOne(wrapper);
        }

        if (semester == null) {
            return Result.error("未找到学期");
        }
        if (semester.getStartDate() == null) {
            return Result.error("学期开始日期未设置");
        }

        LocalDate targetDate;
        try {
            targetDate = (date == null || date.trim().isEmpty()) ? LocalDate.now() : LocalDate.parse(date.trim());
        } catch (Exception e) {
            return Result.error("日期格式不正确，需为 yyyy-MM-dd");
        }

        LocalDate startDate = semester.getStartDate();
        LocalDate week1Monday = WeekUtils.getWeek1Monday(startDate);
        long days = ChronoUnit.DAYS.between(week1Monday, targetDate);
        int weekNumber = days < 0 ? 0 : (int) (days / 7) + 1;
        Integer weekType = weekNumber <= 0 ? null : (weekNumber % 2 == 1 ? 0 : 1);

        int totalWeeks = WeekUtils.getTotalWeeks(startDate, semester.getEndDate());

        // 规则B：第1周绪论课，实验从第3周开始；单双周教学周列表随学期动态生成
        List<Integer> teachingWeeksOdd = WeekUtils.getTeachingWeeksRuleB(startDate, semester.getEndDate(), 0);
        List<Integer> teachingWeeksEven = WeekUtils.getTeachingWeeksRuleB(startDate, semester.getEndDate(), 1);

        boolean inRange = true;
        if (semester.getEndDate() != null) {
            inRange = !targetDate.isBefore(startDate) && !targetDate.isAfter(semester.getEndDate());
        } else {
            inRange = !targetDate.isBefore(startDate);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("semesterId", semester.getSemesterId());
        res.put("targetDate", targetDate.toString());
        res.put("startDate", startDate.toString());
        res.put("week1Monday", week1Monday.toString());
        res.put("weekNumber", weekNumber);
        res.put("weekType", weekType);
        res.put("totalWeeks", totalWeeks);
        res.put("inRange", inRange);
        res.put("weekRule", "B");
        res.put("introWeek", 1);
        res.put("experimentStartWeek", 3);
        res.put("teachingWeeksOdd", teachingWeeksOdd);
        res.put("teachingWeeksEven", teachingWeeksEven);
        return Result.success(res);
    }
}