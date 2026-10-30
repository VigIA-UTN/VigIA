"""
Microservicio de Análisis Geoespacial (Pastoreo y Cohesión)
Compara el posicionamiento del animal para detectar anomalías (abigeato),
calcula el mapa de calor de pastoreo y el índice de aislamiento
(Documento de Arquitectura, sección 3.3).
"""
# pyrefly: ignore [missing-import]
from fastapi import FastAPI
# pyrefly: ignore [missing-import]
from pydantic import BaseModel, Field
from typing import List, Optional
from collections import defaultdict, deque
from datetime import datetime, timezone
import math
import os

from .correlacion import get_logger, instalar_middleware
from .errores import instalar_handlers, respuesta_error

log = get_logger("ml-geo")

app = FastAPI(title="VigIA - Microservicio de Análisis Geoespacial")
instalar_middleware(app, log)
instalar_handlers(app, log)

class CoordenadaGPS(BaseModel):
    animal_id: str
    lat: float = Field(ge=-90, le=90, allow_inf_nan=False)
    lng: float = Field(ge=-180, le=180, allow_inf_nan=False)
    # Pydantic parsea ISO-8601 con o sin 'Z'/offset y rechaza el resto con 422.
    # En el cable sigue siendo el mismo string que manda el backend.
    timestamp: datetime

class GeoAnalyzeRequest(BaseModel):
    animal_id: Optional[str] = None
    # Animales a incluir en el mapa de calor (p. ej. los de un rodeo); None = todos. Las alertas usan siempre el historial completo.
    animal_ids: Optional[List[str]] = None
    historial_7d: List[CoordenadaGPS]
    # Umbral de aislamiento en metros para este análisis. Si no se envía, se usa el valor
    # por defecto configurado en la variable de entorno UMBRAL_AISLAMIENTO_METROS (100 m).
    # Debe ser > 0: con 0 el índice de cohesión de un animal pegado al grupo sería 0/0.
    umbral_aislamiento_m: Optional[float] = Field(default=None, gt=0)
    # Antigüedad máxima (s) de la última posición de un animal para clasificarlo ahora; si no se
    # envía, se usa VIGENCIA_POSICION_S. Ver analyze.
    vigencia_posicion_s: Optional[float] = Field(default=None, gt=0)

class AlertaAnimal(BaseModel):
    animal_id: str
    # Cohesión con el rodeo, 0..1 (ver _indice_cohesion)
    indice_cohesion: float
    # Distancia cruda al centroide del resto del rodeo, en metros
    distancia_centroide_m: float
    alerta_aislamiento: bool
    tiempo_fuera_grupo_s: float
    # Tramo final fuera del grupo sin volver (ver _compute_isolation_times)
    tiempo_fuera_continuo_s: float

class MatrizDensidad(BaseModel):
    lat: float
    lng: float
    intensidad: float

class GeoAnalyzeResponse(BaseModel):
    mapa_calor: List[MatrizDensidad]
    alertas: List[AlertaAnimal]

GRID_SIZE = 20  # Grilla de 20x20

# Umbral de aislamiento por defecto, en metros: distancia máxima entre dos
# animales para considerarlos parte del mismo grupo. El default de 100 m es del
# orden del diámetro de un rodeo en pastoreo. El backend envía el umbral de cada
# establecimiento en umbral_aislamiento_m; este valor solo se usa si no llega.
UMBRAL_AISLAMIENTO_METROS = float(os.getenv("UMBRAL_AISLAMIENTO_METROS", "100.0"))

# Antigüedad máxima, en segundos, de la última posición de un animal para usarla
# en la alerta, medida contra la posición más reciente del historial. Una
# caravana que dejó de transmitir no se compara con dónde está el rodeo ahora.
VIGENCIA_POSICION_S = float(os.getenv("VIGENCIA_POSICION_S", "1800"))

# Duración de los intervalos en que se agrupan las posiciones para medir el
# tiempo fuera del grupo, en segundos.
INTERVALO_COHESION_S = 300.0

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

def _indice_cohesion(distancia_m, umbral_m: float = None):
    """
    Cohesión con el rodeo en 0..1: 1.0 pegado al grupo, 0.5 exactamente sobre
    el umbral de aislamiento, y tendiendo a 0 a medida que el animal se aleja.

    Es una medida continua de la distancia al centroide de referencia (ver
    _clasificar); la alerta, en cambio, es pertenecer o no al grupo principal.
    En un rodeo compacto coinciden (indice < 0.5 <=> alerta), pero en uno
    estirado en fila un animal de la punta puede seguir enlazado al grupo
    (sin alerta) y estar a más del umbral del centroide (indice < 0.5).

    umbral_m: umbral efectivo para este análisis; si es None usa el global
    UMBRAL_AISLAMIENTO_METROS (valor de la variable de entorno).
    """
    u = umbral_m if umbral_m is not None else UMBRAL_AISLAMIENTO_METROS
    return u / (u + distancia_m)

def _centroide(posiciones):
    """Centroide (lat, lng) de una lista de pares (lat, lng)."""
    return (
        sum(lat for lat, _ in posiciones) / len(posiciones),
        sum(lng for _, lng in posiciones) / len(posiciones),
    )

def _grupo_principal(posiciones: dict, umbral_m: float):
    """
    Identifica el grupo principal del rodeo mediante BFS sobre el grafo de
    proximidad: dos animales están conectados si su distancia ≤ umbral_m.

    posiciones: animal_id -> (lat, lng).

    El grupo principal es el cluster más grande (mayor cantidad de animales).
    Si hay dos o más clusters del mismo tamaño máximo (empate), retorna None
    para indicar que no existe un grupo principal definido.

    Esta lógica cubre el criterio de aceptación:
    - Un subgrupo que se aleja junto queda FUERA del cluster principal y se
      lista como alejado (no como cohesionado).
    - Si el rodeo se divide, el grupo de mayor cantidad es el principal.
    """
    ids = list(posiciones)
    n = len(ids)
    if n == 0:
        return set()

    visited = [False] * n
    components = []

    for start in range(n):
        if visited[start]:
            continue
        component = []
        queue = deque([start])
        visited[start] = True
        while queue:
            curr = queue.popleft()
            component.append(curr)
            curr_lat, curr_lng = posiciones[ids[curr]]
            for other in range(n):
                if not visited[other]:
                    other_lat, other_lng = posiciones[ids[other]]
                    if _haversine(curr_lat, curr_lng, other_lat, other_lng) <= umbral_m:
                        visited[other] = True
                        queue.append(other)
        components.append(component)

    max_size = max(len(c) for c in components)
    largest = [c for c in components if len(c) == max_size]

    if len(largest) > 1:
        # Empate: sin grupo principal definido (p. ej. dos animales aislados
        # entre sí, o una división perfectamente pareja del rodeo).
        return None

    return {ids[i] for i in largest[0]}

def _clasificar(posiciones: dict, umbral_m: float) -> dict:
    """
    Única definición de "fuera del grupo", compartida por la alerta (última
    posición) y por el tiempo acumulado (cada intervalo).

    posiciones: animal_id -> (lat, lng).
    Devuelve animal_id -> (distancia_centroide_m, alejado):
    - Con grupo principal: distancia al centroide del grupo principal, y
      alejado = no pertenece al grupo principal.
    - En empate: distancia al centroide del resto y alejado = distancia > umbral,
      como antes de existir el grupo principal. Con una división pareja esto
      marca a todo el rodeo.
    - Con un solo animal no hay rodeo contra el que medir: (0.0, False).
      Sin animales devuelve {}.
    """
    if len(posiciones) <= 1:
        return {a_id: (0.0, False) for a_id in posiciones}

    principal = _grupo_principal(posiciones, umbral_m)
    resultado = {}
    if principal is None:
        for a_id, (lat, lng) in posiciones.items():
            c_lat, c_lng = _centroide([p for o_id, p in posiciones.items() if o_id != a_id])
            distancia = _haversine(lat, lng, c_lat, c_lng)
            resultado[a_id] = (distancia, distancia > umbral_m)
    else:
        c_lat, c_lng = _centroide([posiciones[a_id] for a_id in principal])
        for a_id, (lat, lng) in posiciones.items():
            resultado[a_id] = (_haversine(lat, lng, c_lat, c_lng), a_id not in principal)
    return resultado

def _compute_isolation_times(historial_7d: List[CoordenadaGPS], umbral_m: float, interval_s: float = INTERVALO_COHESION_S) -> tuple:
    """
    Devuelve (acumulado, continuo), ambos animal_id -> segundos fuera del grupo.

    Las caravanas no transmiten todas en el mismo instante, así que las
    posiciones se agrupan en intervalos de interval_s (5 min por defecto) y en
    cada uno se usa la posición promedio de cada animal. Un animal suma
    interval_s por cada intervalo en el que _clasificar lo marca alejado. Los
    intervalos en los que el animal no transmitió no suman (huecos sin datos).

    - acumulado: todos los intervalos alejados del historial.
    - continuo: el tramo final, contado hacia atrás desde el último intervalo
      con datos del animal hasta el primero en que estaba en el grupo. Los
      huecos sin datos no suman ni cortan el tramo.
    """
    intervalos = defaultdict(lambda: defaultdict(list))
    for p in historial_7d:
        interval_idx = int(_ts(p).timestamp() // interval_s)
        intervalos[interval_idx][p.animal_id].append((p.lat, p.lng))

    alejado_por_intervalo = defaultdict(dict)  # animal_id -> {interval_idx: alejado}
    for interval_idx, animales_en_intervalo in intervalos.items():
        pos_animal = {a_id: _centroide(puntos) for a_id, puntos in animales_en_intervalo.items()}
        for a_id, (_, alejado) in _clasificar(pos_animal, umbral_m).items():
            alejado_por_intervalo[a_id][interval_idx] = alejado

    acumulado = {}
    continuo = {}
    for a_id, por_intervalo in alejado_por_intervalo.items():
        acumulado[a_id] = interval_s * sum(por_intervalo.values())
        continuo[a_id] = 0.0
        for idx in sorted(por_intervalo, reverse=True):
            if not por_intervalo[idx]:
                break
            continuo[a_id] += interval_s

    return acumulado, continuo

@app.get("/health")
def health():
    return {"status": "ok", "service": "ml-geo"}

@app.post("/api/internal/ml/geo/analyze", response_model=GeoAnalyzeResponse)
def analyze(payload: GeoAnalyzeRequest):
    """
    Request: historial de coordenadas GPS (lat/lng) del rodeo en los últimos 7 días.

    El campo opcional ``umbral_aislamiento_m`` permite al backend enviar el umbral
    configurado para el establecimiento. Si se omite, se usa el valor por defecto
    de la variable de entorno UMBRAL_AISLAMIENTO_METROS (100 m).
    """
    # Umbral efectivo: el del request tiene prioridad; si no viene, se usa el global.
    umbral_efectivo = payload.umbral_aislamiento_m if payload.umbral_aislamiento_m is not None else UMBRAL_AISLAMIENTO_METROS
    log.info("Request recibida para análisis geoespacial con %d puntos (umbral=%s m).",
             len(payload.historial_7d), umbral_efectivo)

    try:
        if not payload.historial_7d:
            log.info("Historial vacío: se devuelve respuesta vacía.")
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

        # La alerta usa la última posición; el tiempo fuera del grupo, todo el
        # historial por intervalos. Ambos clasifican con _clasificar. Los animales
        # cuya última posición es vieja (caravana muda) quedan fuera de la
        # respuesta: el backend los trata como "sin datos en esta corrida".
        vigencia_s = payload.vigencia_posicion_s if payload.vigencia_posicion_s is not None else VIGENCIA_POSICION_S
        referencia = max(_ts(p) for p in ultimas_posiciones.values())
        posiciones = {
            a_id: (p.lat, p.lng)
            for a_id, p in ultimas_posiciones.items()
            if (referencia - _ts(p)).total_seconds() <= vigencia_s
        }
        iso_times, iso_continuo = _compute_isolation_times(payload.historial_7d, umbral_efectivo)
        alertas = [
            AlertaAnimal(
                animal_id=a_id,
                indice_cohesion=_indice_cohesion(distancia, umbral_efectivo),
                distancia_centroide_m=distancia,
                alerta_aislamiento=alejado,
                tiempo_fuera_grupo_s=iso_times.get(a_id, 0.0),
                tiempo_fuera_continuo_s=iso_continuo.get(a_id, 0.0),
            )
            for a_id, (distancia, alejado) in _clasificar(posiciones, umbral_efectivo).items()
        ]

        aisladas = sum(1 for a in alertas if a.alerta_aislamiento)
        log.info("Análisis exitoso: %d celdas, %d animales (%d aislados).",
                 len(mapa_calor), len(alertas), aisladas)

        return GeoAnalyzeResponse(mapa_calor=mapa_calor, alertas=alertas)

    except Exception as e:
        log.error("Error durante el análisis: %s", e)
        return respuesta_error(503, "Internal Server Error", str(e))
