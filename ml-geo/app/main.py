"""
Microservicio de Análisis Geoespacial (Pastoreo y Cohesión)
Compara el posicionamiento del animal para detectar anomalías (abigeato),
calcula el índice de aislamiento y las zonas subutilizadas del campo
(Documento de Arquitectura, sección 3.3).
"""

from fastapi import FastAPI
from pydantic import BaseModel
from typing import List

app = FastAPI(title="VigIA - Microservicio de Análisis Geoespacial")


class CoordenadaGPS(BaseModel):
    animal_id: str
    lat: float
    lng: float
    timestamp: str


class GeoAnalyzeRequest(BaseModel):
    historial_7d: List[CoordenadaGPS]


class AlertaAnimal(BaseModel):
    animal_id: str
    indice_cohesion: float
    alerta_aislamiento: bool


class MatrizDensidad(BaseModel):
    lat: float
    lng: float
    intensidad: float


class GeoAnalyzeResponse(BaseModel):
    mapa_calor: List[MatrizDensidad]
    alertas: List[AlertaAnimal]


@app.get("/health")
def health():
    return {"status": "ok", "service": "ml-geo"}


@app.post("/api/internal/ml/geo/analyze", response_model=GeoAnalyzeResponse)
def analyze(payload: GeoAnalyzeRequest):
    """
    Request: historial de coordenadas GPS (lat/lng) del rodeo
    en los últimos 7 días.
    """
    # TODO: reemplazar por el cálculo real de densidad y cohesión
    return GeoAnalyzeResponse(mapa_calor=[], alertas=[])
