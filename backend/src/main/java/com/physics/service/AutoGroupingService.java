package com.physics.service;

import com.physics.common.Result;
import java.util.Map;

/**
 * 自动分组服务接口
 */
public interface AutoGroupingService {
    
    /**
     * 根据班级ID列表自动生成分组
     * 
     * @param classIds 班级ID列表，如 "240191,240192,240193,240194"
     * @return 分组结果字符串，如 "240191A,240191B,240192A,240192B"
     */
    String generateGroups(String classIds);
    
    /**
     * 根据时间段更新实验安排的分组信息
     * 
     * @param experimentTime 时间段，如 "周一上午"
     * @param classIds 班级ID列表
     * @param groupIds 分组ID列表
     * @param semesterId 学期ID
     * @param suiteId 实验套ID
     * @param weekType 周类型，0-单周，1-双周
     * @return 操作结果
     */
    Result<Boolean> updateScheduleByTime(String experimentTime, String classIds, String groupIds, Long semesterId, Long suiteId, Integer weekType);
}
