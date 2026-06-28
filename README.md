# 物理实验管理系统

这是一个前后端分离的物理实验管理系统。

- 前端：`frontend/`，Vue 3 + Vite + Element Plus
- 后端：`backend/`，Spring Boot 2.7 + MyBatis Plus + MySQL
- 功能划分说明：见 `docs/code-map.md`
- 历史功能说明：见 `docs/legacy-notes.md`
- 数据库脚本说明：见 `db/README.md`

## 主要功能

- 登录、退出、个人中心、修改密码
- 管理学生、教师、学生导入和小组分配
- 管理学期、当前周次和单双周
- 管理实验项目、实验套和实验模板/资料文件
- 实验排课、自动分组、教师课表、学生课表
- 成绩录入、成绩查看、锁定/解锁、Excel 导入导出
- 额外资料文件上传、下载、批量删除和打包下载

## 本地启动

### 后端

```bash
cd backend
mvn spring-boot:run
```

默认接口前缀是 `http://localhost:8080/api`。

### 前端

```bash
cd frontend
npm install
npm run dev
```

前端通过 Vite 代理访问后端 `/api`。

## 代码入口

| 想改什么 | 先看哪里 |
| --- | --- |
| 页面和菜单 | `frontend/src/router/index.js`、`frontend/src/views/` |
| 前端请求 | `frontend/src/api/` |
| 请求封装 | `frontend/src/utils/request.js` |
| 后端接口 | `backend/src/main/java/com/physics/controller/` |
| 后端业务逻辑 | `backend/src/main/java/com/physics/service/impl/` |
| 数据实体 | `backend/src/main/java/com/physics/entity/` |
| 数据库脚本 | `db/migrations/` |

## 注意

- `backend/target/` 是 Maven 构建产物，不要当源码修改。
- `application.yml` 里包含数据库和 Redis 配置，正式部署前建议改成环境变量读取。
- `frontend/src/views/system/equipment.vue`、`frontend/src/views/system/laboratory.vue`、`frontend/src/views/experiment/report.vue` 当前没有路由入口，属于备用或未接入页面。
