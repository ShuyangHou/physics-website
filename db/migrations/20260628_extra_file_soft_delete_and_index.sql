-- extra_file 表设计改进：
-- 1) 增加 deleted 软删除列，与全局 MyBatis-Plus 逻辑删除配置（logic-delete-field: deleted）保持一致，
--    使删除可审计、可恢复，避免物理删除丢失记录。
-- 2) 为 uploader_id 增加索引，加速按上传者查询并体现其外键语义。

ALTER TABLE `extra_file`
  ADD COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，1已删除';

ALTER TABLE `extra_file`
  ADD INDEX `idx_extra_file_uploader` (`uploader_id`);

ALTER TABLE `extra_file`
  ADD INDEX `idx_extra_file_deleted` (`deleted`);
