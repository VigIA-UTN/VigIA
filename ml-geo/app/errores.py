"""
Formato común de las respuestas de error de los ml-*:
{"error", "mensaje", "trace_id"} más los campos extra de cada caso.

El 422 de validación de FastAPI trae solo {"detail": [...]}, con mensajes de
Pydantic en inglés y sin trace_id. Acá se reemplaza por el formato común, con
un `mensaje` legible ("serie[3].moveState: campo obligatorio") y el `detail`
original adentro para quien necesite el detalle de cada error.

Mismo archivo en ml-celo, ml-salud y ml-geo: cada servicio se buildea por
separado y no comparten código.
"""
import logging
import math

# pyrefly: ignore [missing-import]
from fastapi import FastAPI, Request
# pyrefly: ignore [missing-import]
from fastapi.encoders import jsonable_encoder
# pyrefly: ignore [missing-import]
from fastapi.exceptions import RequestValidationError
# pyrefly: ignore [missing-import]
from fastapi.responses import JSONResponse

from .correlacion import correlation_id_actual

# Mensajes de los tipos de error de Pydantic que puede generar el contrato de
# los ml-*. Los que no están acá usan el mensaje original de Pydantic.
_MENSAJES = {
    "missing": "campo obligatorio",
    "int_type": "debe ser un número entero",
    "int_parsing": "debe ser un número entero",
    "int_from_float": "debe ser un número entero",
    "float_type": "debe ser un número",
    "float_parsing": "debe ser un número",
    "finite_number": "debe ser un número finito",
    "string_type": "debe ser un texto",
    "bool_type": "debe ser verdadero o falso",
    "bool_parsing": "debe ser verdadero o falso",
    "list_type": "debe ser una lista",
    "dict_type": "debe ser un objeto",
    "model_type": "debe ser un objeto",
    "model_attributes_type": "debe ser un objeto",
    "datetime_type": "debe ser una fecha y hora ISO-8601",
    "datetime_parsing": "debe ser una fecha y hora ISO-8601",
    "datetime_from_date_parsing": "debe ser una fecha y hora ISO-8601",
    "json_invalid": "el cuerpo no es un JSON válido",
    "greater_than": "debe ser mayor que {gt}",
    "greater_than_equal": "debe ser mayor o igual que {ge}",
    "less_than": "debe ser menor que {lt}",
    "less_than_equal": "debe ser menor o igual que {le}",
}


MAX_ERRORES_EN_MENSAJE = 5


def respuesta_error(status_code: int, error: str, mensaje: str, **extra) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={"error": error, "mensaje": mensaje, **extra, "trace_id": correlation_id_actual()},
    )


def _campo(loc) -> str:
    """("body", "serie", 3, "moveState") -> "serie[3].moveState"; un body que es lista queda "body[0]..."."""
    partes = list(loc[1:]) if loc and loc[0] == "body" else list(loc)
    texto = "body" if partes and isinstance(partes[0], int) else ""
    for p in partes:
        if isinstance(p, int):
            texto += f"[{p}]"
        else:
            texto += f".{p}" if texto else str(p)
    return texto or "body"


def _json_seguro(valor):
    """
    El `input` de un error puede traer NaN/Infinity (Python los acepta en el
    JSON de entrada) y JSONResponse no los serializa: sin esto el 422 terminaba
    en un 500 sin formato ni X-Correlation-ID.
    """
    if isinstance(valor, float) and not math.isfinite(valor):
        return str(valor)
    if isinstance(valor, dict):
        return {k: _json_seguro(v) for k, v in valor.items()}
    if isinstance(valor, list):
        return [_json_seguro(v) for v in valor]
    return valor


def _describir(error: dict) -> str:
    if error.get("type") == "json_invalid":
        # Acá loc es ("body", posición del carácter), no un campo.
        return f"{_mensaje(error)} (posición {error.get('loc', (None, '?'))[-1]})"
    return f"{_campo(error.get('loc', ()))}: {_mensaje(error)}"


def _mensaje(error: dict) -> str:
    plantilla = _MENSAJES.get(error.get("type"))
    if plantilla is None:
        return error.get("msg", "valor inválido")
    try:
        return plantilla.format(**error.get("ctx", {}))
    except (KeyError, IndexError):
        return error.get("msg", "valor inválido")


def instalar_handlers(app: FastAPI, logger: logging.Logger) -> None:
    @app.exception_handler(RequestValidationError)
    async def payload_invalido(request: Request, exc: RequestValidationError):
        errores = exc.errors()
        # Una serie de 288 puntos con el mismo error daría 288 mensajes: van los primeros y la cuenta.
        mensaje = "; ".join(_describir(e) for e in errores[:MAX_ERRORES_EN_MENSAJE])
        if len(errores) > MAX_ERRORES_EN_MENSAJE:
            mensaje += f" (y {len(errores) - MAX_ERRORES_EN_MENSAJE} errores más)"
        logger.warning("Payload inválido: %s", mensaje)
        return respuesta_error(422, "Payload Invalido", mensaje, detail=_json_seguro(jsonable_encoder(errores)))
