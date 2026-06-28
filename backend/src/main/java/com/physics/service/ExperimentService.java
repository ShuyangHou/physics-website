package com.physics.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.physics.entity.Experiment;

import java.util.List;

public interface ExperimentService extends IService<Experiment> {
    
    /**
     * 根据实验套ID获取实验列表
     */
    List<Experiment> getBySuiteId(Long suiteId);
    
    /**
     * 根据实验套ID获取按顺序排列的实验列表
     */
    List<Experiment> getExperimentsBySuiteOrdered(Long suiteId, String keyword);
}