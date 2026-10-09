FROM python:3.12-slim

WORKDIR /srv
COPY backend/requirements.txt /srv/requirements.txt
RUN pip install --no-cache-dir -r /srv/requirements.txt
COPY backend /srv/backend

ENV TICKTICK_DB_PATH=/data/tasks.sqlite3
VOLUME /data
EXPOSE 8200

CMD ["uvicorn", "backend.app.main:app", "--host", "0.0.0.0", "--port", "8200"]
