package com.physics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.physics.entity.ExperimentSuite;
import com.physics.mapper.ExperimentSuiteMapper;
import com.physics.service.ExperimentSuiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExperimentSuiteServiceImpl extends ServiceImpl<ExperimentSuiteMapper, ExperimentSuite> implements ExperimentSuiteService {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public Map<String, Object> getSuiteDetail(Long suiteId) {
        Map<String, Object> result = new HashMap<>();
        
        // 获取实验套基本信息
        ExperimentSuite suite = this.getById(suiteId);
        if (suite == null) {
            return result;
        }
        
        result.put("suite", suite);
        
        // 解析实验ID列表
        List<Long> experimentIds = getExperimentIdsBySuiteId(suiteId);
        result.put("experimentIds", experimentIds);
        
        return result;
    }
    
    @Override
    public List<ExperimentSuite> getAllSuites() {
        QueryWrapper<ExperimentSuite> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        return this.list(wrapper);
    }
    
    @Override
    public List<Long> getExperimentIdsBySuiteId(Long suiteId) {
        ExperimentSuite suite = this.getById(suiteId);
        if (suite == null || suite.getExperimentIds() == null) {
            return Collections.emptyList();
        }
        
        return parseExperimentIds(suite.getExperimentIds());
    }
    
    @Override
    public List<Long> parseExperimentIds(String experimentIds) {
        if (experimentIds == null || experimentIds.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        try {
            return objectMapper.readValue(experimentIds, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            // 如果JSON解析失败，返回空列表
            return Collections.emptyList();
        }
    }
}
