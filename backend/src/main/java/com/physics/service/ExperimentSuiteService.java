package com.physics.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.physics.entity.ExperimentSuite;

import java.util.List;
import java.util.Map;

public interface ExperimentSuiteService extends IService<ExperimentSuite> {
    
    /**
     * 获取实验套详情（包含实验信息）
     */
    Map<String, Object> getSuiteDetail(Long suiteId);
    
    /**
     * 获取所有实验套列表
     */
    List<ExperimentSuite> getAllSuites();
    
    /**
     * 根据实验套ID获取实验列表
     */
    List<Long> getExperimentIdsBySuiteId(Long suiteId);

    /**
     * 解析实验ID列表
     */
    List<Long> parseExperimentIds(String experimentIds);
}
