package com.physics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.physics.entity.Experiment;
import com.physics.entity.ExperimentSuite;
import com.physics.mapper.ExperimentMapper;
import com.physics.service.ExperimentService;
import com.physics.service.ExperimentSuiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class ExperimentServiceImpl extends ServiceImpl<ExperimentMapper, Experiment> implements ExperimentService {
    
    @Autowired
    private ExperimentSuiteService experimentSuiteService;
    
    @Override
    public List<Experiment> getBySuiteId(Long suiteId) {
        try {
            // 1. 先查询实验套，获取实验ID列表
            ExperimentSuite suite = experimentSuiteService.getById(suiteId);
            if (suite == null) {
                return new ArrayList<>();
            }
            
            // 2. 解析实验ID列表（格式为JSON数组，如："[6, 8, 9, 10, 11, 12, 13, 14, 15, 16]"）
            String experimentIdsStr = suite.getExperimentIds();
            if (experimentIdsStr == null || experimentIdsStr.trim().isEmpty()) {
                return new ArrayList<>();
            }
            
            // 3. 将JSON字符串转换为ID列表
            List<Long> experimentIds;
            try {
                // 尝试解析JSON数组格式
                if (experimentIdsStr.startsWith("[") && experimentIdsStr.endsWith("]")) {
                    // 移除方括号，按逗号分割
                    String content = experimentIdsStr.substring(1, experimentIdsStr.length() - 1);
                    experimentIds = Arrays.stream(content.split(","))
                            .map(String::trim)
                            .filter(id -> !id.isEmpty())
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                } else {
                    // 如果不是JSON格式，尝试按逗号分割（兼容旧格式）
                    experimentIds = Arrays.stream(experimentIdsStr.split(","))
                            .map(String::trim)
                            .filter(id -> !id.isEmpty())
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                System.err.println("解析实验ID列表失败: " + experimentIdsStr + ", 错误: " + e.getMessage());
                return new ArrayList<>();
            }
            
            if (experimentIds.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 4. 根据ID列表查询实验信息
            QueryWrapper<Experiment> queryWrapper = new QueryWrapper<>();
            queryWrapper.in("experiment_id", experimentIds);
            queryWrapper.orderByAsc("experiment_id");
            
            return this.list(queryWrapper);
            
        } catch (Exception e) {
            // 记录错误日志，返回空列表
            System.err.println("获取实验套 " + suiteId + " 的实验列表失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<Experiment> getExperimentsBySuiteOrdered(Long suiteId, String keyword) {
        try {
            // 1. 先查询实验套，获取实验ID列表
            ExperimentSuite suite = experimentSuiteService.getById(suiteId);
            if (suite == null) {
                return new ArrayList<>();
            }
            
            // 2. 解析实验ID列表
            String experimentIdsStr = suite.getExperimentIds();
            if (experimentIdsStr == null || experimentIdsStr.trim().isEmpty()) {
                return new ArrayList<>();
            }
            
            // 3. 将JSON字符串转换为ID列表
            List<Long> experimentIds;
            try {
                if (experimentIdsStr.startsWith("[") && experimentIdsStr.endsWith("]")) {
                    String content = experimentIdsStr.substring(1, experimentIdsStr.length() - 1);
                    experimentIds = Arrays.stream(content.split(","))
                            .map(String::trim)
                            .filter(id -> !id.isEmpty())
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                } else {
                    experimentIds = Arrays.stream(experimentIdsStr.split(","))
                            .map(String::trim)
                            .filter(id -> !id.isEmpty())
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                System.err.println("解析实验ID列表失败: " + experimentIdsStr + ", 错误: " + e.getMessage());
                return new ArrayList<>();
            }
            
            if (experimentIds.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 4. 根据ID列表查询实验信息
            QueryWrapper<Experiment> queryWrapper = new QueryWrapper<>();
            queryWrapper.in("experiment_id", experimentIds);
            
            // 5. 如果有关键词筛选，添加筛选条件
            if (keyword != null && !keyword.trim().isEmpty()) {
                queryWrapper.like("experiment_name", keyword.trim());
            }
            
            List<Experiment> experiments = this.list(queryWrapper);
            
            // 6. 按照实验套中定义的顺序排序
            List<Experiment> orderedExperiments = new ArrayList<>();
            for (Long experimentId : experimentIds) {
                experiments.stream()
                    .filter(exp -> exp.getExperimentId().equals(experimentId))
                    .findFirst()
                    .ifPresent(orderedExperiments::add);
            }
            
            return orderedExperiments;
            
        } catch (Exception e) {
            System.err.println("获取实验套 " + suiteId + " 的有序实验列表失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}