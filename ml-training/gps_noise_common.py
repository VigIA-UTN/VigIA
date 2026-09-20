"""
gps_noise_common.py
====================
Lógica compartida entre ml-training/celo/generate_dataset.py y
ml-training/salud/generate_dataset.py para simular ruido de medición GPS
y la granularidad real de moveState (ver investigación de causa raíz:
un collar real, aunque esté físicamente quieto, produce un ruido de
posición GNSS de ~15m de desvío estándar por eje entre fixes
consecutivos, muy por encima del desplazamiento sintético que ambos
generadores producían -- eso hacía que el modelo de celo interpretara
ese ruido como actividad extrema).

A diferencia de ml-celo/ml-salud (imágenes Docker independientes que sí
duplican a propósito lógica como distancias_desde_coords), celo/ y
salud/ dentro de ml-training comparten el mismo requirements.txt/venv,
así que esta lógica vive en un solo lugar.
"""

import numpy as np

METERS_PER_DEGREE = 111_320.0  # aprox. WGS84 ecuatorial; ver nota en el
# docstring de inject_gps_noise sobre por qué no se corrige por cos(lat).

# Derivado empíricamente de una muestra real de 12 lecturas consecutivas
# de un collar físicamente quieto: distancias entre fixes consecutivos
# (unidades "escaladas" = grados*100000, mismo criterio que
# DIST_SCALE_FACTOR en ml-celo/app/main.py y ml-salud/app/features_salud.py)
# = [37.4, 20.3, 4.27, 31.6, 15.19, 16.63, 24.8, 37.3, 33.4, 23.5, 27.6],
# media ~24.7. Asumiendo ruido gaussiano i.i.d. por eje, la distancia
# esperada entre dos fixes ruidosos es ~1.772*sigma (ver RAYLEIGH_MEAN_FACTOR
# en calibracion_dataset.py), lo que da sigma ~13.9 unidades escaladas
# ~1.39e-4 grados ~15.5 metros por eje.
DEFAULT_GPS_NOISE_STD_M = 15.5

# Cadencia real prevista de heartbeat (que trae moveState): 30 minutos,
# o sea 6 ticks de 5 minutos. Se deja fijo (no aleatorizado) a pedido: se
# prioriza que el dataset sintético sea lo más fiel posible a la
# cadencia real prevista. Si esa cadencia cambia en producción, hay que
# regenerar el dataset y reentrenar -- ver
# ml-training/RUNBOOK_CAMBIO_HEARTBEAT.md.
DEFAULT_HEARTBEAT_INTERVAL_TICKS = 6


def inject_gps_noise(lat: np.ndarray, lon: np.ndarray, std_m: float, rng: np.random.Generator):
    """Ruido de medición GNSS i.i.d. por tick y por eje, aplicado SOBRE la
    trayectoria 'verdadera' ya calculada por el random walk (nunca dentro
    del loop): inyectarlo tick a tick durante la simulación lo
    autocorrelacionaría a través del término de cohesión hacia el centro
    del rodeo y del heading persistente, lo que contradice la asunción de
    ruido independiente usada para derivar std_m arriba.

    No se corrige la asimetría real lat/lon por cos(latitud): el resto
    del pipeline (SPEED_BASE del random walk, DIST_SCALE_FACTOR en los
    microservicios) ya trata lat/lon de forma simétrica, así que corregir
    sólo el ruido introduciría una inconsistencia nueva en vez de
    arreglar una existente.

    Devuelve (lat_obs, lon_obs); lat/lon de entrada (trayectoria
    'verdadera', la que determina las labels) quedan sin modificar.
    """
    if std_m <= 0:
        return lat.copy(), lon.copy()
    sigma_deg = std_m / METERS_PER_DEGREE
    lat_obs = lat + rng.normal(0.0, sigma_deg, size=lat.shape).astype(lat.dtype)
    lon_obs = lon + rng.normal(0.0, sigma_deg, size=lon.shape).astype(lon.dtype)
    return lat_obs, lon_obs


def quantize_movestate(move_state: np.ndarray, heartbeat_interval_ticks: int):
    """Reduce moveState de 'un valor fresco por tick' a 'constante dentro
    de cada bloque de heartbeat_interval_ticks', imitando que en
    producción el valor solo puede cambiar cuando llega un Heartbeat real
    (ver el forward-fill de moveState en
    backend/backend-worker/.../controller/IngestaController.java, que ya
    propaga el último valor conocido a cada posición intermedia). Sin
    este ajuste, el dataset sintético entrena al modelo con un moveState
    que cambia cada 5 minutos, algo que nunca va a ver en producción.

    Opera como post-proceso sobre move_state ya completamente calculado
    (con todos los efectos de circadiano/intensidad/picos ya aplicados):
    toma el valor del primer tick de cada bloque y lo repite en el resto
    del bloque. move_state tiene shape (n_animales, T).
    """
    if heartbeat_interval_ticks <= 1:
        return move_state
    n_animales, T = move_state.shape
    block_idx = np.arange(T) // heartbeat_interval_ticks
    anchor_tick = np.minimum(block_idx * heartbeat_interval_ticks, T - 1)
    return move_state[:, anchor_tick]
