from __future__ import annotations

from fastapi.testclient import TestClient
from hashlib import sha256
import json
import pytest

from backend.app.main import create_app


@pytest.fixture
def client(tmp_path):
    state_path = tmp_path / "binding.json"
    state_path.write_text(json.dumps({"api_token": "test-api-token"}), encoding="utf-8")
    with TestClient(create_app(tmp_path / "tasks.sqlite3", binding_state_path=state_path), headers={"Authorization": "Bearer test-api-token"}) as test_client:
        yield test_client


def task(task_id: str, title: str, **overrides):
    payload = {
        "id": task_id,
        "series_id": None,
        "title": title,
        "start_date": "2026-10-02",
        "end_date": "2026-10-02",
        "time": None,
        "reminder_at": None,
        "repeat_rule": {
            "kind": "NONE", "weekdays": [], "month_day": None,
            "total_count": None, "occurrence_index": 1,
        },
        "status": "ACTIVE",
        "is_projection": False,
        "completed_at": None,
        "updated_at": "2026-10-02T08:00:00Z",
    }
    payload.update(overrides)
    return payload


def test_crud_round_trip_all_task_shapes(client):
    assert client.get("/api/tasks", headers={"Authorization": ""}).status_code == 401
    all_day = task("all-day", "明天交材料", start_date="2026-10-03", end_date="2026-10-03")
    timed = task(
        "timed", "下午开会", time="15:30", reminder_at="2026-10-02T15:30:00+08:00",
        repeat_rule={
            "kind": "WEEKLY", "weekdays": [2, 4], "month_day": None,
            "total_count": 8, "occurrence_index": 1,
        },
    )
    multi_day = task("multi", "持续两天的任务", start_date="2026-10-04", end_date="2026-10-05")

    for payload in (all_day, timed, multi_day):
        response = client.post("/api/tasks", json=payload)
        assert response.status_code == 201
        assert response.json() == payload
        assert client.get(f"/api/tasks/{payload['id']}").json() == payload

    ranged = client.get("/api/tasks", params={"start_date": "2026-10-05", "end_date": "2026-10-05"})
    assert ranged.status_code == 200
    assert [item["id"] for item in ranged.json()] == ["multi"]

    changed = {**timed, "title": "会议改期", "time": "16:00", "updated_at": "2026-10-02T09:00:00Z"}
    assert client.put("/api/tasks/timed", json=changed).json() == changed

    completed = {**all_day, "status": "COMPLETED", "updated_at": "2026-10-03T12:00:00Z"}
    completed["completed_at"] = "2026-10-03T12:00:00Z"
    assert client.put("/api/tasks/all-day", json=completed).json()["status"] == "COMPLETED"
    deleted = client.delete("/api/tasks/timed")
    assert deleted.status_code == 200
    assert deleted.json()["status"] == "DELETED"
    assert client.get("/api/tasks/timed").status_code == 404
    assert client.get("/api/tasks/all-day").json()["status"] == "COMPLETED"
    assert [item["id"] for item in client.get("/api/tasks", params={"include_completed": True}).json()] == [
        "all-day", "multi",
    ]


def test_sync_prefers_latest_updated_at_and_is_deterministic(client):
    server = task("same", "服务器新版", updated_at="2026-10-02T10:00:00Z")
    client.post("/api/tasks", json=server)
    older = {**server, "title": "客户端旧版", "updated_at": "2026-10-02T09:59:59Z"}
    newer = task("new", "客户端新增", updated_at="2026-10-02T11:00:00Z")
    response = client.post("/api/sync", json={"tasks": [newer, older]})
    assert response.status_code == 200
    assert [(item["id"], item["title"]) for item in response.json()["tasks"]] == [
        ("new", "客户端新增"), ("same", "服务器新版"),
    ]
    tie = {**server, "title": "平局不能覆盖"}
    second = client.post("/api/sync", json={"tasks": [tie]}).json()
    assert next(item for item in second["tasks"] if item["id"] == "same")["title"] == "服务器新版"

    duplicate_a = task("duplicate", "A", updated_at="2026-10-02T12:00:00Z")
    duplicate_b = {**duplicate_a, "title": "B"}
    first_order = client.post("/api/sync", json={"tasks": [duplicate_b, duplicate_a]}).json()
    first_title = next(item for item in first_order["tasks"] if item["id"] == "duplicate")["title"]
    assert first_title == "A"


def test_search_supports_pinyin_initials_and_subsequence(client):
    client.post("/api/tasks", json=task("read", "阅读书籍"))
    client.post("/api/tasks", json=task("meeting", "Weekly Meeting"))
    assert [item["id"] for item in client.get("/api/tasks/search", params={"q": "yd"}).json()] == ["read"]
    assert [item["id"] for item in client.get("/api/tasks/search", params={"q": "wkmt"}).json()] == ["meeting"]


def test_invalid_date_range_is_rejected(client):
    invalid = task("bad", "日期错误", start_date="2026-10-03", end_date="2026-10-02")
    assert client.post("/api/tasks", json=invalid).status_code == 422
    assert client.get(
        "/api/tasks", params={"start_date": "2026-10-03", "end_date": "2026-10-02"}
    ).status_code == 422


def test_binding_token_is_consumed_once_and_expired_or_wrong_tokens_are_rejected(tmp_path):
    state_path = tmp_path / "binding.json"
    state_path.write_text(json.dumps({
        "token_hash": sha256(b"one-time-secret").hexdigest(),
        "expires_at": "2099-01-01T00:00:00Z",
        "used": False,
    }), encoding="utf-8")
    with TestClient(create_app(tmp_path / "tasks.sqlite3", binding_state_path=state_path)) as bound_client:
        wrong = bound_client.post("/api/bind/consume", json={"token": "wrong"})
        assert wrong.status_code == 401
        assert state_path.read_text(encoding="utf-8").find('"used": true') == -1

        accepted = bound_client.post("/api/bind/consume", json={"token": "one-time-secret"})
        assert accepted.status_code == 200
        assert accepted.json()["ok"] is True
        assert len(accepted.json()["access_token"]) >= 32
        assert json.loads(state_path.read_text(encoding="utf-8"))["used"] is True
        assert bound_client.post("/api/bind/consume", json={"token": "one-time-secret"}).status_code == 409

    state_path.write_text(json.dumps({
        "token_hash": sha256(b"expired").hexdigest(),
        "expires_at": "2020-01-01T00:00:00Z",
        "used": False,
    }), encoding="utf-8")
    with TestClient(create_app(tmp_path / "expired.sqlite3", binding_state_path=state_path)) as expired_client:
        assert expired_client.post("/api/bind/consume", json={"token": "expired"}).status_code == 410
