FROM python:3.11-slim

WORKDIR /app

# Logs al momento en Docker/Railway (sin TTY el stdout de Python va con buffer).
ENV PYTHONUNBUFFERED=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app/ ./app

EXPOSE 8002

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8002"]
