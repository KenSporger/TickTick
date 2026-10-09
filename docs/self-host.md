# 自托管 API

本项目没有官方云服务。Android 客户端通过 `BuildConfig.API_BASE_URL` 调用你部署的 FastAPI。默认把任务存在一份 SQLite 文件里，适合单人使用。

## 运行时要求

- Python 3.10+ 或 Docker
- 持久化目录，用来放 `tasks.sqlite3`
- 可选：Nginx / Caddy 做 HTTPS 和路径前缀

API 无鉴权。生产上应只监听 `127.0.0.1`，再用反向代理或 VPN 接入，不要把 `0.0.0.0:8200` 直接映射到公网。

## 方式 A：venv + uvicorn

在仓库根目录：

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r backend/requirements.txt

export TICKTICK_DB_PATH=/var/lib/ticktick/tasks.sqlite3
mkdir -p "$(dirname "$TICKTICK_DB_PATH")"
uvicorn backend.app.main:app --host 127.0.0.1 --port 8200
```

未设置 `TICKTICK_DB_PATH` 时，默认文件是当前工作目录下的 `backend/tasks.sqlite3`。

用 systemd、launchd 或 pm2 守护进程均可。健康检查：

```bash
curl -fsS http://127.0.0.1:8200/health
```

## 方式 B：Docker Compose

仓库根目录已提供 `Dockerfile` 和 `docker-compose.yml`：

```bash
docker compose up --build -d
```

默认发布 `127.0.0.1:8200`。数据在 Docker volume `ticktick-data` 中。备份时导出该 volume，或改 compose 把 `/data` 挂到主机目录。

## 反向代理

客户端请求的是 `{API_BASE_URL}api/sync`。FastAPI 路由挂在 `/api/...`，没有 `/ticktick-api` 前缀。若要用子路径，必须在代理里剥掉前缀：

```nginx
server {
    listen 443 ssl;
    server_name your.domain;

    location /ticktick-api/ {
        proxy_pass http://127.0.0.1:8200/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

此时 App 里应写：

```
ticktick.apiBaseUrl=https://your.domain/ticktick-api/
```

注意末尾斜杠。直接反代根路径时写成 `https://your.domain/` 即可。

HTTP 明文仅建议用于本机或内网。App 已开启 `usesCleartextTraffic`，方便局域网调试；对公网请用 HTTPS。

## 配置 Android 客户端

1. 复制 `local.properties.example` 为 `local.properties`（若 Android Studio 已生成该文件，只追加 API 行）。
2. 按场景填写 `ticktick.apiBaseUrl`：

| 客户端 | 服务器 | 地址 |
|---|---|---|
| 模拟器 | 宿主机 8200 | `http://10.0.2.2:8200/` |
| 真机 | 同一 Wi-Fi 的电脑 | `http://<电脑局域网IP>:8200/` |
| 真机 | 你的域名 + 上例 Nginx | `https://your.domain/ticktick-api/` |

3. 重新编译安装。地址写在编译期 `BuildConfig` 里，改 URL 必须重编，不能只热重启。

命令行一次性覆盖：

```bash
./gradlew assembleDebug -Pticktick.apiBaseUrl=http://192.168.1.10:8200/
```

真机访问电脑时，电脑防火墙需要放行 8200，uvicorn / compose 需要监听 `0.0.0.0`（仅内网！）。compose 默认绑在 `127.0.0.1`，局域网调试请改端口映射或在宿主机加一层反向代理。

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 存活检查 |
| GET | `/api/tasks` | 列表，可带 `start_date` / `end_date` / `include_completed` |
| GET | `/api/tasks/{id}` | 单条 |
| POST | `/api/tasks` | 创建 |
| PUT | `/api/tasks/{id}` | 更新 |
| DELETE | `/api/tasks/{id}` | 软删除 |
| GET | `/api/tasks/search?q=` | 标题模糊搜索 |
| POST | `/api/sync` | 客户端同步：按 `updated_at` 取较新版本 |

字段约定与 Android DTO 一致，见 `backend/app/schemas.py`。

## 备份

停写或可接受短暂不一致后，复制 SQLite 文件（以及同目录 `-wal` / `-shm` 若存在）。Compose 部署则备份 volume `/data/tasks.sqlite3`。
