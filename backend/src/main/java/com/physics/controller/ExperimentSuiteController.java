package com.physics.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.physics.common.Result;
import com.physics.entity.ExperimentSuite;
import com.physics.service.ExperimentSuiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/experiment-suite")
public class ExperimentSuiteController {
    
    @Autowired
    private ExperimentSuiteService experimentSuiteService;
    
    /**
     * 获取实验套列表（分页）
     */
    @GetMapping("/list")
    public Result<Page<ExperimentSuite>> getSuiteList(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        
        Page<ExperimentSuite> page = new Page<>(current, size);
        QueryWrapper<ExperimentSuite> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        
        Page<ExperimentSuite> result = experimentSuiteService.page(page, wrapper);
        return Result.success(result);
    }
    
    /**
     * 获取所有实验套列表
     */
    @GetMapping("/all")
    public Result<List<ExperimentSuite>> getAllSuites() {
        List<ExperimentSuite> suites = experimentSuiteService.getAllSuites();
        return Result.success(suites);
    }
    
    /**
     * 获取实验套详情
     */
    @GetMapping("/detail/{suiteId}")
    public Result<Map<String, Object>> getSuiteDetail(@PathVariable Long suiteId) {
        Map<String, Object> detail = experimentSuiteService.getSuiteDetail(suiteId);
        return Result.success(detail);
    }
    
    /**
     * 根据实验套ID获取实验列表
     */
    @GetMapping("/experiments/{suiteId}")
    public Result<List<Long>> getExperimentIdsBySuiteId(@PathVariable Long suiteId) {
        List<Long> experimentIds = experimentSuiteService.getExperimentIdsBySuiteId(suiteId);
        return Result.success(experimentIds);
    }
    
    /**
     * 新增实验套
     */
    @PostMapping("/add")
    public Result<Boolean> addSuite(@RequestBody ExperimentSuite suite) {
        // 解析验证格式但不限制数量
        List<Long> experimentIds = experimentSuiteService.parseExperimentIds(suite.getExperimentIds());
        if (experimentIds == null) {
            return Result.error("实验套数据格式错误");
        }
        
        boolean success = experimentSuiteService.save(suite);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("新增实验套失败");
        }
    }
    
    /**
     * 更新实验套
     */
    @PostMapping("/update")
    public Result<Boolean> updateSuite(@RequestBody ExperimentSuite suite) {
        // 解析验证格式但不限制数量
        List<Long> experimentIds = experimentSuiteService.parseExperimentIds(suite.getExperimentIds());
        if (experimentIds == null) {
            return Result.error("实验套数据格式错误");
        }
        
        boolean success = experimentSuiteService.updateById(suite);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("更新实验套失败");
        }
    }
    
    /**
     * 删除实验套
     */
    @PostMapping("/delete/{suiteId}")
    public Result<Boolean> deleteSuite(@PathVariable Long suiteId) {
        boolean success = experimentSuiteService.removeById(suiteId);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error("删除实验套失败");
        }
    }
}
