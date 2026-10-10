#!/usr/bin/env bash
set -euo pipefail

bind_host="${TICKTICK_HOST:-127.0.0.1}"
bind_port="${TICKTICK_PORT:-8200}"
exec python -m uvicorn backend.app.main:app --host "$bind_host" --port "$bind_port"
