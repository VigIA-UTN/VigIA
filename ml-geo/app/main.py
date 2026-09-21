"""
Microservicio de Análisis Geoespacial (Pastoreo y Cohesión)
Compara el posicionamiento del animal para detectar anomalías (abigeato),
calcula el índice de aislamiento y las zonas subutilizadas del campo
(Documento de Arquitectura, sección 3.3).
"""
# pyrefly: ignore [missing-import]
from fastapi import FastAPI
# pyrefly: ignore [missing-import]
from pydantic import BaseModel
from typing import List
from collections import defaultdict

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
    if not payload.historial_7d:
        return GeoAnalyzeResponse(mapa_calor=[], alertas=[])

    # Encontrar límites (bounding box)
    min_lat = min(p.lat for p in payload.historial_7d)
    max_lat = max(p.lat for p in payload.historial_7d)
    min_lng = min(p.lng for p in payload.historial_7d)
    max_lng = max(p.lng for p in payload.historial_7d)

    # Manejar caso de un solo punto o puntos idénticos
    if min_lat == max_lat:
        min_lat -= 0.0001
        max_lat += 0.0001
    if min_lng == max_lng:
        min_lng -= 0.0001
        max_lng += 0.0001

    GRID_SIZE = 20 # Grilla de 20x20
    lat_step = (max_lat - min_lat) / GRID_SIZE
    lng_step = (max_lng - min_lng) / GRID_SIZE

    grid_counts = defaultdict(int)

    for p in payload.historial_7d:
        lat_idx = int((p.lat - min_lat) / lat_step)
        lng_idx = int((p.lng - min_lng) / lng_step)
        
        # Asegurar que el valor máximo caiga dentro del último bin
        if lat_idx == GRID_SIZE: lat_idx -= 1
        if lng_idx == GRID_SIZE: lng_idx -= 1
        
        grid_counts[(lat_idx, lng_idx)] += 1

    max_count = max(grid_counts.values()) if grid_counts else 1

    mapa_calor = []
    for (lat_idx, lng_idx), count in grid_counts.items():
        center_lat = min_lat + (lat_idx + 0.5) * lat_step
        center_lng = min_lng + (lng_idx + 0.5) * lng_step
        intensidad = count / max_count
        mapa_calor.append(MatrizDensidad(lat=center_lat, lng=center_lng, intensidad=intensidad))

    # TODO: reemplazar por el cálculo real de cohesión para las alertas
    return GeoAnalyzeResponse(mapa_calor=mapa_calor, alertas=[])
