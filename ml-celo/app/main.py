"""
Microservicio de Detección de Celo
Evalúa el aumento sostenido de actividad motora para diagnosticar
el inicio de un evento reproductivo 
"""

# pyrefly: ignore [missing-import]
from fastapi import FastAPI
# pyrefly: ignore [missing-import]
from fastapi.responses import JSONResponse
# pyrefly: ignore [missing-import]
from pydantic import BaseModel
from typing import List
import math
import os
# pyrefly: ignore [missing-import]
import numpy as np
# pyrefly: ignore [missing-import]
import joblib
from pathlib import Path
from contextlib import asynccontextmanager

from .correlacion import correlation_id_actual, get_logger, instalar_middleware

try:
    import tensorflow as tf
except ImportError:
    tf = None

model = None
scaler = None

log = get_logger("ml-celo")

# Debe coincidir con la escala usada en generate_dataset.py / train_baseline_rnn.py
DIST_SCALE_FACTOR = 100000

# Umbral de decisión sobre la probabilidad del modelo (P(celo) > CELO_THRESHOLD).
# Se saca a config en lugar de hardcodear 0.5 porque, con class_weight balanceado,
# el umbral que maximiza F1 sobre la curva Precision-Recall NO es 0.5 (ver
# run_summary.json de la corrida elegida en ml-training/celo/models/.../run_summary.json,
# campo "best_threshold_f1"). Default 0.5 es un fallback conservador si no se
# configura nada; en producción se espera CELO_THRESHOLD seteado explícitamente.
CELO_THRESHOLD = float(os.getenv("CELO_THRESHOLD", "0.5"))

@asynccontextmanager
async def lifespan(app: FastAPI):
    global model, scaler
    if tf is None:
        log.warning("tensorflow no está instalado. No se podrá cargar el modelo.")
    else:
        models_dir = Path(__file__).parent / "models"
        model_path = models_dir / "modelo_celo_lstm.keras"
        scaler_path = models_dir / "scaler_celo.joblib"

        if model_path.exists() and scaler_path.exists():
            model = tf.keras.models.load_model(model_path)
            scaler = joblib.load(scaler_path)
            log.info("Modelo y scaler de normalización cargados correctamente. "
                     "Umbral de decisión (CELO_THRESHOLD) = %s", CELO_THRESHOLD)
        else:
            log.warning("No se encontró el modelo en %s. Ejecute el script de entrenamiento primero.", models_dir)
    yield

app = FastAPI(title="VigIA - Microservicio de Detección de Celo", lifespan=lifespan)
instalar_middleware(app, log)

class PuntoTelemetria(BaseModel):
    moveState: int  # 0-9, según protocolo real de la caravana Lansitec
    latitud: float
    longitud: float

class PrediccionCeloResponse(BaseModel):
    evento_celo: bool
    confianza_modelo: float
    ventana_optima_horas: int

@app.get("/health")
def health():
    return {"status": "ok", "service": "ml-celo"}

@app.post("/api/internal/ml/celo/predict", response_model=PrediccionCeloResponse)
async def predict(serie: List[PuntoTelemetria]):
    """
    Request: serie temporal de la telemetría del animal de las últimas 24 horas.
    """
    log.info("Request recibida para predicción de celo con %d puntos.", len(serie))

    try:
        if model is None:
            raise ValueError("El modelo de machine learning no está cargado.")

        if not serie:
            raise ValueError("La serie de telemetría está vacía.")

        features = []
        for i in range(len(serie)):
            ms = serie[i].moveState
            if i == 0:
                dist = 0.0
            else:
                lat1, lon1 = serie[i-1].latitud, serie[i-1].longitud
                lat2, lon2 = serie[i].latitud, serie[i].longitud
                # Misma escala que en generate_dataset.py / train_baseline_rnn.py:
                # sin este factor, la distancia queda ~100000 veces más chica
                # que lo que el modelo aprendió y se pierde toda la señal de
                # movimiento al normalizar.
                dist = math.sqrt((lat2 - lat1)**2 + (lon2 - lon1)**2) * DIST_SCALE_FACTOR
            features.append([float(ms), float(dist)])

        X_input = np.array([features])

        if scaler is not None:
            X_input = scaler.transform(
                X_input.reshape(-1, X_input.shape[-1])
            ).reshape(X_input.shape)

        y_pred_prob = model.predict(X_input, verbose=0)
        prob = float(y_pred_prob[0][0])
        evento = prob > CELO_THRESHOLD
        ventana = 12 if evento else 0

        log.info("Inferencia exitosa: celo=%s, confianza=%.4f", evento, prob)

        return PrediccionCeloResponse(
            evento_celo=evento,
            confianza_modelo=prob,
            ventana_optima_horas=ventana
        )
    except Exception as e:
        log.error("Error durante la inferencia: %s", e)
        return JSONResponse(
            status_code=503,
            content={
                "error": "Internal Server Error",
                "mensaje": str(e),
                "trace_id": correlation_id_actual()
            }
        )