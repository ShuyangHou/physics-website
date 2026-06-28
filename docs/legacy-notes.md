# 历史功能说明合并记录

这份文档合并了原来散落在根目录、`backend/`、`frontend/` 下的旧说明文档。只保留当前代码仍能对应上的内容，已经过期的描述不再保留。

## 1. 实验模板上传和下载

相关代码：

- 前端页面：`frontend/src/views/experiment/template.vue`
- 后端配置：`backend/src/main/java/com/physics/config/WebConfig.java`
- 文件配置测试接口：`backend/src/main/java/com/physics/controller/FileTestController.java`
- 通用文件接口：`backend/src/main/java/com/physics/controller/FileController.java`

历史问题是实验模板文件下载被登录拦截器或静态资源路径影响。当前代码里需要重点检查：

- `WebConfig` 是否映射了 `/files/**` 到配置的上传目录。
- 鉴权拦截器是否允许已登录用户访问文件下载接口。
- 数据库里的 `Experiment.filePath` 是否保存了正确的文件访问路径。
- `application.yml` 里的 `file.upload.path` 是否指向真实存在、可读写的目录。

前端 `template.vue` 里保留了“测试文件配置”的诊断入口，会调用 `/file-test/config` 检查上传目录状态。

## 2. 额外资料文件管理

相关代码：

- 前端组件：`frontend/src/components/ExtraFilesManager.vue`
- 前端 API：`frontend/src/api/file.js`
- 后端接口：`backend/src/main/java/com/physics/controller/FileController.java`
- 后端服务：`backend/src/main/java/com/physics/service/impl/ExtraFileServiceImpl.java`
- 数据实体：`backend/src/main/java/com/physics/entity/ExtraFile.java`
- 建表脚本：`db/migrations/20251004_create_extra_file.sql`

当前功能：

- 管理员可以上传、删除、批量删除额外资料文件。
- 学生可以查看、搜索、下载单个文件或下载 ZIP 资料包。
- 支持 PDF、Word、Excel、PowerPoint 等资料类型。
- 单个文件大小限制由 `application.yml` 的 multipart 和 `file.upload.max-size` 配置控制。

当前接口：

| 功能 | 接口 |
| --- | --- |
| 获取额外资料列表 | `GET /file/extra/list` |
| 上传额外资料 | `POST /file/extra/upload` |
| 下载单个额外资料 | `GET /file/extra/download/{fileId}` |
| 删除单个额外资料 | `DELETE /file/extra/delete/{fileId}` |
| 批量删除额外资料 | `POST /file/extra/batch-delete` |
| 下载资料 ZIP | `GET /file/extra/download-zip` |

## 3. 已过期的历史说明

原 `backend/SCHEDULE_EXPORT_README.md` 记录的是 PDF 导出接口：

- `POST /schedule/exportStudentSchedule`
- `POST /schedule/exportTeacherSchedule`

当前源码中没有这些接口。现在课表导出使用的是：

- `GET /schedule/export-xlsx`
- 前端入口：`frontend/src/api/schedule.js` 的 `exportScheduleXlsx`

所以旧 PDF 导出说明已删除，不再作为当前功能依据。
