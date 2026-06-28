ALTER TABLE group_experiment
  ADD COLUMN time_slot VARCHAR(64) NULL COMMENT '时间段：例如 单周一上午/双周二下午，用于区分同周次同实验在不同时间段的老师分配';

CREATE INDEX idx_group_experiment_time_slot
  ON group_experiment (semester_id, suite_id, time_slot, experiment_time, experiment_id, group_name);
