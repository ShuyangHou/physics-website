# 数据库脚本说明

数据库脚本统一放在 `db/migrations/` 下，按日期排序执行。根目录里原来的完整数据库 dump 和一次性修复脚本已经清理，避免把历史数据、学生数据或过期结构长期留在项目根目录。

## 当前保留的迁移脚本

| 文件 | 作用 |
| --- | --- |
| `20251004_create_extra_file.sql` | 创建额外资料文件表 `extra_file` |
| `20251021_add_performance_indexes.sql` | 给教师工作统计、实验安排查询补充索引 |
| `20251102_add_is_intro_course_to_group_experiment.sql` | 给 `group_experiment` 增加绪论课标记字段 |
| `20251115_add_group_context_to_user.sql` | 给 `user` 增加按学期、实验套、单双周区分分组的字段 |
| `20260205_add_remark_to_experiment_grade.sql` | 给成绩表增加备注字段 |
| `20260228_add_time_slot_to_group_experiment.sql` | 给小组实验安排增加 `time_slot`，区分同周次同实验在不同时段的教师分配 |

## 已清理的历史脚本

| 原文件 | 处理原因 |
| --- | --- |
| 根目录 `physics-website (1).sql` | 历史数据库 dump，结构较旧，含业务数据 |
| 根目录 `localhost (1).sql` | 历史数据库 dump，含业务数据 |
| 根目录 `group_experiment.sql` | 单表 dump，内容可由当前迁移和数据库生成，不适合作为源码维护 |
| 根目录 `fix_experiment_time_format.sql` | 一次性数据修复脚本，已不作为常规迁移保留 |
| `backend/delete_students.sql` | 高风险一次性删学生数据脚本，不应留在代码库 |
| `backend/create_user_group_assignment_table.sql` | 当前源码未使用 `user_group_assignment` 表 |
| `backend/setup_db.ps1` | 指向不存在的初始化 SQL，并包含真实连接信息 |

需要恢复数据库时，优先使用最新可信备份加 `db/migrations/` 中的结构迁移，不要依赖根目录历史 dump。
