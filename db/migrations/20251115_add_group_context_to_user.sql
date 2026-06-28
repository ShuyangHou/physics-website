ALTER TABLE `user`
ADD COLUMN `semester_id` bigint DEFAULT NULL COMMENT '学期ID（用于分组）' AFTER `group_name`,
ADD COLUMN `suite_id` bigint DEFAULT NULL COMMENT '实验套ID（用于分组）' AFTER `semester_id`,
ADD COLUMN `week_type` int DEFAULT NULL COMMENT '周类型：0-单周，1-双周（用于分组）' AFTER `suite_id`;

ALTER TABLE `user`
ADD INDEX `idx_group_query` (`semester_id`, `suite_id`, `week_type`, `group_name`),
ADD INDEX `idx_semester_suite_week` (`semester_id`, `suite_id`, `week_type`);
