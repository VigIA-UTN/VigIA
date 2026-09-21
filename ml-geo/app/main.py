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

    # Filtrar puntos para el mapa de calor si se especificó un animal
    puntos_mapa = payload.historial_7d
    if payload.animal_id:
        puntos_mapa = [p for p in payload.historial_7d if p.animal_id == payload.animal_id]

    for p in puntos_mapa:
        lat_idx = int((p.lat - min_lat) / lat_step)
        lng_idx = int((p.lng - min_lng) / lng_step)
        
        # Asegurar que el valor máximo caiga dentro del último bin
        if lat_idx == GRID_SIZE: lat_idx -= 1
        if lng_idx == GRID_SIZE: lng_idx -= 1
        
        grid_counts[(lat_idx, lng_idx)] += 1

    max_count = max(grid_counts.values()) if grid_counts else 1
    UMBRAL_SUBUTILIZADA = 0.15

    mapa_calor = []
    for lat_idx in range(GRID_SIZE):
        for lng_idx in range(GRID_SIZE):
            count = grid_counts.get((lat_idx, lng_idx), 0)
            center_lat = min_lat + (lat_idx + 0.5) * lat_step
            center_lng = min_lng + (lng_idx + 0.5) * lng_step
            intensidad = count / max_count
            es_subutilizada = intensidad <= UMBRAL_SUBUTILIZADA
            
            mapa_calor.append(MatrizDensidad(
                lat=center_lat, 
                lng=center_lng, 
                intensidad=intensidad,
                subutilizada=es_subutilizada
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
