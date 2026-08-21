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

Nota sobre el campo `perfil_referencia` de la versión anterior de este
microservicio: se eliminó por completo (no se documenta como "pendiente")
porque asumía datos de rumia que el dispositivo IoT de VigIA no emite.
Fue reemplazado por `perfil_calibracion`, que sí es telemetría real
(moveState + GPS) del propio animal.
"""

# pyrefly: ignore [missing-import]
from fastapi import FastAPI, Request, Response
# pyrefly: ignore [missing-import]
from fastapi.responses import JSONResponse
# pyrefly: ignore [missing-import]
from pydantic import BaseModel
from typing import List, Optional
# pyrefly: ignore [missing-import]
import numpy as np
from pathlib import Path
# pyrefly: ignore [missing-import]
import joblib

from .features_salud import (
    compute_baseline,
    compute_features,
    ErrorCalibracionInsuficiente,
    CALIB_DIAS_MINIMO,
    SAMPLES_PER_DAY,
)

app = FastAPI(title="VigIA - Microservicio de Detección de Desvíos Sanitarios")

model = None


@app.on_event("startup")
def load_ml_model():
    global model
    models_dir = Path(__file__).parent / "models"
    model_path = models_dir / "modelo_salud_rf.joblib"

    if model_path.exists():
        model = joblib.load(model_path)
        print("Modelo (RandomForest) cargado correctamente.")
    else:
        print(f"ADVERTENCIA: No se encontró el modelo en {model_path}.")


class PuntoTelemetria(BaseModel):
    moveState: int
    latitud: float
    longitud: float


class PredictRequest(BaseModel):
    serie: List[PuntoTelemetria]
    perfil_calibracion: List[PuntoTelemetria]


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
    return {"status": "ok", "service": "ml-salud"}


@app.post("/api/internal/ml/salud/predict", response_model=PrediccionSaludResponse)
async def predict(request: Request, response: Response, body: PredictRequest):
    """
    Request:
      - serie: ventana de 24hs (288 puntos, 1 cada 5 min) a evaluar.
      - perfil_calibracion: telemetría cruda de los últimos >= 7 días
        (RF03) del MISMO animal, usada para construir su línea base
        individual. El backend es responsable de no llamar a este
        endpoint para un animal que todavía no completó su período de
        calibración.
    """
    correlation_id = request.headers.get("X-Correlation-ID", "unknown")
    print(f"[{correlation_id}] Request recibida: serie={len(body.serie)} puntos, "
          f"perfil_calibracion={len(body.perfil_calibracion)} puntos.")
    response.headers["X-Correlation-ID"] = correlation_id

    try:
        if model is None:
            raise ValueError("El modelo de machine learning no está cargado.")

        if not body.serie:
            raise ValueError("La serie a evaluar está vacía.")

        move_state = [p.moveState for p in body.serie]
        lat = [p.latitud for p in body.serie]
        lon = [p.longitud for p in body.serie]

        calib_move_state = [p.moveState for p in body.perfil_calibracion]
        calib_lat = [p.latitud for p in body.perfil_calibracion]
        calib_lon = [p.longitud for p in body.perfil_calibracion]

        baseline = compute_baseline(calib_move_state, calib_lat, calib_lon)
        features = compute_features(move_state, lat, lon, baseline)

        X = np.array([features])
        proba = model.predict_proba(X)[0]
        clases = model.classes_

        idx_max = int(np.argmax(proba))
        severidad = int(clases[idx_max])
        confianza = float(proba[idx_max])

        desvio = severidad > 0
        causa = get_posible_causa(severidad)

        print(f"[{correlation_id}] Inferencia exitosa: severidad={severidad}, confianza={confianza:.4f}")

        return PrediccionSaludResponse(
            desvio_detectado=desvio,
            severidad=severidad,
            posible_causa=causa,
            confianza_modelo=confianza
        )
    except ErrorCalibracionInsuficiente as e:
        # Error de negocio (RF03), no una falla del servicio: el backend
        # no debería haber llamado a este endpoint todavía para este
        # animal. Se distingue con 422 del 503 de fallas internas.
        print(f"[{correlation_id}] Calibración insuficiente: {str(e)}")
        return JSONResponse(
            status_code=422,
            content={
                "error": "Calibracion Insuficiente",
                "mensaje": str(e),
                "calib_dias_minimo": CALIB_DIAS_MINIMO,
                "samples_per_day": SAMPLES_PER_DAY,
                "trace_id": correlation_id
            },
            headers={"X-Correlation-ID": correlation_id}
        )
    except Exception as e:
        print(f"[{correlation_id}] Error durante la inferencia: {str(e)}")
        return JSONResponse(
            status_code=503,
            content={
                "error": "Internal Server Error",
                "mensaje": str(e),
                "trace_id": correlation_id
            },
            headers={"X-Correlation-ID": correlation_id}
        )
