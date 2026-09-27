"""
Microservicio de Análisis Geoespacial (Pastoreo y Cohesión)
Compara el posicionamiento del animal para detectar anomalías (abigeato),
calcula el mapa de calor de pastoreo y el índice de aislamiento
(Documento de Arquitectura, sección 3.3).
"""
# pyrefly: ignore [missing-import]
from fastapi import FastAPI, Request, Response
# pyrefly: ignore [missing-import]
from fastapi.responses import JSONResponse
# pyrefly: ignore [missing-import]
from pydantic import BaseModel
from typing import List, Optional
from collections import defaultdict
from datetime import datetime, timezone
import math
import os

app = FastAPI(title="VigIA - Microservicio de Análisis Geoespacial")

class CoordenadaGPS(BaseModel):
    animal_id: str
    lat: float
    lng: float
    # Pydantic parsea ISO-8601 con o sin 'Z'/offset y rechaza el resto con 422.
    # En el cable sigue siendo el mismo string que manda el backend.
    timestamp: datetime

class GeoAnalyzeRequest(BaseModel):
    animal_id: Optional[str] = None
    # Animales a incluir en el mapa de calor (p. ej. los de un rodeo); None = todos. Las alertas usan siempre el historial completo.
    animal_ids: Optional[List[str]] = None
    historial_7d: List[CoordenadaGPS]

class AlertaAnimal(BaseModel):
    animal_id: str
    # Cohesión con el rodeo, 0..1 (ver _indice_cohesion)
    indice_cohesion: float
    # Distancia cruda al centroide del resto del rodeo, en metros
    distancia_centroide_m: float
    alerta_aislamiento: bool

class MatrizDensidad(BaseModel):
    lat: float
    lng: float
    intensidad: float

class GeoAnalyzeResponse(BaseModel):
    mapa_calor: List[MatrizDensidad]
    alertas: List[AlertaAnimal]

GRID_SIZE = 20  # Grilla de 20x20

# Distancia al centroide del resto del rodeo, en metros, a partir de la cual se
# considera que un animal quedó aislado. El default de 100 m es del orden del
# diámetro de un rodeo en pastoreo: más lejos que eso el animal dejó de seguir
# al grupo. Se ajusta por entorno mientras no sea configurable por establecimiento.
UMBRAL_AISLAMIENTO_METROS = float(os.getenv("UMBRAL_AISLAMIENTO_METROS", "100.0"))

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

def _ts(coordenada):
    """
    Timestamp comparable. Los naive se asumen UTC: mezclar aware y naive al
    comparar lanza TypeError, y el backend manda LocalDateTime sin offset.
    """
    t = coordenada.timestamp
    return t if t.tzinfo is not None else t.replace(tzinfo=timezone.utc)

def _haversine(lat1, lon1, lat2, lon2):
    R = 6371000  # Radio de la Tierra en metros
    phi_1 = math.radians(lat1)
    phi_2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = math.sin(delta_phi / 2.0) ** 2 + math.cos(phi_1) * math.cos(phi_2) * math.sin(delta_lambda / 2.0) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

def _indice_cohesion(distancia_m):
    """
    Cohesión con el rodeo en 0..1: 1.0 pegado al grupo, 0.5 exactamente sobre
    UMBRAL_AISLAMIENTO_METROS, y tendiendo a 0 a medida que el animal se aleja.
    Por construcción indice < 0.5 <=> distancia > UMBRAL_AISLAMIENTO_METROS, pero
    la alerta se deriva de la distancia para no depender de la igualdad de floats.
    """
    return UMBRAL_AISLAMIENTO_METROS / (UMBRAL_AISLAMIENTO_METROS + distancia_m)

@app.get("/health")
def health():
    return {"status": "ok", "service": "ml-geo"}

@app.post("/api/internal/ml/geo/analyze", response_model=GeoAnalyzeResponse)
def analyze(request: Request, response: Response, payload: GeoAnalyzeRequest):
    """
    Request: historial de coordenadas GPS (lat/lng) del rodeo
    en los últimos 7 días.
    """
    correlation_id = request.headers.get("X-Correlation-ID", "unknown")
    print(f"[{correlation_id}] Request recibida para análisis geoespacial con {len(payload.historial_7d)} puntos.")
    response.headers["X-Correlation-ID"] = correlation_id

    try:
        if not payload.historial_7d:
            print(f"[{correlation_id}] Historial vacío: se devuelve respuesta vacía.")
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
            mapa_calor = [
                MatrizDensidad(
                    lat=min_lat + (i + 0.5) * lat_step,
                    lng=min_lng + (j + 0.5) * lng_step,
                    intensidad=intensidad,
                )
                for (i, j), intensidad in _intensidades(puntos_mapa, min_lat, min_lng, lat_step, lng_step)
            ]

        # Obtener la posición más reciente de cada animal
        ultimas_posiciones = {}
        for p in payload.historial_7d:
            if p.animal_id not in ultimas_posiciones or _ts(p) > _ts(ultimas_posiciones[p.animal_id]):
                ultimas_posiciones[p.animal_id] = p

        animales = list(ultimas_posiciones.values())
        alertas = []

        if len(animales) == 1:
            # Con un solo animal, no hay aislamiento posible respecto a un rodeo
            alertas.append(AlertaAnimal(
                animal_id=animales[0].animal_id,
                indice_cohesion=_indice_cohesion(0.0),
                distancia_centroide_m=0.0,
                alerta_aislamiento=False
            ))
        elif len(animales) > 1:
            for a in animales:
                otros = [o for o in animales if o.animal_id != a.animal_id]
                avg_lat = sum(o.lat for o in otros) / len(otros)
                avg_lng = sum(o.lng for o in otros) / len(otros)

                distancia_centroide = _haversine(a.lat, a.lng, avg_lat, avg_lng)

                alertas.append(AlertaAnimal(
                    animal_id=a.animal_id,
                    indice_cohesion=_indice_cohesion(distancia_centroide),
                    distancia_centroide_m=distancia_centroide,
                    alerta_aislamiento=distancia_centroide > UMBRAL_AISLAMIENTO_METROS
                ))

        aisladas = sum(1 for a in alertas if a.alerta_aislamiento)
        print(f"[{correlation_id}] Análisis exitoso: {len(mapa_calor)} celdas, "
              f"{len(alertas)} animales ({aisladas} aislados).")

        return GeoAnalyzeResponse(mapa_calor=mapa_calor, alertas=alertas)

    except Exception as e:
        print(f"[{correlation_id}] Error durante el análisis: {str(e)}")
        return JSONResponse(
            status_code=503,
            content={
                "error": "Internal Server Error",
                "mensaje": str(e),
                "trace_id": correlation_id
            },
            headers={"X-Correlation-ID": correlation_id}
        )
