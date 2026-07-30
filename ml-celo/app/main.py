"""
Microservicio de Detección de Celo
Evalúa el aumento sostenido de actividad motora para diagnosticar
el inicio de un evento reproductivo (Documento de Arquitectura, sección 3.1).
"""

from fastapi import FastAPI
from pydantic import BaseModel
from typing import List
import json
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
    x: float
    y: float
    z: float
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
def predict(serie: List[PuntoTelemetria]):
    """
    Request: serie temporal de la telemetría del animal de las últimas 24 horas.
    """
    # TODO: calcular la distancia euclidiana entre los puntos (lat, lon) de la serie
    # para reconstruir la distancia recorrida en cada intervalo, y luego inyectar 
    # la matriz final de (288, 5) a model.predict().
    return PrediccionCeloResponse(
        evento_celo=False,
        confianza_modelo=0.0,
        ventana_optima_horas=0,
    )
