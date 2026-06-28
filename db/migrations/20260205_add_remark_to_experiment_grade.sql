ALTER TABLE `experiment_grade`
  ADD COLUMN `remark` varchar(255) NULL COMMENT '备注' AFTER `score`;
