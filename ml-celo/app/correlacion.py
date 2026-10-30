"""
X-Correlation-ID y logging del microservicio.

El middleware toma el X-Correlation-ID que manda el backend, lo deja en un
contextvar para que cada línea de log lo incluya y lo devuelve en TODAS las
respuestas, incluidos los 422 de validación, que FastAPI arma antes de llegar
al endpoint.

Se loguea con `logging` y no con print: sin TTY (Docker/Railway) el stdout de
Python va con buffer y los print pueden aparecer tarde o perderse si el
proceso se cae; cada registro de logging se escribe al momento.

Mismo archivo en ml-celo, ml-salud y ml-geo: cada servicio se buildea por
separado y no comparten código.
"""
import logging
import os
from contextvars import ContextVar

# pyrefly: ignore [missing-import]
from fastapi import FastAPI, Request

HEADER = "X-Correlation-ID"
SIN_CORRELATION_ID = "unknown"

_correlation_id: ContextVar[str] = ContextVar("correlation_id", default=SIN_CORRELATION_ID)

FORMATO_LOG = "%(asctime)s %(levelname)s [%(correlation_id)s] %(name)s: %(message)s"


def correlation_id_actual() -> str:
    return _correlation_id.get()


class _CorrelationIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.correlation_id = _correlation_id.get()
        return True


def get_logger(nombre: str) -> logging.Logger:
    logger = logging.getLogger(nombre)
    if not logger.handlers:
        handler = logging.StreamHandler()
        handler.setFormatter(logging.Formatter(FORMATO_LOG))
        logger.addHandler(handler)
        logger.addFilter(_CorrelationIdFilter())
        logger.setLevel(os.getenv("LOG_LEVEL", "INFO"))
    return logger


def instalar_middleware(app: FastAPI, logger: logging.Logger) -> None:
    @app.middleware("http")
    async def correlation_id_middleware(request: Request, call_next):
        token = _correlation_id.set(request.headers.get(HEADER, SIN_CORRELATION_ID))
        try:
            response = await call_next(request)
            # El health lo consulta el healthcheck cada pocos segundos: no se loguea.
            if request.url.path != "/health":
                logger.info("%s %s -> %s", request.method, request.url.path, response.status_code)
            response.headers[HEADER] = _correlation_id.get()
            return response
        finally:
            _correlation_id.reset(token)
