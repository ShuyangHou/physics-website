# 物理实验管理系统代码功能划分

这份文档按“用户能看到的功能”来整理代码位置，方便后续改功能时快速找到前端页面、前端 API、后端接口和主要数据对象。

## 1. 项目整体

| 区域 | 路径 | 作用 |
| --- | --- | --- |
| 前端 | `frontend/` | Vue 3 + Vite + Element Plus，负责页面、菜单、表单、课表展示、文件上传下载等交互 |
| 后端 | `backend/` | Spring Boot 2.7 + MyBatis Plus，负责登录、用户、学期、实验、排课、成绩、文件等接口 |
| 数据库脚本 | `db/`、根目录 `.sql`、`backend/*.sql` | 建表、补字段、迁移、历史导出的 SQL |
| 上传文件 | `backend/uploads/` | 本地上传/导出的文件样例或运行产物 |
| 构建产物 | `backend/target/` | Maven 编译生成的 class 文件，不是源码，通常不要手动改 |

## 2. 前端功能入口

路由入口在 `frontend/src/router/index.js`，菜单权限也在这里定义。

| 页面功能 | 路由 | 页面文件 | 可访问角色 | 主要作用 |
| --- | --- | --- | --- | --- |
| 登录 | `/login` | `views/login/index.vue` | 未登录用户 | 用户登录 |
| 首页 | `/dashboard` | `views/dashboard/index.vue` | admin、teacher、student | 系统概览、统计入口 |
| 实验课表 | `/experiment/schedule` | `views/experiment/schedule.vue` | admin、teacher | 管理实验时间、分组、教师、套题课表，是目前最大的核心页面 |
| 教师课表 | `/experiment/teacher-schedule` | `views/experiment/teacher-schedule.vue` | teacher | 教师查看自己负责的实验安排 |
| 学生课表 | `/experiment/student-schedule` | `views/experiment/student-schedule.vue` | student | 学生查看自己的实验课表 |
| 学生上课查询 | `/experiment/student-trace` | `views/experiment/student-trace.vue` | admin、teacher | 按学生查询上课安排 |
| 教师工作 | `/experiment/teacher-work` | `views/experiment/teacher-work.vue` | admin | 统计教师承担的实验工作 |
| 成绩录入 | `/experiment/grade-entry` | `views/experiment/grade-entry.vue` | admin、teacher | 按小组和实验录入、锁定、导入导出成绩 |
| 成绩查看 | `/experiment/grade` | `views/experiment/grade.vue` | admin、teacher、student | 查看成绩，学生只能看自己的，教师/管理员可筛选 |
| 实验管理 | `/experiment/template` | `views/experiment/template.vue` | admin、teacher、student | 实验项目、实验文件、模板下载相关 |
| 实验套管理 | `/experiment/suite` | `views/experiment/suite.vue` | admin | 管理实验套及其包含的实验 |
| 学期管理 | `/system/semester` | `views/system/semester.vue` | admin | 学期起止时间、当前学期、周次 |
| 导入新生 | `/user/import` | `views/user/import.vue` | admin | Excel 导入学生 |
| 教师管理 | `/user/teacher` | `views/user/teacher.vue` | admin | 教师增删改查、重置密码 |
| 学生管理 | `/user/student` | `views/user/student.vue` | admin、teacher | 学生增删改查、班级/小组筛选 |
| 个人中心 | `/profile/index` | `views/profile/index.vue` | admin、teacher、student | 个人资料、改密码 |

未挂到路由的前端页面：

| 文件 | 当前状态 | 说明 |
| --- | --- | --- |
| `views/system/equipment.vue` | 未在路由中使用 | 设备管理样例页，使用本地静态数据，没有对应后端 Controller |
| `views/system/laboratory.vue` | 未在路由中使用 | 实验室管理样例页，使用本地静态数据，没有对应后端 Controller |
| `views/experiment/report.vue` | 未在路由中使用 | 实验报告页面，当前菜单没有入口 |

## 3. 前端公共代码

| 路径 | 作用 |
| --- | --- |
| `src/main.js` | 创建 Vue 应用，挂载 Element Plus、路由、全局样式 |
| `src/App.vue` | 应用根组件，只负责渲染路由出口 |
| `src/layout/index.vue` | 登录后的主布局，包含侧边栏、顶部栏、面包屑、退出登录等 |
| `src/layout/components/SidebarItem.vue` | 菜单项渲染 |
| `src/layout/components/Breadcrumb.vue` | 面包屑 |
| `src/utils/request.js` | Axios 实例，统一 `/api` 前缀、cookie、响应错误处理 |
| `src/utils/permission.js` | 登录用户信息读取、角色权限判断 |
| `src/utils/date.js` | 日期格式化工具 |
| `src/directives/permission.js` | 按角色控制按钮/元素显示 |
| `src/components/ExtraFilesManager.vue` | 额外资料文件列表、上传、下载、批量删除、打包下载 |

## 4. 前端 API 文件

| API 文件 | 对应后端 | 作用 |
| --- | --- | --- |
| `api/auth.js` | `/auth` | 登录、退出、获取当前用户、改密码、检查默认密码 |
| `api/user.js` | `/user` | 用户、学生、教师、导入学生、分组、重复学生检查、成绩合并 |
| `api/semester.js` | `/semester` | 学期列表、当前学期、周次信息、增删改 |
| `api/experiment.js` | `/experiment` | 实验项目列表、详情、增删改、按实验套查询 |
| `api/suite.js` | `/experiment-suite` | 实验套列表、详情、实验套与实验关联 |
| `api/schedule.js` | `/schedule` | 课表列表、教师/学生课表、按时间更新教师/小组、导出课表、学生上课反查 |
| `api/groupExperiment.js` | `/group-experiment` | 小组实验安排、按小组/时间查询、批量分配实验、学生课表 |
| `api/autoGrouping.js` | `/auto-grouping` | 自动分组并同步课表 |
| `api/grade.js` | `/grade` | 成绩列表、录入、锁定/解锁、统计、导出、模板导入导出 |
| `api/file.js` | `/file` | 实验文件和额外资料文件上传、下载、删除 |
| `api/group.js` | `/user/studentList` | 旧的“小组 API”适配层，目前通过学生列表接口提取小组列表，不是独立后端 `/group` 模块 |

## 5. 后端分层

| 包 | 作用 |
| --- | --- |
| `controller` | 接收 HTTP 请求，做参数校验和权限判断，调用 Service |
| `service` | 业务接口定义 |
| `service/impl` | 业务实现，包括排课、分组、成绩等核心逻辑 |
| `mapper` | MyBatis Plus 数据访问接口 |
| `entity` | 数据库表对应实体 |
| `dto` | 前后端传输对象，不一定直接对应数据库表 |
| `common` | 通用返回结果、用户上下文 |
| `config` | Web 拦截器、MyBatis Plus、Redis 配置 |
| `interceptor` | 登录鉴权拦截器 |
| `utils` | Excel、文件、分组、周次等工具函数 |

## 6. 后端功能模块

| 模块 | Controller | Service/Impl | 主要实体/DTO | 做什么 |
| --- | --- | --- | --- | --- |
| 认证登录 | `AuthController` | `UserServiceImpl` | `LoginRequest`、`LoginResponse`、`User` | 登录、退出、获取当前用户、改密码、检查是否默认密码 |
| 用户管理 | `UserController` | `UserServiceImpl` | `User`、`StudentImportDTO`、`ImportResult` | 管理管理员/教师/学生，导入学生，学生分组，重复学生检查，成绩迁移 |
| 学期管理 | `SemesterController` | `SemesterServiceImpl` | `Semester` | 学期增删改查、当前学期、按日期计算周次 |
| 实验项目 | `ExperimentController` | `ExperimentServiceImpl` | `Experiment` | 实验项目列表、详情、增删改、按实验套取实验 |
| 实验套 | `ExperimentSuiteController` | `ExperimentSuiteServiceImpl` | `ExperimentSuite` | 管理实验套，以及实验套包含哪些实验 |
| 实验课表 | `ExperimentScheduleController` | `ExperimentScheduleServiceImpl` | `ExperimentSchedule`、`ScheduleDTO`、`GenerateScheduleRequest` | 排课、教师课表、学生课表、课表展示、按时间批量更新教师/小组、导出课表 |
| 小组实验安排 | `GroupExperimentController` | `GroupExperimentServiceImpl` | `GroupExperiment` | 维护每个小组在不同周次做哪个实验、哪个老师负责 |
| 自动分组 | `AutoGroupingController` | `AutoGroupingServiceImpl` | `User`、`GroupExperiment`、`ExperimentSchedule` | 根据班级、时间、实验套、单双周自动生成小组并写回学生和课表 |
| 成绩管理 | `ExperimentGradeController` | `ExperimentGradeServiceImpl` | `ExperimentGrade`、`GradeDTO`、`StudentGradeInfoDTO` | 成绩查询、录入、批量覆盖、锁定/解锁、统计、Excel 导入导出 |
| 文件管理 | `FileController` | `ExtraFileServiceImpl`、`FileUtil` | `ExtraFile` | 实验文件上传下载，额外资料上传、下载、批量删除、打包下载 |
| 文件配置测试 | `FileTestController` | 无 | 无 | 给前端测试文件上传配置，`template.vue` 仍会调用 `/file-test/config` |

## 7. 数据关系理解

| 对象 | 含义 | 关键字段 |
| --- | --- | --- |
| `User` | 用户表，三类角色都在这里 | `userType` 区分 `admin`、`teacher`、`student`，学生还有 `schoolId`、`classId`、`groupName` |
| `Semester` | 学期 | `startDate`、`endDate`、`status` |
| `Experiment` | 单个实验项目 | `experimentName`、`description`、文件字段 |
| `ExperimentSuite` | 实验套 | `suiteName`、`experimentIds` |
| `ExperimentSchedule` | 某时间段的课表 | `teacherIds`、`groupIds`、`experimentTime`、`weekType`、`semesterId`、`suiteId` |
| `GroupExperiment` | 某小组某周做的实验 | `groupName`、`weekNumber`、`experimentId`、`teacherId`、`semesterId`、`suiteId` |
| `ExperimentGrade` | 学生成绩 | `userId`、`experimentId`、`score`、`groupName`、`teacherId`、`isLocked` |
| `ExtraFile` | 额外资料文件 | `fileName`、`originalName`、`filePath`、`fileType`、`uploaderId` |

## 8. 按需求找代码

| 要改的功能 | 先看前端 | 再看后端 |
| --- | --- | --- |
| 登录/退出/改密码 | `views/login/index.vue`、`views/profile/index.vue`、`api/auth.js` | `AuthController`、`UserServiceImpl`、`AuthInterceptor` |
| 菜单权限 | `router/index.js`、`utils/permission.js`、`directives/permission.js` | `AuthInterceptor`、各 Controller 内的用户类型判断 |
| 学生/教师管理 | `views/user/student.vue`、`views/user/teacher.vue`、`api/user.js` | `UserController`、`UserServiceImpl` |
| 导入学生 | `views/user/import.vue`、`api/user.js` | `UserController#importStudents`、`ExcelUtils` |
| 学期/周次 | `views/system/semester.vue`、`api/semester.js` | `SemesterController`、`WeekUtils` |
| 实验项目/模板 | `views/experiment/template.vue`、`api/experiment.js`、`api/file.js` | `ExperimentController`、`FileController`、`ExperimentServiceImpl` |
| 实验套 | `views/experiment/suite.vue`、`api/suite.js` | `ExperimentSuiteController`、`ExperimentSuiteServiceImpl` |
| 排课/分组 | `views/experiment/schedule.vue`、`api/schedule.js`、`api/groupExperiment.js`、`api/autoGrouping.js` | `ExperimentScheduleController`、`GroupExperimentController`、`AutoGroupingController` |
| 教师课表/学生课表 | `teacher-schedule.vue`、`student-schedule.vue` | `ExperimentScheduleController`、`GroupExperimentController` |
| 成绩录入/查看 | `grade-entry.vue`、`grade.vue`、`api/grade.js` | `ExperimentGradeController`、`ExperimentGradeServiceImpl` |
| 额外资料文件 | `components/ExtraFilesManager.vue`、`api/file.js` | `FileController`、`ExtraFileServiceImpl`、`FileUtil` |

## 9. 当前整理结论

1. 核心业务是“学生/教师/实验/实验套/学期/小组/课表/成绩/文件”这几条线。
2. `schedule.vue`、`ExperimentScheduleController`、`ExperimentGradeController`、`AutoGroupingServiceImpl`、`GroupExperimentServiceImpl` 文件很大，是后续重构优先级最高的地方。
3. `api/group.js` 不是独立分组模块，它只是从 `/user/studentList` 提取 `groupList` 的适配层，命名容易误解。
4. `equipment.vue`、`laboratory.vue`、`report.vue` 目前没有路由入口，属于备用页面或未完成功能。
5. `backend/target/` 是构建产物，整理代码时不要把它当源码维护。
6. `application.yml` 里有数据库和 Redis 连接配置，后续正式整理时建议改成环境变量配置，避免把真实账号密码写在源码里。
