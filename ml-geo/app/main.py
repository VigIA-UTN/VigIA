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
from typing import List, Optional
from collections import defaultdict
import math

app = FastAPI(title="VigIA - Microservicio de Análisis Geoespacial")

class CoordenadaGPS(BaseModel):
    animal_id: str
    lat: float
    lng: float
    timestamp: str

class GeoAnalyzeRequest(BaseModel):
    animal_id: Optional[str] = None
    # Animales a incluir en el mapa de calor (p. ej. los de un rodeo); None = todos. Las alertas usan siempre el historial completo.
    animal_ids: Optional[List[str]] = None
    historial_7d: List[CoordenadaGPS]

class AlertaAnimal(BaseModel):
    animal_id: str
    indice_cohesion: float
    alerta_aislamiento: bool

class MatrizDensidad(BaseModel):
    lat: float
    lng: float
    intensidad: float
    subutilizada: bool = False

class GeoAnalyzeResponse(BaseModel):
    mapa_calor: List[MatrizDensidad]
    alertas: List[AlertaAnimal]

GRID_SIZE = 20  # Grilla de 20x20
UMBRAL_SUBUTILIZADA = 0.15

def _grilla(puntos):
    """Bounding box de los puntos dividido en GRID_SIZE x GRID_SIZE: (min_lat, min_lng, lat_step, lng_step)."""
    min_lat = min(p.lat for p in puntos)
    max_lat = max(p.lat for p in puntos)
    min_lng = min(p.lng for p in puntos)
    max_lng = max(p.lng for p in puntos)
    # Manejar caso de un solo punto o puntos idénticos
    if min_lat == max_lat:
        min_lat -= 0.0001
        max_lat += 0.0001
    if min_lng == max_lng:
        min_lng -= 0.0001
        max_lng += 0.0001
    return min_lat, min_lng, (max_lat - min_lat) / GRID_SIZE, (max_lng - min_lng) / GRID_SIZE

def _intensidades(puntos, min_lat, min_lng, lat_step, lng_step):
    """Intensidad 0..1 (count / max_count) de cada celda de la grilla, como ((lat_idx, lng_idx), intensidad)."""
    counts = defaultdict(int)
    for p in puntos:
        # El máximo cae en el último bin
        lat_idx = min(int((p.lat - min_lat) / lat_step), GRID_SIZE - 1)
        lng_idx = min(int((p.lng - min_lng) / lng_step), GRID_SIZE - 1)
        counts[(lat_idx, lng_idx)] += 1
    max_count = max(counts.values()) if counts else 1
    return [((i, j), counts.get((i, j), 0) / max_count) for i in range(GRID_SIZE) for j in range(GRID_SIZE)]

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

    # Animales cuyo mapa de calor se pide; None = todos los del historial
    ids = payload.animal_ids
    if ids is None and payload.animal_id:
        ids = [payload.animal_id]
    puntos_mapa = payload.historial_7d if ids is None else [p for p in payload.historial_7d if p.animal_id in set(ids)]

    mapa_calor = []
    if puntos_mapa:
        # Mapa de calor: la grilla se ajusta a los puntos filtrados para no perder detalle
        min_lat, min_lng, lat_step, lng_step = _grilla(puntos_mapa)
        for (lat_idx, lng_idx), intensidad in _intensidades(puntos_mapa, min_lat, min_lng, lat_step, lng_step):
            mapa_calor.append(MatrizDensidad(
                lat=min_lat + (lat_idx + 0.5) * lat_step,
                lng=min_lng + (lng_idx + 0.5) * lng_step,
                intensidad=intensidad,
                subutilizada=intensidad <= UMBRAL_SUBUTILIZADA,
            ))

    # Umbral de aislamiento en metros. Configurable para alertar si un animal se aleja demasiado.
    UMBRAL_AISLAMIENTO_METROS = 100.0

    # Obtener la posición más reciente de cada animal
    ultimas_posiciones = {}
    for p in payload.historial_7d:
        # Se asume que el timestamp puede ordenarse lexicográficamente
        if p.animal_id not in ultimas_posiciones or p.timestamp > ultimas_posiciones[p.animal_id].timestamp:
            ultimas_posiciones[p.animal_id] = p

    animales = list(ultimas_posiciones.values())
    alertas = []

    def haversine(lat1, lon1, lat2, lon2):
        R = 6371000 # Radio de la Tierra en metros
        phi_1 = math.radians(lat1)
        phi_2 = math.radians(lat2)
        delta_phi = math.radians(lat2 - lat1)
        delta_lambda = math.radians(lon2 - lon1)
        
        a = math.sin(delta_phi / 2.0) ** 2 + math.cos(phi_1) * math.cos(phi_2) * math.sin(delta_lambda / 2.0) ** 2
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
        return R * c

    if len(animales) == 1:
        # Con un solo animal, no hay aislamiento posible respecto a un rodeo
        alertas.append(AlertaAnimal(
            animal_id=animales[0].animal_id,
            indice_cohesion=0.0,
            alerta_aislamiento=False
        ))
    elif len(animales) > 1:
        for a in animales:
            otros = [o for o in animales if o.animal_id != a.animal_id]
            avg_lat = sum(o.lat for o in otros) / len(otros)
            avg_lng = sum(o.lng for o in otros) / len(otros)
            
            distancia_centroide = haversine(a.lat, a.lng, avg_lat, avg_lng)
            
            alertas.append(AlertaAnimal(
                animal_id=a.animal_id,
                indice_cohesion=distancia_centroide,
                alerta_aislamiento=distancia_centroide > UMBRAL_AISLAMIENTO_METROS
            ))

    return GeoAnalyzeResponse(mapa_calor=mapa_calor, alertas=alertas)
