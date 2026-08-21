"""
features_salud.py
==================
Extracción de features de DESVÍO respecto al perfil individual de cada
animal, para el modelo híbrido (features estadísticas + clasificador) de
detección de problemas de salud.

Este módulo es el ÚNICO lugar donde vive esta lógica. Tanto el
entrenamiento (ml-training/salud/train_model.py) como el servicio de
inferencia (ml-salud/app/main.py) usan este archivo textualmente
idéntico -- no se reimplementa en los dos lugares -- para eliminar el
riesgo de que el preprocesamiento de entrenamiento y el de producción
diverjan silenciosamente (skew entrenamiento/serving). El archivo se
COPIA (no se referencia por path relativo/símlink) a ml-salud/app/ al
final de train_model.py, porque ml-salud y ml-training son builds/
imágenes Docker independientes y no comparten filesystem en producción.
Si se edita esta lógica, hay que volver a correr train_model.py (o copiar
el archivo a mano) para que ml-salud/app/features_salud.py quede
sincronizado -- de lo contrario el modelo servido seguiría viendo el
preprocesamiento viejo. Un test de regresión que compare el hash de
ambos archivos sería una mejora natural de CI (ver limitación equivalente
para train_rnn.py/main.py en el modelo de celo, que ya duplicaba el
cálculo de distancia antes de esta reconstrucción).

Diseño (por qué NO se usa hora-del-día como en el simulador de celo):
la API no recibe timestamps por punto, solo listas ordenadas de
telemetría a intervalos de 5 minutos. En vez de exigir timestamps (un
cambio de contrato más invasivo), este módulo compara ventanas
COMPLETAS de 24hs (288 puntos) contra otras ventanas completas de 24hs
del período de calibración: al ser ambas de 24hs, el efecto del ritmo
circadiano (más actividad de día, menos de noche) se cancela igual en
ambas, así que comparar agregados diarios sigue siendo una comparación
"como corresponde" sin necesitar alinear por hora real del día.

Requisito de negocio (RF03): el perfil de referencia se calcula sobre
un período de calibración de exactamente CALIB_DIAS_MINIMO = 7 días. Se
exige un mínimo de CALIB_DIAS_MINIMO * SAMPLES_PER_DAY puntos de
calibración; con menos, no hay perfil de referencia válido y no
corresponde predecir (ver ErrorCalibracionInsuficiente en main.py).

Persistencia del perfil (decisión de costo/arquitectura): compute_baseline()
se invoca UNA sola vez por parte de main.py (endpoint /perfil), y el
backend persiste el resultado en vez de reenviar la telemetría cruda de
calibración en cada predicción -- reenviar y recalcular ~2000 puntos en
cada llamada de un batch que puede correr cada pocos segundos es
desperdicio de red y de CPU sin ningún beneficio de precisión (la función
es determinística: mismos puntos de entrada, mismo resultado siempre).
FEATURE_VERSION se incluye en el perfil devuelto como metadato informativo
para poder diagnosticar manualmente si un perfil persistido quedó
calculado con una versión vieja de esta lógica; no hay chequeo de versión
automático en /predict en esta iteración -- el mecanismo real para que un
perfil no quede desactualizado para siempre es la recalibración periódica
que corresponde implementar en el backend (ver RECALIBRACION_DIAS en
SaludDetectorStrategyImpl), no una validación estricta acá.
"""

FEATURE_VERSION = "v1"

import math
from typing import List, Sequence, Tuple

import numpy as np

SAMPLES_PER_DAY = 288  # 1 lectura cada 5 minutos
CALIB_DIAS_MINIMO = 7  # RF03
CALIB_PUNTOS_MINIMO = CALIB_DIAS_MINIMO * SAMPLES_PER_DAY
DIST_SCALE_FACTOR = 100000
N_QUARTERS = 4  # bloques de 6hs dentro de cada ventana de 24hs (72 puntos c/u)
EPS = 1e-6

# Orden fijo del vector de features. Entrenamiento y serving DEBEN
# recorrer esta misma lista en el mismo orden.
FEATURE_NAMES = [
    "mean_moveState",
    "std_moveState",
    "pct_reposo",
    "pct_alta_actividad",
    "mean_distancia",
    "std_distancia",
    "z_mean_moveState",
    "z_std_moveState",
    "z_pct_reposo",
    "z_mean_distancia",
    "z_std_distancia",
    "max_abs_z_quarter_moveState",
    "max_abs_z_quarter_distancia",
]


class ErrorCalibracionInsuficiente(ValueError):
    """La serie de calibración no cubre los CALIB_DIAS_MINIMO exigidos por RF03."""
    pass


def distancias_desde_coords(lat: Sequence[float], lon: Sequence[float]) -> np.ndarray:
    """Distancia euclídea entre puntos GPS consecutivos, escalada (mismo
    criterio que el modelo de celo: no es una distancia geodésica real,
    es un proxy relativo consistente entre calibración y evaluación)."""
    lat = np.asarray(lat, dtype=np.float64)
    lon = np.asarray(lon, dtype=np.float64)
    dist = np.zeros(len(lat), dtype=np.float64)
    if len(lat) > 1:
        d = np.sqrt(np.diff(lat) ** 2 + np.diff(lon) ** 2) * DIST_SCALE_FACTOR
        dist[1:] = d
    return dist


def _split_full_days(move_state: np.ndarray, dist: np.ndarray) -> List[Tuple[np.ndarray, np.ndarray]]:
    """Parte una serie en ventanas completas de 288 puntos, descartando el
    resto final si no completa un día (mismo criterio que el simulador y
    que train_rnn.py original: días incompletos se descartan)."""
    n_dias = len(move_state) // SAMPLES_PER_DAY
    dias = []
    for i in range(n_dias):
        s, e = i * SAMPLES_PER_DAY, (i + 1) * SAMPLES_PER_DAY
        dias.append((move_state[s:e], dist[s:e]))
    return dias


def _day_aggregates(move_state_day: np.ndarray, dist_day: np.ndarray) -> dict:
    return {
        "mean_moveState": float(np.mean(move_state_day)),
        "std_moveState": float(np.std(move_state_day)),
        "pct_reposo": float(np.mean(move_state_day <= 1)),
        "pct_alta_actividad": float(np.mean(move_state_day >= 7)),
        "mean_distancia": float(np.mean(dist_day)),
        "std_distancia": float(np.std(dist_day)),
    }


def _quarter_means(move_state_day: np.ndarray, dist_day: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
    q_len = SAMPLES_PER_DAY // N_QUARTERS
    ms_q = np.array([move_state_day[i * q_len:(i + 1) * q_len].mean() for i in range(N_QUARTERS)])
    dist_q = np.array([dist_day[i * q_len:(i + 1) * q_len].mean() for i in range(N_QUARTERS)])
    return ms_q, dist_q


def compute_baseline(calib_move_state: Sequence[float], calib_lat: Sequence[float],
                      calib_lon: Sequence[float]) -> dict:
    """Construye el perfil de referencia individual (RF03) a partir de la
    serie cruda del período de calibración. Lanza ErrorCalibracionInsuficiente
    si no hay al menos CALIB_DIAS_MINIMO días completos."""
    move_state = np.asarray(calib_move_state, dtype=np.float64)
    dist = distancias_desde_coords(calib_lat, calib_lon)

    dias = _split_full_days(move_state, dist)
    if len(dias) < CALIB_DIAS_MINIMO:
        raise ErrorCalibracionInsuficiente(
            f"Se requieren al menos {CALIB_DIAS_MINIMO} días completos de calibración "
            f"({CALIB_PUNTOS_MINIMO} puntos); se recibieron {len(move_state)} puntos "
            f"({len(dias)} días completos)."
        )

    agg_por_dia = [_day_aggregates(ms, d) for ms, d in dias]
    baseline = {}
    for key in ["mean_moveState", "std_moveState", "pct_reposo", "pct_alta_actividad",
                "mean_distancia", "std_distancia"]:
        valores = np.array([a[key] for a in agg_por_dia])
        baseline[f"{key}_mean"] = float(valores.mean())
        baseline[f"{key}_std"] = float(valores.std())

    quarters_ms = np.array([_quarter_means(ms, d)[0] for ms, d in dias])  # (n_dias, N_QUARTERS)
    quarters_dist = np.array([_quarter_means(ms, d)[1] for ms, d in dias])
    baseline["quarter_moveState_mean"] = quarters_ms.mean(axis=0).tolist()
    baseline["quarter_moveState_std"] = quarters_ms.std(axis=0).tolist()
    baseline["quarter_distancia_mean"] = quarters_dist.mean(axis=0).tolist()
    baseline["quarter_distancia_std"] = quarters_dist.std(axis=0).tolist()
    baseline["n_dias_calibracion"] = len(dias)
    baseline["feature_version"] = FEATURE_VERSION
    return baseline


def compute_features(eval_move_state: Sequence[float], eval_lat: Sequence[float],
                      eval_lon: Sequence[float], baseline: dict) -> List[float]:
    """Calcula el vector de features (orden = FEATURE_NAMES) para UNA
    ventana de evaluación de 24hs (288 puntos), relativas al `baseline`
    devuelto por compute_baseline(). Requiere exactamente 288 puntos:
    ventanas incompletas no tienen sentido como "un día evaluado"."""
    move_state = np.asarray(eval_move_state, dtype=np.float64)
    dist = distancias_desde_coords(eval_lat, eval_lon)

    if len(move_state) != SAMPLES_PER_DAY:
        raise ValueError(
            f"La ventana a evaluar debe tener exactamente {SAMPLES_PER_DAY} puntos (24hs a 5 min); "
            f"se recibieron {len(move_state)}."
        )

    dia = _day_aggregates(move_state, dist)

    def z(key):
        mean_b = baseline[f"{key}_mean"]
        std_b = baseline[f"{key}_std"]
        return (dia[key] - mean_b) / (std_b + EPS)

    quarters_ms, quarters_dist = _quarter_means(move_state, dist)
    z_quarters_ms = (quarters_ms - np.array(baseline["quarter_moveState_mean"])) / (
        np.array(baseline["quarter_moveState_std"]) + EPS)
    z_quarters_dist = (quarters_dist - np.array(baseline["quarter_distancia_mean"])) / (
        np.array(baseline["quarter_distancia_std"]) + EPS)

    features = [
        dia["mean_moveState"],
        dia["std_moveState"],
        dia["pct_reposo"],
        dia["pct_alta_actividad"],
        dia["mean_distancia"],
        dia["std_distancia"],
        z("mean_moveState"),
        z("std_moveState"),
        z("pct_reposo"),
        z("mean_distancia"),
        z("std_distancia"),
        float(np.max(np.abs(z_quarters_ms))),
        float(np.max(np.abs(z_quarters_dist))),
    ]

    for v in features:
        if not math.isfinite(v):
            raise ValueError("Se produjo un valor no finito (NaN/Inf) al calcular features de desvío.")

    return features
