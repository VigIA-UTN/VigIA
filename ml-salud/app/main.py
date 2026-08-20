"""
Microservicio de Detección de Desvíos Sanitarios
Evalúa la telemetría de las últimas 24 horas para diagnosticar la severidad de un problema de salud.
"""

from fastapi import FastAPI, Request, Response
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import List, Optional
import math
import numpy as np
from pathlib import Path
import joblib

try:
    import tensorflow as tf
except ImportError:
    tf = None

app = FastAPI(title="VigIA - Microservicio de Detección de Desvíos Sanitarios")

model = None
scaler = None

DIST_SCALE_FACTOR = 100000

@app.on_event("startup")
def load_ml_model():
    global model, scaler
    if tf is None:
        print("ADVERTENCIA: tensorflow no está instalado. No se podrá cargar el modelo.")
        return

    models_dir = Path(__file__).parent / "models"
    model_path = models_dir / "modelo_salud_lstm.keras"
    scaler_path = models_dir / "scaler_salud.joblib"

    if model_path.exists() and scaler_path.exists():
        model = tf.keras.models.load_model(model_path)
        scaler = joblib.load(scaler_path)
        print("Modelo y scaler cargados correctamente.")
    else:
        print(f"ADVERTENCIA: No se encontró el modelo o scaler en {models_dir}.")


class PuntoTelemetria(BaseModel):
    moveState: int
    latitud: float
    longitud: float

class PredictRequest(BaseModel):
    serie: List[PuntoTelemetria]
    perfil_referencia: Optional[dict] = None

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
    Request: serie temporal de la telemetría del animal de las últimas 24 horas y perfil de referencia.
    """
    correlation_id = request.headers.get("X-Correlation-ID", "unknown")
    print(f"[{correlation_id}] Request recibida para predicción de salud con {len(body.serie)} puntos.")
    response.headers["X-Correlation-ID"] = correlation_id

    try:
        if model is None or scaler is None:
            raise ValueError("El modelo de machine learning no está cargado.")

        if not body.serie:
            raise ValueError("La serie de telemetría está vacía.")

        features = []
        for i in range(len(body.serie)):
            ms = body.serie[i].moveState
            if i == 0:
                dist = 0.0
            else:
                lat1, lon1 = body.serie[i-1].latitud, body.serie[i-1].longitud
                lat2, lon2 = body.serie[i].latitud, body.serie[i].longitud
                dist = math.sqrt((lat2 - lat1)**2 + (lon2 - lon1)**2) * DIST_SCALE_FACTOR
            features.append([float(ms), float(dist)])

        X_input = np.array([features])

        # El scaler espera un array 2D, aplanamos, transformamos y volvemos a la forma original
        X_input_flat = X_input.reshape(-1, X_input.shape[-1])
        X_scaled_flat = scaler.transform(X_input_flat)
        X_input = X_scaled_flat.reshape(X_input.shape)

        y_pred_prob = model.predict(X_input, verbose=0)
        prob_array = y_pred_prob[0]
        
        severidad = int(np.argmax(prob_array))
        confianza = float(prob_array[severidad])
        
        desvio = severidad > 0
        causa = get_posible_causa(severidad)

        print(f"[{correlation_id}] Inferencia exitosa: severidad={severidad}, confianza={confianza:.4f}")

        return PrediccionSaludResponse(
            desvio_detectado=desvio,
            severidad=severidad,
            posible_causa=causa,
            confianza_modelo=confianza
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
