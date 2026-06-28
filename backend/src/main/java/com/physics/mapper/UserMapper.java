package com.physics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.physics.dto.GroupAssignmentDTO;
import com.physics.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 一条 UPDATE 完成整批学生的分组写回：所有学生共享同一 semester/suite/week，
     * 仅 group_name 因人而异，用 CASE user_id 区分；通过单次往返取代“每组一条 UPDATE”。
     *
     * @param assignments 学生 user_id 与目标组名（参数化，无 SQL 注入风险）
     * @return 受影响行数
     */
    @Update("<script>" +
            "UPDATE `user` SET " +
            "semester_id = #{semesterId}, " +
            "suite_id = #{suiteId}, " +
            "week_type = #{weekType}, " +
            "update_time = NOW(), " +
            "group_name = CASE user_id" +
            "<foreach collection='assignments' item='a'>" +
            " WHEN #{a.userId} THEN #{a.groupName}" +
            "</foreach>" +
            " ELSE group_name END " +
            "WHERE user_id IN " +
            "<foreach collection='assignments' item='a' open='(' separator=',' close=')'>#{a.userId}</foreach>" +
            "</script>")
    int updateGroupingBatch(@Param("assignments") List<GroupAssignmentDTO> assignments,
                            @Param("semesterId") Long semesterId,
                            @Param("suiteId") Long suiteId,
                            @Param("weekType") Integer weekType);
} 