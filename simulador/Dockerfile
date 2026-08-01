FROM python:3.11-slim

WORKDIR /app

RUN pip install --no-cache-dir requests

COPY simulador.py .

CMD ["python", "-u", "simulador.py"]
