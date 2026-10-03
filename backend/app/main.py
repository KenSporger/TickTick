from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import datetime, timezone
import os
from pathlib import Path
import re

from fastapi import FastAPI, HTTPException, Query, Request, status

from .schemas import SyncRequest, SyncResponse, TaskPayload
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


def create_app(database_path: str | Path | None = None) -> FastAPI:
    path = Path(database_path or os.environ.get("TICKTICK_DB_PATH", "backend/tasks.sqlite3"))

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        path.parent.mkdir(parents=True, exist_ok=True)
        app.state.store = TaskStore(path)
        yield

    app = FastAPI(title="TickTick personal API", lifespan=lifespan)

    def store(request: Request) -> TaskStore:
        return request.app.state.store

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
