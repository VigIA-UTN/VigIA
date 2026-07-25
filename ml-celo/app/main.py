"""
Microservicio de Detección de Celo
Evalúa el aumento sostenido de actividad motora para diagnosticar
el inicio de un evento reproductivo (Documento de Arquitectura, sección 3.1).
"""

from fastapi import FastAPI
from pydantic import BaseModel
from typing import List

app = FastAPI(title="VigIA - Microservicio de Detección de Celo")

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
