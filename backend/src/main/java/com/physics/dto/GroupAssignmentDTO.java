package com.physics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个学生的分组归属（用于批量分组写回）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupAssignmentDTO {
    /** 学生 user_id */
    private Long userId;
    /** 该学生所属组名，如 231012A */
    private String groupName;
}
