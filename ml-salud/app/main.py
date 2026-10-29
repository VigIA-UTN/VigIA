"""
Microservicio de Detección de Desvíos Sanitarios
=================================================
Reconstruido para trabajar por PERFIL INDIVIDUAL: a diferencia del modelo
de celo (evento puntual, comparado contra la población general), este
microservicio compara la ventana de telemetría a evaluar contra la línea
base propia de CADA animal, construida sobre un período de calibración de
7 días (requisito de negocio RF03; ver features_salud.py).

El modelo es un clasificador híbrido: las features de desvío (z-scores
respecto al perfil de calibración) se calculan con lógica determinística
en features_salud.py, y sobre esas features se entrena un
RandomForestClassifier (ver ml-training/salud/train_model.py). No se usa
una red neuronal: para esta tarea el ensamble de árboles es
sustancialmente más reproducible entre corridas (ver justificación
detallada en train_model.py) y no requiere TensorFlow en este servicio.

Dos endpoints, no uno solo (decisión de costo, ver features_salud.py):
  - POST /api/internal/ml/salud/perfil: recibe la telemetría cruda de
    calibración (>=7 días) UNA vez y devuelve el perfil ya calculado
    (unos ~20 números). El backend persiste ese resultado.
  - POST /api/internal/ml/salud/predict: recibe la ventana a evaluar (288
    puntos) MÁS el perfil ya calculado (no telemetría cruda) y devuelve
    la predicción. No recalcula el perfil en cada llamada.
Separarlos evita reenviar y recalcular ~2000 puntos de calibración en
cada ciclo del batch (que puede correr cada pocos segundos) cuando el
perfil no cambió desde la última vez.

Nota sobre el campo `perfil_referencia` de la versión anterior de este
microservicio: se eliminó por completo (no se documenta como "pendiente")
porque asumía datos de rumia que el dispositivo IoT de VigIA no emite.
"""

from fastapi import FastAPI
from pydantic import BaseModel, Field
from typing import Any, Dict, List, Optional
import math
import numpy as np
from pathlib import Path
import joblib

from .features_salud import (
    compute_baseline,
    compute_features,
    ErrorCalibracionInsuficiente,
    CALIB_DIAS_MINIMO,
    SAMPLES_PER_DAY,
    FEATURE_VERSION,
    N_QUARTERS,
)
from .correlacion import get_logger, instalar_middleware
from .errores import instalar_handlers, respuesta_error

log = get_logger("ml-salud")

app = FastAPI(title="VigIA - Microservicio de Detección de Desvíos Sanitarios")
instalar_middleware(app, log)
instalar_handlers(app, log)

model = None


@app.on_event("startup")
def load_ml_model():
    global model
    models_dir = Path(__file__).parent / "models"
    model_path = models_dir / "modelo_salud_rf.joblib"

    if model_path.exists():
        model = joblib.load(model_path)
        log.info("Modelo (RandomForest) cargado correctamente.")
    else:
        log.warning("No se encontró el modelo en %s.", model_path)


class PuntoTelemetria(BaseModel):
    # 0-9, según protocolo real de la caravana Lansitec.
    moveState: int = Field(ge=0, le=9)
    latitud: float = Field(ge=-90, le=90, allow_inf_nan=False)
    longitud: float = Field(ge=-180, le=180, allow_inf_nan=False)


class PerfilRequest(BaseModel):
    calibracion: List[PuntoTelemetria]


class PredictRequest(BaseModel):
    serie: List[PuntoTelemetria]
    perfil: Dict[str, Any]


class PrediccionSaludResponse(BaseModel):
    desvio_detectado: bool
    severidad: int
    posible_causa: Optional[str]
    confianza_modelo: float


# Claves del perfil que usa compute_features (ver compute_baseline en
# features_salud.py): un número por cada una de las escalares y una lista de
# N_QUARTERS números por cada una de las de cuartos.
_PERFIL_ESCALARES = [
    f"{feature}_{estadistico}"
    for feature in ("mean_moveState", "std_moveState", "pct_reposo", "mean_distancia", "std_distancia")
    for estadistico in ("mean", "std")
]
_PERFIL_CUARTOS = [
    "quarter_moveState_mean", "quarter_moveState_std",
    "quarter_distancia_mean", "quarter_distancia_std",
]


def _es_numero(valor) -> bool:
    return isinstance(valor, (int, float)) and not isinstance(valor, bool) and math.isfinite(valor)


def _problemas_de_entrada(body: PredictRequest) -> List[str]:
    """
    Lo que hace que la request no se pueda evaluar por culpa de los datos:
    se responde 422 (el backend no reintenta) y no 503, que es para fallas
    del servicio.
    """
    problemas = []
    if len(body.serie) != SAMPLES_PER_DAY:
        problemas.append(f"serie: debe tener exactamente {SAMPLES_PER_DAY} puntos (24 hs a 5 min); "
                         f"se recibieron {len(body.serie)}")
    if not body.perfil:
        problemas.append("perfil: no se recibió el perfil de referencia; "
                         "llamar primero a /api/internal/ml/salud/perfil")
        return problemas

    faltantes = [k for k in _PERFIL_ESCALARES + _PERFIL_CUARTOS if k not in body.perfil]
    if faltantes:
        problemas.append(f"perfil: faltan las claves {', '.join(faltantes)}; "
                         "recalcularlo con /api/internal/ml/salud/perfil")
    for k in _PERFIL_ESCALARES:
        if k in body.perfil and not _es_numero(body.perfil[k]):
            problemas.append(f"perfil.{k}: debe ser un número finito")
    for k in _PERFIL_CUARTOS:
        valor = body.perfil.get(k)
        if k in body.perfil and not (isinstance(valor, list) and len(valor) == N_QUARTERS
                                     and all(_es_numero(v) for v in valor)):
            problemas.append(f"perfil.{k}: debe ser una lista de {N_QUARTERS} números finitos")
    return problemas


def get_posible_causa(severidad: int) -> Optional[str]:
    causas = {
        0: None,
        1: "Enfermedad leve / Inicio",
        2: "Enfermedad moderada / Cojera clínica",
        3: "Problema severo postrante o complicación de parto"
    }
    return causas.get(severidad, "Desconocida")


@app.get("/health")
def health():
    return {"status": "ok", "service": "ml-salud", "feature_version": FEATURE_VERSION}


@app.post("/api/internal/ml/salud/perfil")
async def calcular_perfil(body: PerfilRequest):
    """
    Calcula el perfil de referencia individual (RF03) a partir de la
    telemetría cruda de calibración. Se llama UNA vez cuando el animal
    completa sus 7 días de calibración (o cuando el backend decide
    recalibrarlo); el resultado se persiste del lado del backend y se
    reenvía en cada /predict posterior en vez de la telemetría cruda.
    """
    log.info("Request de cálculo de perfil: calibracion=%d puntos.", len(body.calibracion))

    try:
        calib_move_state = [p.moveState for p in body.calibracion]
        calib_lat = [p.latitud for p in body.calibracion]
        calib_lon = [p.longitud for p in body.calibracion]

        perfil = compute_baseline(calib_move_state, calib_lat, calib_lon)

        log.info("Perfil calculado correctamente (%d días de calibración).", perfil["n_dias_calibracion"])
        return {"perfil": perfil}
    except ErrorCalibracionInsuficiente as e:
        log.warning("Calibración insuficiente: %s", e)
        return respuesta_error(422, "Calibracion Insuficiente", str(e),
                               calib_dias_minimo=CALIB_DIAS_MINIMO, samples_per_day=SAMPLES_PER_DAY)
    except Exception as e:
        log.error("Error calculando el perfil: %s", e)
        return respuesta_error(503, "Internal Server Error", str(e))


@app.post("/api/internal/ml/salud/predict", response_model=PrediccionSaludResponse)
async def predict(body: PredictRequest):
    """
    Request:
      - serie: ventana de 24hs (288 puntos, 1 cada 5 min) a evaluar.
      - perfil: perfil de referencia YA CALCULADO (ver /perfil) del mismo
        animal. No se recalcula acá -- si el backend necesita
        recalibrar, debe llamar primero a /perfil.
    """
    log.info("Request recibida: serie=%d puntos, perfil provisto (feature_version=%s).",
             len(body.serie), body.perfil.get("feature_version", "?"))

    problemas = _problemas_de_entrada(body)
    if problemas:
        mensaje = "; ".join(problemas)
        log.warning("Payload inválido: %s", mensaje)
        return respuesta_error(422, "Payload Invalido", mensaje)

    try:
        if model is None:
            raise ValueError("El modelo de machine learning no está cargado.")

        move_state = [p.moveState for p in body.serie]
        lat = [p.latitud for p in body.serie]
        lon = [p.longitud for p in body.serie]

        features = compute_features(move_state, lat, lon, body.perfil)

        X = np.array([features])
        proba = model.predict_proba(X)[0]
        clases = model.classes_

        idx_max = int(np.argmax(proba))
        severidad = int(clases[idx_max])
        confianza = float(proba[idx_max])

        desvio = severidad > 0
        causa = get_posible_causa(severidad)

        log.info("Inferencia exitosa: severidad=%d, confianza=%.4f", severidad, confianza)

        return PrediccionSaludResponse(
            desvio_detectado=desvio,
            severidad=severidad,
            posible_causa=causa,
            confianza_modelo=confianza
        )
    except Exception as e:
        # Los problemas de los datos (serie o perfil) ya salieron como 422
        # en _problemas_de_entrada; lo que llega acá es una falla del servicio.
        log.error("Error durante la inferencia: %s", e)
        return respuesta_error(503, "Internal Server Error", str(e))
