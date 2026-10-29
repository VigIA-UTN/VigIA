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

    try:
        if model is None:
            raise ValueError("El modelo de machine learning no está cargado.")

        if not body.serie:
            raise ValueError("La serie a evaluar está vacía.")

        if not body.perfil:
            raise ValueError("No se recibió un perfil de referencia. "
                              "Llamar primero a /api/internal/ml/salud/perfil.")

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
        # Un perfil malformado (ej. faltan claves porque vino de una
        # versión vieja de /perfil) también cae acá: compute_features
        # lanza KeyError/ValueError, y se reporta como 503 igual que
        # cualquier otra falla de inferencia -- no hay forma de servir
        # una predicción sin un perfil válido.
        log.error("Error durante la inferencia: %s", e)
        return respuesta_error(503, "Internal Server Error", str(e))
