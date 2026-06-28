package com.physics.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.physics.common.Result;
import com.physics.entity.Experiment;
import com.physics.service.ExperimentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/experiment")
public class ExperimentController {
    
    @Autowired
    private ExperimentService experimentService;
    
    @GetMapping("/getExperimentList")
    public Result<Page<Experiment>> getExperimentList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long suiteId) {
        
        Page<Experiment> page = new Page<>(current, size);
        QueryWrapper<Experiment> wrapper = new QueryWrapper<>();
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like("experiment_name", keyword.trim());
        }
        
        // 如果指定了实验套，按实验套中的顺序排列
        if (suiteId != null) {
            // 通过实验套服务获取按顺序排列的实验列表
            List<Experiment> orderedExperiments = experimentService.getExperimentsBySuiteOrdered(suiteId, keyword);
            
            // 手动分页
            int start = (current - 1) * size;
            int end = Math.min(start + size, orderedExperiments.size());
            List<Experiment> pageData = orderedExperiments.subList(start, end);
            
            Page<Experiment> result = new Page<>(current, size);
            result.setRecords(pageData);
            result.setTotal(orderedExperiments.size());
            result.setCurrent(current);
            result.setSize(size);
            
            return Result.success(result);
        } else {
            // 默认按创建时间排序，但优先考虑实验套顺序
            wrapper.orderByAsc("experiment_id"); // 改为按ID升序，通常ID越小越早创建
            
            Page<Experiment> result = experimentService.page(page, wrapper);
            return Result.success(result);
        }
    }
    
    @GetMapping("/getExperimentsBySuite")
    public Result<List<Experiment>> getExperimentsBySuite(@RequestParam Long suiteId) {
        List<Experiment> list = experimentService.getBySuiteId(suiteId);
        return Result.success(list);
    }
    
    @GetMapping("/getExperimentDetail")
    public Result<Experiment> getExperimentDetail(@RequestParam Long experimentId) {
        Experiment experiment = experimentService.getById(experimentId);
        return Result.success(experiment);
    }
    
    @PostMapping("/addExperiment")
    public Result<Boolean> addExperiment(@RequestBody Experiment experiment) {
        boolean result = experimentService.save(experiment);
        return Result.success(result);
    }
    
    @PostMapping("/updateExperiment")
    public Result<Boolean> updateExperiment(@RequestBody Experiment experiment) {
        boolean result = experimentService.updateById(experiment);
        return Result.success(result);
    }
    
    @PostMapping("/deleteExperiment")
    public Result<Boolean> deleteExperiment(@RequestParam Long experimentId) {
        boolean result = experimentService.removeById(experimentId);
        return Result.success(result);
    }
} 