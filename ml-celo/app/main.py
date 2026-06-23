"""
Microservicio de Detección de Celo
Evalúa el aumento sostenido de actividad motora para diagnosticar
el inicio de un evento reproductivo (Documento de Arquitectura, sección 3.1).
"""

from fastapi import FastAPI
from pydantic import BaseModel
from typing import List

app = FastAPI(title="VigIA - Microservicio de Detección de Celo")


class AccelPoint(BaseModel):
    x: float
    y: float
    z: float
    timestamp: str


class PrediccionCeloResponse(BaseModel):
    evento_celo: bool
    confianza_modelo: float
    ventana_optima_horas: int


@app.get("/health")
def health():
    return {"status": "ok", "service": "ml-celo"}


@app.post("/api/internal/ml/celo/predict", response_model=PrediccionCeloResponse)
def predict(serie: List[AccelPoint]):
    """
    Request: serie temporal del vector de aceleración (x,y,z)
    de las últimas 24 horas del animal.
    """
    # TODO: reemplazar por la inferencia real del modelo entrenado
    return PrediccionCeloResponse(
        evento_celo=False,
        confianza_modelo=0.0,
        ventana_optima_horas=0,
    )
