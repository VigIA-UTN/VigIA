"""
generate_dataset.py (Salud)
=========================
Generador sintético y reproducible del dataset de telemetría para VigIA,
orientado a la detección de DESVÍOS SANITARIOS por perfil individual.

A diferencia del modelo de celo (evento puntual, comparado contra la
población general), el modelo de salud compara a cada animal contra SU
PROPIA línea base, construida durante un período de calibración de 7 días
(requisito de negocio RF03). Por eso el simulador reproduce dos cosas que
el diseño anterior (basado en clasificación poblacional) no necesitaba:

1. Un período de calibración inicial (CALIB_DIAS = 7) por animal, en el
   que se asume al animal sano (no se generan eventos de enfermedad ahí).
   Esto refleja la asunción de negocio de RF03: el perfil de referencia se
   arma en base a una ventana en la que no hay evidencia de enfermedad.
2. Variabilidad INDIVIDUAL de comportamiento base (cada animal tiene su
   propio nivel de actividad "normal", no solo el mismo patrón circadiano
   poblacional). Sin esto, un enfoque de "desvío respecto al propio
   perfil" sería equivalente a comparar contra la población, y no tendría
   sentido usar un perfil individual en primer lugar.

Severidades (igual que antes):
0: Normal
1: Bajo (ej. cojera leve, inicio de enfermedad)
2: Medio (ej. cojera clínica, enfermedad moderada)
3: Alto (ej. enfermedad grave que postra al animal, o estrés agudo/problema de parto)
"""

import argparse
import json
import sys
from pathlib import Path
# pyrefly: ignore [missing-import]
import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from gps_noise_common import (  # noqa: E402
    inject_gps_noise,
    quantize_movestate,
    DEFAULT_GPS_NOISE_STD_M,
    DEFAULT_HEARTBEAT_INTERVAL_TICKS,
)

SAMPLES_PER_DAY = 288  # 1 lectura cada 5 minutos
MINUTES_PER_SAMPLE = 5
CALIB_DIAS = 7  # RF03: período de calibración de referencia individual
CALIB_TICKS = CALIB_DIAS * SAMPLES_PER_DAY


def build_dataset(n_animales: int, dias_evaluacion: int, seed: int,
                   gps_noise_std_m: float = DEFAULT_GPS_NOISE_STD_M,
                   heartbeat_interval_ticks: int = DEFAULT_HEARTBEAT_INTERVAL_TICKS):
    rng = np.random.default_rng(seed)
    dias_totales = CALIB_DIAS + dias_evaluacion
    T = dias_totales * SAMPLES_PER_DAY

    print(f"Simulando {n_animales} animales por {dias_totales} días "
          f"({CALIB_DIAS} de calibración + {dias_evaluacion} de evaluación), "
          f"{T} intervalos de 5 mins...")

    # ------------------------------------------------------------------
    # Personalidad individual: cada animal tiene su propio nivel de
    # actividad "normal", independiente de los demás. Esto es lo que
    # justifica comparar contra un perfil PROPIO en vez de contra la
    # población: sin esta variabilidad, el perfil individual coincidiría
    # con el perfil poblacional.
    # ------------------------------------------------------------------
    activity_offset = np.clip(rng.normal(0.0, 0.10, n_animales), -0.25, 0.25)
    rest_bias = np.clip(rng.normal(0.0, 0.08, n_animales), -0.2, 0.2)

    # Matrices base
    severidad = np.zeros((n_animales, T), dtype=int)
    tipo_alto = np.zeros((n_animales, T), dtype=int)  # 0: N/A, 1: Inmovilidad, 2: Hiperactividad (parto)

    metadata_list = []

    # Generar eventos de enfermedad SOLO después del período de calibración
    # (mismo desbalance que antes: mayoría de días normales)
    for n in range(n_animales):
        eventos = []
        if rng.random() < 0.40:
            r = rng.random()
            if r < 0.50:
                sev_max = 1
            elif r < 0.85:
                sev_max = 2
            else:
                sev_max = 3

            duracion_dias = rng.uniform(2, 6)
            duracion_ticks = int(duracion_dias * SAMPLES_PER_DAY)

            # El evento solo puede empezar DESPUÉS de la calibración
            ventana_disponible = T - CALIB_TICKS - duracion_ticks
            if ventana_disponible <= 0:
                metadata_list.append({"caravana_id": n + 1, "eventos_salud": []})
                continue
            t_start = CALIB_TICKS + rng.integers(0, max(1, ventana_disponible))
            t_end = t_start + duracion_ticks

            is_progresivo = rng.random() < 0.70
            t_alto = 0

            if is_progresivo and sev_max > 1:
                etapas = sev_max
                ticks_por_etapa = duracion_ticks // etapas

                for i in range(etapas):
                    sev_actual = i + 1
                    s_idx = t_start + i * ticks_por_etapa
                    e_idx = t_start + (i + 1) * ticks_por_etapa if i < etapas - 1 else t_end
                    severidad[n, s_idx:e_idx] = sev_actual

                    if sev_actual == 3:
                        t_alto = 1 if rng.random() < 0.5 else 2
                        tipo_alto[n, s_idx:e_idx] = t_alto
            else:
                severidad[n, t_start:t_end] = sev_max
                if sev_max == 3:
                    t_alto = 1 if rng.random() < 0.5 else 2
                    tipo_alto[n, t_start:t_end] = t_alto

            eventos.append({
                "start_tick": int(t_start),
                "end_tick": int(t_end),
                "severidad_maxima": int(sev_max),
                "progresiva": bool(is_progresivo),
                "duracion_dias": round(duracion_dias, 2)
            })

        metadata_list.append({
            "caravana_id": n + 1,
            "eventos_salud": eventos
        })

    # GPS / Random Walk
    lat = np.zeros((n_animales, T), dtype=np.float32)
    lon = np.zeros((n_animales, T), dtype=np.float32)
    heading = rng.uniform(0, 2 * np.pi, n_animales)

    lat[:, 0] = -34.0 + rng.uniform(-0.01, 0.01, n_animales)
    lon[:, 0] = -60.0 + rng.uniform(-0.01, 0.01, n_animales)

    SPEED_BASE = 0.000018
    TURN_STD_BASE = 0.35

    print("Calculando trayectorias (GPS, random walk correlacionado)...")
    for t in range(1, T):
        center_lat = np.mean(lat[:, t - 1])
        center_lon = np.mean(lon[:, t - 1])

        sev = severidad[:, t - 1]
        t_alto = tipo_alto[:, t - 1]

        # Modificadores por defecto (Normal) + personalidad individual
        speed_mult = np.ones(n_animales) * (1.0 + activity_offset)
        turn_std = np.full(n_animales, TURN_STD_BASE)

        mask_bajo = (sev == 1)
        speed_mult[mask_bajo] = 0.8 * (1.0 + activity_offset[mask_bajo])

        mask_medio = (sev == 2)
        speed_mult[mask_medio] = 0.4 * (1.0 + activity_offset[mask_medio])

        mask_alto_1 = (sev == 3) & (t_alto == 1)
        speed_mult[mask_alto_1] = 0.1

        mask_alto_2 = (sev == 3) & (t_alto == 2)
        speed_mult[mask_alto_2] = 2.5
        turn_std[mask_alto_2] = TURN_STD_BASE * 3

        speed_mult = np.clip(speed_mult, 0.05, None)

        heading = heading + rng.normal(0, 1, n_animales) * turn_std
        speed = SPEED_BASE * speed_mult

        step_lat = speed * np.cos(heading)
        step_lon = speed * np.sin(heading)

        pull_lat = (center_lat - lat[:, t - 1]) * 0.008
        pull_lon = (center_lon - lon[:, t - 1]) * 0.008

        lat[:, t] = lat[:, t - 1] + step_lat + pull_lat
        lon[:, t] = lon[:, t - 1] + step_lon + pull_lon

    # Trayectoria "verdadera" (determina severidad/tipo_alto, ya calculados
    # más arriba y ajenos a lat/lon) vs. observada: un collar real, aunque
    # esté quieto, mide con ruido GNSS -- ver gps_noise_common.py para la
    # derivación empírica de gps_noise_std_m.
    lat_true, lon_true = lat, lon
    lat, lon = inject_gps_noise(lat_true, lon_true, gps_noise_std_m, rng)

    # Estado de Movimiento (MOVESTATE)
    print("Generando estados de movimiento (MOVESTATE)...")
    hours_of_day = (np.arange(T) * MINUTES_PER_SAMPLE / 60.0) % 24.0

    activity_base = np.where(
        (hours_of_day >= 6) & (hours_of_day < 20),
        rng.uniform(0.4, 0.8, (n_animales, T)),
        rng.uniform(0.0, 0.3, (n_animales, T))
    )

    # Personalidad individual: desplaza el nivel de actividad "normal" de
    # cada animal de forma consistente en el tiempo.
    activity_base = activity_base + activity_offset[:, None] - rest_bias[:, None]

    activity_base[severidad == 1] -= 0.15
    activity_base[severidad == 2] -= 0.40
    activity_base[(severidad == 3) & (tipo_alto == 1)] -= 0.80
    activity_base[(severidad == 3) & (tipo_alto == 2)] += 0.80

    activity_base = np.clip(activity_base, 0.0, 1.0)

    is_resting = rng.random((n_animales, T)) > activity_base
    move_state = np.where(is_resting, rng.integers(0, 2, (n_animales, T)), rng.integers(2, 7, (n_animales, T)))

    mask_hiper = (severidad == 3) & (tipo_alto == 2)
    prob_high_move = np.where(mask_hiper, 0.6, 0.005)
    high_move_mask = rng.random((n_animales, T)) < prob_high_move
    move_state[high_move_mask] = rng.integers(7, 10, high_move_mask.sum())

    # En producción moveState solo puede cambiar cuando llega un Heartbeat
    # real (ver gps_noise_common.quantize_movestate): un valor fresco cada
    # tick de 5 min, como se generó arriba, no es lo que el modelo va a
    # ver en inferencia.
    move_state = quantize_movestate(move_state, heartbeat_interval_ticks)

    print("Simulando batería...")
    bateria = np.zeros((n_animales, T), dtype=np.float32)
    bateria[:, 0] = rng.uniform(85, 100, n_animales)
    drops = rng.uniform(0.0001, 0.0015, (n_animales, T))
    bateria = np.clip(bateria[:, [0]] - np.cumsum(drops, axis=1), 0, 100)

    print("Convirtiendo a formato columnar (Pandas)...")
    caravana_id = np.repeat(np.arange(1, n_animales + 1), T)
    start_ts = pd.Timestamp("2026-01-01")
    fecha_hora = start_ts + pd.to_timedelta(np.tile(np.arange(T) * 5, n_animales), unit="m")

    df = pd.DataFrame({
        "caravana_id": caravana_id,
        "fecha_hora": fecha_hora,
        "latitud": np.round(lat.flatten(), 6),
        "longitud": np.round(lon.flatten(), 6),
        "latitud_true": np.round(lat_true.flatten(), 6),
        "longitud_true": np.round(lon_true.flatten(), 6),
        "moveState": move_state.flatten(),
        "bateria": np.round(bateria.flatten().astype(np.float32), 2),
        "severidad": severidad.flatten(),
        # tick relativo al inicio de la serie del animal: permite
        # separar limpiamente calibración (< CALIB_TICKS) de evaluación
        # sin recalcular fechas.
        "tick": np.tile(np.arange(T), n_animales),
    })

    return df, metadata_list


def main():
    parser = argparse.ArgumentParser(description="Generador sintético VigIA - Salud (perfil individual)")
    parser.add_argument("--n-animales", type=int, default=300,
                         help="120 animales daba una muestra de severidad 1/2/3 demasiado chica "
                              "para estimar precisión/recall de forma confiable (ver README.md); "
                              "300 la estabiliza sin diluir el balance de clases")
    parser.add_argument("--dias-evaluacion", type=int, default=90,
                         help="Días de evaluación POST-calibración por animal (además de los 7 de calibración fijos por RF03)")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out-dir", type=str, default="dataset")
    parser.add_argument("--gps-noise-std-m", type=float, default=DEFAULT_GPS_NOISE_STD_M,
                         help="Desvío estándar (metros, por eje) del ruido de medición GNSS "
                              "inyectado sobre la trayectoria simulada, ver gps_noise_common.py")
    parser.add_argument("--heartbeat-interval-ticks", type=int, default=DEFAULT_HEARTBEAT_INTERVAL_TICKS,
                         help="Cada cuántos ticks de 5 min cambia moveState (cadencia real de Heartbeat)")
    args = parser.parse_args()

    df, metadata = build_dataset(args.n_animales, args.dias_evaluacion, args.seed,
                                  gps_noise_std_m=args.gps_noise_std_m,
                                  heartbeat_interval_ticks=args.heartbeat_interval_ticks)

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"Exportando {len(df)} filas a {out_dir / 'telemetria.parquet'} ...")
    df.to_parquet(out_dir / "telemetria.parquet", index=False)

    with open(out_dir / "ground_truth_metadata.json", "w") as f:
        json.dump({
            "calib_dias": CALIB_DIAS,
            "calib_ticks": CALIB_TICKS,
            "samples_per_day": SAMPLES_PER_DAY,
            "gps_noise_std_m": args.gps_noise_std_m,
            "heartbeat_interval_ticks": args.heartbeat_interval_ticks,
            "animales": metadata,
        }, f, indent=2, ensure_ascii=False)

    print("¡Generación completada con éxito!")
    print(f"Días de calibración por animal (RF03): {CALIB_DIAS}")
    print("Distribución de severidad (en ticks, incluye calibración -siempre 0-):")
    print(df['severidad'].value_counts())
    print("Distribución de severidad SOLO en período de evaluación:")
    print(df.loc[df['tick'] >= CALIB_TICKS, 'severidad'].value_counts())


if __name__ == "__main__":
    main()
