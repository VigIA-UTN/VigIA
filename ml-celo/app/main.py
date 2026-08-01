"""
Microservicio de Detección de Celo
Evalúa el aumento sostenido de actividad motora para diagnosticar
el inicio de un evento reproductivo (Documento de Arquitectura, sección 3.1).
"""

# pyrefly: ignore [missing-import]
from fastapi import FastAPI, Request, Response
# pyrefly: ignore [missing-import]
from fastapi.responses import JSONResponse
# pyrefly: ignore [missing-import]
from pydantic import BaseModel
from typing import List
import json
import math
# pyrefly: ignore [missing-import]
import numpy as np
from pathlib import Path

try:
    import tensorflow as tf
except ImportError:
    tf = None

app = FastAPI(title="VigIA - Microservicio de Detección de Celo")

model = None
norm_stats = None

@app.on_event("startup")
def load_ml_model():
    global model, norm_stats
    if tf is None:
        print("ADVERTENCIA: tensorflow no está instalado. No se podrá cargar el modelo.")
        return

    models_dir = Path(__file__).parent / "models"
    model_path = models_dir / "modelo_celo_lstm.keras"
    norm_path = models_dir / "normalizacion.json"
    
    if model_path.exists() and norm_path.exists():
        model = tf.keras.models.load_model(model_path)
        with open(norm_path, "r") as f:
            norm_stats = json.load(f)
        print("Modelo y estadísticas de normalización cargados correctamente.")
    else:
        print(f"ADVERTENCIA: No se encontró el modelo en {models_dir}. Ejecute el script de entrenamiento primero.")

class PuntoTelemetria(BaseModel):
    moveState: int
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
async def predict(request: Request, response: Response, serie: List[PuntoTelemetria]):
    """
    Request: serie temporal de la telemetría del animal de las últimas 24 horas.
    """
    correlation_id = request.headers.get("X-Correlation-ID", "unknown")
    print(f"[{correlation_id}] Request recibida para predicción de celo con {len(serie)} puntos.")
    response.headers["X-Correlation-ID"] = correlation_id
    
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
                dist = math.sqrt((lat2 - lat1)**2 + (lon2 - lon1)**2)
            features.append([float(ms), float(dist)])
            
        X_input = np.array([features])
        
        if norm_stats is not None:
            mean = np.array(norm_stats["mean"])
            std = np.array(norm_stats["std"])
            # Evitar división por cero
            std = np.where(std == 0, 1e-7, std)
            X_input = (X_input - mean) / std
            
        y_pred_prob = model.predict(X_input, verbose=0)
        prob = float(y_pred_prob[0][0])
        evento = prob > 0.5
        ventana = 12 if evento else 0
        
        print(f"[{correlation_id}] Inferencia exitosa: celo={evento}, confianza={prob:.4f}")
        
        return PrediccionCeloResponse(
            evento_celo=evento,
            confianza_modelo=prob,
            ventana_optima_horas=ventana
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
