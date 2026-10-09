# Changelog

## 1.0.0 — 2026-10-09

首个公开版本。源码按 MIT 发布；**不提供托管服务器**，API 需自行部署。

- Android：今天 / 已过期、周视图、月视图、收集箱
- 中文日期识别、全天 / 定时 / 跨天、重复任务、提醒、标题模糊搜索
- 本地 Room + 自托管 FastAPI/SQLite 同步（无账号）
- API 地址改为编译期配置（`local.properties` 或 `-Pticktick.apiBaseUrl`），默认 `http://10.0.2.2:8200/`
- 提供 Docker Compose 与 Nginx 子路径反代说明
