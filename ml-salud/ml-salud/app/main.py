"""
Microservicio de Análisis de Salud
Compara la rumia y actividad actual del animal contra su perfil de
referencia calibrado para detectar desvíos anómalos (Documento de
Arquitectura, sección 3.2).
"""

from enum import Enum
from fastapi import FastAPI
from pydantic import BaseModel
from typing import List

app = FastAPI(title="VigIA - Microservicio de Análisis de Salud")


class EstadoSalud(str, Enum):
    NORMAL = "NORMAL"
    BORDE = "BORDE"
    CRITICO = "CRITICO"


class MetricaVentana(BaseModel):
    timestamp: str
    minutos_rumia: float
    nivel_actividad: float


class PerfilReferencia(BaseModel):
    animal_id: str
    rumia_promedio_historico: float
    actividad_promedio_historico: float


class SaludRequest(BaseModel):
    perfil_referencia: PerfilReferencia
    ventana_48h: List[MetricaVentana]


class PrediccionSaludResponse(BaseModel):
    estado_salud: EstadoSalud
    causa_probable: str


@app.get("/health")
def health():
    return {"status": "ok", "service": "ml-salud"}


@app.post("/api/internal/ml/salud/predict", response_model=PrediccionSaludResponse)
def predict(payload: SaludRequest):
    """
    Request: perfil de referencia histórico del animal y ventana
    de métricas de las últimas 48 horas.
    """
    # TODO: reemplazar por la inferencia real del modelo entrenado
    return PrediccionSaludResponse(
        estado_salud=EstadoSalud.NORMAL,
        causa_probable="",
    )
