ALTER TABLE `group_experiment`
ADD COLUMN `is_intro_course` tinyint(1) DEFAULT 0 COMMENT '是否为绪论课：0 不是，1 是' AFTER `suite_id`;
