from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import datetime, timezone
import fcntl
from hashlib import sha256
import hmac
import json
import os
from pathlib import Path
import re
import tempfile
import secrets

from fastapi import FastAPI, HTTPException, Query, Request, status
from fastapi.responses import JSONResponse

from .schemas import BindConsumeRequest, SyncRequest, SyncResponse, TaskPayload
from .store import TaskStore, parse_updated_at


def _validate_timestamp(payload: TaskPayload) -> None:
    try:
        parse_updated_at(payload.updated_at)
    except (TypeError, ValueError) as error:
        raise HTTPException(status_code=422, detail=str(error)) from error


def _is_subsequence(needle: str, haystack: str) -> bool:
    iterator = iter(haystack)
    return all(any(character == candidate for candidate in iterator) for character in needle)


def _chinese_initial(character: str) -> str:
    try:
        code = character.encode("gbk")
    except UnicodeEncodeError:
        return character.casefold()
    if len(code) != 2:
        return character.casefold()
    value = code[0] * 256 + code[1] - 65536
    boundaries = (
        (-20319, "a"), (-20284, "b"), (-19776, "c"), (-19219, "d"),
        (-18711, "e"), (-18527, "f"), (-18240, "g"), (-17923, "h"),
        (-17418, "j"), (-16475, "k"), (-16213, "l"), (-15641, "m"),
        (-15166, "n"), (-14923, "o"), (-14915, "p"), (-14631, "q"),
        (-14150, "r"), (-14091, "s"), (-13319, "t"), (-12839, "w"),
        (-12557, "x"), (-11848, "y"), (-11056, "z"),
    )
    initial = character.casefold()
    for boundary, letter in boundaries:
        if value < boundary:
            break
        initial = letter
    return initial


def _search_keys(title: str) -> tuple[str, str]:
    folded = "".join(character for character in title.casefold() if character.isalnum())
    initials = "".join(_chinese_initial(character) for character in title if character.isalnum())
    words = "".join(word[0] for word in re.findall(r"[a-z0-9]+", title.casefold()) if word)
    return folded, initials + words


def create_app(
    database_path: str | Path | None = None,
    binding_state_path: str | Path | None = None,
) -> FastAPI:
    path = Path(database_path or os.environ.get("TICKTICK_DB_PATH", "backend/tasks.sqlite3"))
    bind_path_value = binding_state_path or os.environ.get("TICKTICK_BINDING_STATE")
    bind_path = Path(bind_path_value) if bind_path_value else None

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        path.parent.mkdir(parents=True, exist_ok=True)
        app.state.store = TaskStore(path)
        yield

    app = FastAPI(title="TickTick personal API", lifespan=lifespan)

    @app.middleware("http")
    async def require_api_token(request: Request, call_next):
        if request.url.path.startswith("/api/") and request.url.path != "/api/bind/consume":
            if bind_path is None or not bind_path.is_file():
                return JSONResponse(status_code=503, content={"detail": "API token is not configured"})
            try:
                state = json.loads(bind_path.read_text(encoding="utf-8"))
                expected = str(state["api_token"])
            except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError):
                return JSONResponse(status_code=503, content={"detail": "API token is not configured"})
            supplied = request.headers.get("Authorization", "")
            if not supplied.startswith("Bearer ") or not hmac.compare_digest(supplied[7:], expected):
                return JSONResponse(status_code=401, content={"detail": "invalid API token"})
        return await call_next(request)

    def store(request: Request) -> TaskStore:
        return request.app.state.store

    @app.post("/api/bind/consume")
    def consume_binding(payload: BindConsumeRequest):
        if bind_path is None or not bind_path.is_file():
            raise HTTPException(status_code=503, detail="binding is not configured")
        lock_path = bind_path.with_suffix(bind_path.suffix + ".lock")
        lock_path.parent.mkdir(parents=True, exist_ok=True)
        with lock_path.open("a+", encoding="utf-8") as lock:
            fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
            try:
                try:
                    state = json.loads(bind_path.read_text(encoding="utf-8"))
                    expires_at = datetime.fromisoformat(str(state["expires_at"]).replace("Z", "+00:00"))
                    expected_hash = str(state.get("token_hash") or state["token_sha256"])
                except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
                    raise HTTPException(status_code=503, detail="binding state is invalid") from error
                if bool(state.get("used")):
                    raise HTTPException(status_code=409, detail="binding token already used")
                if expires_at <= datetime.now(timezone.utc):
                    raise HTTPException(status_code=410, detail="binding token expired")
                actual_hash = sha256(payload.token.encode("utf-8")).hexdigest()
                if not hmac.compare_digest(actual_hash, expected_hash):
                    raise HTTPException(status_code=401, detail="invalid binding token")
                state["used"] = True
                state["used_at"] = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
                state.setdefault("api_token", secrets.token_urlsafe(32))
                fd, temporary_name = tempfile.mkstemp(prefix=bind_path.name, dir=bind_path.parent)
                try:
                    with os.fdopen(fd, "w", encoding="utf-8") as output:
                        json.dump(state, output, ensure_ascii=False)
                        output.flush()
                        os.fsync(output.fileno())
                    os.replace(temporary_name, bind_path)
                finally:
                    if os.path.exists(temporary_name):
                        os.unlink(temporary_name)
                return {"ok": True, "access_token": state["api_token"]}
            finally:
                fcntl.flock(lock.fileno(), fcntl.LOCK_UN)

    @app.post("/api/tasks", response_model=TaskPayload, status_code=status.HTTP_201_CREATED)
    def create_task(payload: TaskPayload, request: Request):
        _validate_timestamp(payload)
        repository = store(request)
        if repository.get(payload.id, include_deleted=True) is not None:
            raise HTTPException(status_code=409, detail="task already exists")
        return repository.upsert(payload.model_dump(mode="json"))

    @app.get("/api/tasks/search", response_model=list[TaskPayload])
    def search_tasks(request: Request, q: str = Query(min_length=1, max_length=500)):
        query = "".join(character for character in q.casefold() if character.isalnum())
        matches = []
        for payload in store(request).searchable():
            folded, initials = _search_keys(payload["title"])
            if query in folded or _is_subsequence(query, folded) or _is_subsequence(query, initials):
                matches.append(payload)
        return matches

    @app.get("/api/tasks/{task_id}", response_model=TaskPayload)
    def get_task(task_id: str, request: Request):
        payload = store(request).get(task_id)
        if payload is None:
            raise HTTPException(status_code=404, detail="task not found")
        return payload

    @app.get("/api/tasks", response_model=list[TaskPayload])
    def list_tasks(
        request: Request,
        start_date: str | None = None,
        end_date: str | None = None,
        include_completed: bool = False,
    ):
        if start_date and end_date and end_date < start_date:
            raise HTTPException(status_code=422, detail="end_date must not precede start_date")
        return store(request).list(start_date, end_date, include_completed)

    @app.put("/api/tasks/{task_id}", response_model=TaskPayload)
    def update_task(task_id: str, payload: TaskPayload, request: Request):
        if task_id != payload.id:
            raise HTTPException(status_code=422, detail="path and payload IDs must match")
        _validate_timestamp(payload)
        repository = store(request)
        if repository.get(task_id, include_deleted=True) is None:
            raise HTTPException(status_code=404, detail="task not found")
        return repository.upsert(payload.model_dump(mode="json"))

    @app.delete("/api/tasks/{task_id}", response_model=TaskPayload)
    def delete_task(task_id: str, request: Request):
        payload = store(request).soft_delete(task_id)
        if payload is None:
            raise HTTPException(status_code=404, detail="task not found")
        return payload

    @app.post("/api/sync", response_model=SyncResponse)
    def sync_tasks(payload: SyncRequest, request: Request):
        for item in payload.tasks:
            _validate_timestamp(item)
        tasks = store(request).sync([item.model_dump(mode="json") for item in payload.tasks])
        server_time = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        return {"tasks": tasks, "server_time": server_time}

    return app


app = create_app()
