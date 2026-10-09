# Personal TickTick

非官方、可自托管的 Android 任务应用。界面和交互参考滴答清单 / TickTick 的今天、周、月视图，数据存在你自己的服务器上。

本仓库**不提供公共服务器**。要用同步，请先自己部署 `backend/`，再把 App 指到那个地址。

> 本项目与 TickTick / 滴答清单官方无关，也不是它们的产品或服务。TickTick 和滴答清单是其各自所有者的商标。

![周视图参考](ticktick-1.jpg)

## 能做什么

- 今天 / 已过期、七日周视图、月视图
- 半屏创建和编辑；标题里的中文日期时间会自动识别
- 全天、定时、跨天任务；每天 / 工作日 / 每周 / 每月重复
- 完成、恢复、跳过、删除本次 / 本次及以后
- 准点提醒（需系统通知和精确闹钟权限）
- 标题模糊搜索（中文子序列、拼音首字母）
- 收集箱：未排期任务；已排期任务移入后会从今天和日历里消失
- 本地 Room 优先，联网后与自建 API 同步；断网修改不会丢

明确不做：登录账号、多用户、协作、标签清单、子任务、附件。

## 架构

| 部分 | 技术 | 谁来跑 |
|---|---|---|
| Android App | Kotlin + Jetpack Compose + Room | 自己编译安装 |
| API | FastAPI + SQLite | **你自己部署** |

API **没有登录、没有鉴权**。只适合个人使用：监听在本机、内网，或放在 VPN / 反向代理后面。不要把端口直接暴露到公网。

## 快速开始

### 1. 部署 API

需要 Python 3.10+。

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r backend/requirements.txt
TICKTICK_DB_PATH=./tasks.sqlite3 uvicorn backend.app.main:app --host 127.0.0.1 --port 8200
```

或用 Docker：

```bash
docker compose up --build
```

确认健康检查：

```bash
curl http://127.0.0.1:8200/health
# {"status":"ok"}
```

更完整的 Nginx、进程守护和真机联网说明见 [docs/self-host.md](docs/self-host.md)。

### 2. 把 App 指到你的 API

复制示例并改地址（`local.properties` 已被 git 忽略）：

```bash
cp local.properties.example local.properties
```

| 场景 | `ticktick.apiBaseUrl` |
|---|---|
| 模拟器访问本机 Docker / uvicorn | `http://10.0.2.2:8200/`（默认） |
| 真机访问同一局域网电脑 | `http://192.168.x.x:8200/` |
| 反向代理，例如 `/ticktick-api/` | `https://your.domain/ticktick-api/` |

Android Studio 如果已经生成过 `local.properties`，只要补上一行 `ticktick.apiBaseUrl=...` 即可。也可以在命令行覆盖：

```bash
./gradlew assembleDebug -Pticktick.apiBaseUrl=http://192.168.1.10:8200/
```

### 3. 编译安装

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release 包同样需要先写好 API 地址再编译。仓库不发布预编译 APK，避免大家连到错误的服务器。

## 开发

P0 检查（改代码后应跑）：

```bash
python3 -m pytest backend/tests -q
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

产品规格在 `docs/specs/v1-personal-ticktick/`。贡献方式见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可

[MIT](LICENSE)
