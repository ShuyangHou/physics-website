USE `physics-website`;

SET @schema_name = 'physics-website';

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_experiment_grade_group_suite ON experiment_grade(semester_id, suite_id, group_name, experiment_id, user_id, update_time)',
    'SELECT ''idx_experiment_grade_group_suite exists'''
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'experiment_grade'
    AND index_name = 'idx_experiment_grade_group_suite'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_user_group_students ON `user`(user_type, group_name, semester_id, suite_id, week_type, school_id)',
    'SELECT ''idx_user_group_students exists'''
  )
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'user'
    AND index_name = 'idx_user_group_students'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
