package com.physics.controller;

import com.physics.common.Result;
import com.physics.service.AutoGroupingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 自动分组控制器
 */
@Slf4j
@RestController
@RequestMapping("/auto-grouping")
public class AutoGroupingController {
    
    @Autowired
    private AutoGroupingService autoGroupingService;
    
    /**
     * 自动分组并更新数据库
     * 
     * @param experimentTime 时间段，如 "周一上午"
     * @param classIds 班级ID列表，如 "240191,240192,240193,240194"
     * @param semesterId 学期ID
     * @param suiteId 实验套ID
     * @return 分组结果
     */
    @PostMapping("/auto-group")
    public Result<String> autoGroupAndUpdate(@RequestParam String experimentTime, 
                                            @RequestParam String classIds,
                                            @RequestParam Long semesterId,
                                            @RequestParam Long suiteId,
                                            @RequestParam(required = false) Integer weekType) {
        try {
            log.info("接收到自动分组请求，时间段: {}, 班级ID列表: {}, 学期ID: {}, 实验套ID: {}, 周类型: {}", 
                    experimentTime, classIds, semesterId, suiteId, weekType);
            
            // 生成分组
            String groupIds = autoGroupingService.generateGroups(classIds);
            
            // 查找或创建实验安排记录
            Result<Boolean> updateResult = autoGroupingService.updateScheduleByTime(experimentTime, classIds, groupIds, semesterId, suiteId, weekType);
            
            if (updateResult.getCode() == 200) {
                return Result.success(groupIds);
            } else {
                return Result.error(updateResult.getMessage());
            }
            
        } catch (Exception e) {
            log.error("自动分组失败", e);
            return Result.error("自动分组失败: " + e.getMessage());
        }
    }
}
