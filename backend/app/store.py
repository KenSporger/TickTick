from __future__ import annotations

from datetime import datetime, timezone
import json
from pathlib import Path
import sqlite3
from threading import RLock
from typing import Any


def parse_updated_at(value: str) -> datetime:
    normalized = value[:-1] + "+00:00" if value.endswith("Z") else value
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        raise ValueError("updated_at must include a timezone")
    return parsed.astimezone(timezone.utc)


class TaskStore:
    """Small SQLite repository with serialized writes for the single-user service."""

    def __init__(self, path: str | Path):
        self.path = str(path)
        self._lock = RLock()
        with self._connect() as connection:
            connection.execute("PRAGMA journal_mode=WAL")
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    payload TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    updated_epoch REAL NOT NULL,
                    status TEXT NOT NULL,
                    start_date TEXT,
                    end_date TEXT
                )
                """
            )

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.path, timeout=10)
        connection.row_factory = sqlite3.Row
        return connection

    @staticmethod
    def _decode(row: sqlite3.Row) -> dict[str, Any]:
        return json.loads(row["payload"])

    def get(self, task_id: str, include_deleted: bool = False) -> dict[str, Any] | None:
        with self._connect() as connection:
            row = connection.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
        if row is None or (row["status"] == "DELETED" and not include_deleted):
            return None
        return self._decode(row)

    def upsert(self, payload: dict[str, Any], only_if_newer: bool = False) -> dict[str, Any]:
        incoming_epoch = parse_updated_at(payload["updated_at"]).timestamp()
        encoded = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
        with self._lock, self._connect() as connection:
            existing = connection.execute(
                "SELECT payload, updated_epoch FROM tasks WHERE id = ?", (payload["id"],)
            ).fetchone()
            if only_if_newer and existing is not None and incoming_epoch <= existing["updated_epoch"]:
                return json.loads(existing["payload"])
            connection.execute(
                """
                INSERT INTO tasks(id, payload, updated_at, updated_epoch, status, start_date, end_date)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    payload=excluded.payload, updated_at=excluded.updated_at,
                    updated_epoch=excluded.updated_epoch, status=excluded.status,
                    start_date=excluded.start_date, end_date=excluded.end_date
                """,
                (
                    payload["id"], encoded, payload["updated_at"], incoming_epoch,
                    payload["status"], payload.get("start_date"), payload.get("end_date"),
                ),
            )
        return payload

    def list(
        self,
        start_date: str | None = None,
        end_date: str | None = None,
        include_completed: bool = False,
    ) -> list[dict[str, Any]]:
        conditions = ["status != 'DELETED'"]
        arguments: list[Any] = []
        if not include_completed:
            conditions.append("status NOT IN ('COMPLETED', 'SKIPPED')")
        if start_date is not None:
            conditions.append("end_date >= ?")
            arguments.append(start_date)
        if end_date is not None:
            conditions.append("start_date <= ?")
            arguments.append(end_date)
        query = "SELECT * FROM tasks WHERE " + " AND ".join(conditions) + " ORDER BY id"
        with self._connect() as connection:
            rows = connection.execute(query, arguments).fetchall()
        return [self._decode(row) for row in rows]

    def soft_delete(self, task_id: str) -> dict[str, Any] | None:
        with self._lock:
            payload = self.get(task_id)
            if payload is None:
                return None
            payload["status"] = "DELETED"
            payload["updated_at"] = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
            return self.upsert(payload)

    def sync(self, incoming: list[dict[str, Any]]) -> list[dict[str, Any]]:
        # Sorting removes request-order dependence when duplicate IDs are supplied.
        ordered = sorted(
            incoming,
            key=lambda item: (
                item["id"],
                parse_updated_at(item["updated_at"]),
                json.dumps(item, ensure_ascii=False, sort_keys=True, separators=(",", ":")),
            ),
        )
        for payload in ordered:
            self.upsert(payload, only_if_newer=True)
        with self._connect() as connection:
            rows = connection.execute("SELECT * FROM tasks ORDER BY id").fetchall()
        return [self._decode(row) for row in rows]

    def searchable(self) -> list[dict[str, Any]]:
        with self._connect() as connection:
            rows = connection.execute(
                "SELECT * FROM tasks WHERE status != 'DELETED' ORDER BY id"
            ).fetchall()
        return [self._decode(row) for row in rows]
