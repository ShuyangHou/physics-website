SET @schema_name = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_experiment_schedule_semester_suite_week ON experiment_schedule(semester_id, suite_id, week_type)',
    'SELECT ''idx_experiment_schedule_semester_suite_week exists'''
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'experiment_schedule'
    AND index_name = 'idx_experiment_schedule_semester_suite_week'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_group_experiment_semester_suite_group_slot ON group_experiment(semester_id, suite_id, group_name, time_slot)',
    'SELECT ''idx_group_experiment_semester_suite_group_slot exists'''
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'group_experiment'
    AND index_name = 'idx_group_experiment_semester_suite_group_slot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_group_experiment_semester_suite_teacher_time ON group_experiment(semester_id, suite_id, teacher_id, experiment_time)',
    'SELECT ''idx_group_experiment_semester_suite_teacher_time exists'''
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'group_experiment'
    AND index_name = 'idx_group_experiment_semester_suite_teacher_time'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_experiment_grade_lookup ON experiment_grade(semester_id, suite_id, experiment_id, group_name, user_id)',
    'SELECT ''idx_experiment_grade_lookup exists'''
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'experiment_grade'
    AND index_name = 'idx_experiment_grade_lookup'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_user_student_lookup ON `user`(user_type, class_id, group_name, school_id)',
    'SELECT ''idx_user_student_lookup exists'''
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'user'
    AND index_name = 'idx_user_student_lookup'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
