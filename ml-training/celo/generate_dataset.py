"""
generate_dataset.py (v3)
=========================
Generador sintético y reproducible del dataset de telemetría para VigIA,
basado en estudios de comportamiento bovino y dinámica del rodeo en pastoreo.

"""

import argparse
import json
import sys
from pathlib import Path
import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from gps_noise_common import (  # noqa: E402
    inject_gps_noise,
    quantize_movestate,
    DEFAULT_GPS_NOISE_STD_M,
    DEFAULT_HEARTBEAT_INTERVAL_TICKS,
)

SAMPLES_PER_DAY = 288  # 1 lectura cada 5 minutos (24 * 60 / 5)
MINUTES_PER_SAMPLE = 5

# moveState real del collar: 0 (reposo) a 9 (alta aceleración)


def build_dataset(n_animales: int, dias: int, seed: int,
                   gps_noise_std_m: float = DEFAULT_GPS_NOISE_STD_M,
                   heartbeat_interval_ticks: int = DEFAULT_HEARTBEAT_INTERVAL_TICKS):
    rng = np.random.default_rng(seed)
    T = dias * SAMPLES_PER_DAY

    print(f"Simulando {n_animales} animales por {dias} días ({T} intervalos de 5 mins)...")

    # ------------------------------------------------------------------
    # 1. Calendario de Celos (con rampa suave de periestro -> celo -> baja)
    # ------------------------------------------------------------------
    # En vez de flags binarios, calculamos una "intensidad de celo" continua
    # en [0, 1] por animal y tick, usando una ventana gaussiana centrada en
    # el pico del estro. is_celo/is_periestro siguen existiendo como labels
    # binarios (umbral sobre la intensidad) para no romper el downstream.
    intensidad = np.zeros((n_animales, T), dtype=np.float32)
    is_celo = np.zeros((n_animales, T), dtype=bool)
    is_periestro = np.zeros((n_animales, T), dtype=bool)

    metadata_list = []

    # Duración total de la "campana" de periestro+celo+posceo (en ticks)
    ventana_total_hs = 39  # 12hs periestro + 15hs celo + 12hs postceo (aprox)
    sigma_ticks = (ventana_total_hs * 60 / MINUTES_PER_SAMPLE) / 4  # ~4 sigma cubre la ventana

    for n in range(n_animales):
        ciclo_dias = rng.normal(21, 4)
        duracion_celo_hs = max(4, rng.normal(15, 6))
        duracion_celo_ticks = max(1, int(duracion_celo_hs * 60 / MINUTES_PER_SAMPLE))
        peri_ticks = int(12 * 60 / MINUTES_PER_SAMPLE)

        t_start = rng.uniform(0, 25) * SAMPLES_PER_DAY

        eventos = []
        while t_start < T:
            centro = int(t_start + duracion_celo_ticks / 2)

            # Campana gaussiana de intensidad centrada en el pico del celo
            ticks = np.arange(T)
            campana = np.exp(-0.5 * ((ticks - centro) / sigma_ticks) ** 2)
            intensidad[n] = np.maximum(intensidad[n], campana)

            start_idx = int(t_start)
            end_idx = min(T, start_idx + duracion_celo_ticks)
            if start_idx < T:
                is_celo[n, start_idx:end_idx] = True
                p_start = max(0, start_idx - peri_ticks)
                p_end = min(T, end_idx + peri_ticks)
                is_periestro[n, p_start:p_end] = True
                eventos.append({
                    "start_tick": start_idx,
                    "end_tick": end_idx,
                    "duracion_hs": round((end_idx - start_idx) * 5 / 60, 2)
                })

            t_start += ciclo_dias * SAMPLES_PER_DAY

        metadata_list.append({
            "caravana_id": n + 1,
            "ciclo_promedio_dias": round(ciclo_dias, 2),
            "eventos_celo": eventos
        })

    is_periestro = is_periestro & ~is_celo

    # ------------------------------------------------------------------
    # 2. Dinámica de Movimiento — Random Walk CORRELACIONADO (con heading)
    # ------------------------------------------------------------------
    lat = np.zeros((n_animales, T), dtype=np.float32)
    lon = np.zeros((n_animales, T), dtype=np.float32)
    heading = rng.uniform(0, 2 * np.pi, n_animales)  # rumbo inicial por animal

    lat[:, 0] = -34.0 + rng.uniform(-0.01, 0.01, n_animales)
    lon[:, 0] = -60.0 + rng.uniform(-0.01, 0.01, n_animales)

    # Velocidad base y ruido de giro (turn_std) por estado. En celo, además
    # de más velocidad, el rumbo se vuelve mucho más errático (turn_std alto).
    SPEED_BASE = 0.000018
    TURN_STD_BASE = 0.35        # rad por tick, pastoreo normal
    TURN_STD_PERIESTRO = 0.55
    TURN_STD_CELO = 1.1         # trayectoria muy errática

    print("Calculando trayectorias (GPS, random walk correlacionado)...")
    for t in range(1, T):
        center_lat = np.mean(lat[:, t - 1])
        center_lon = np.mean(lon[:, t - 1])

        # Intensidad de celo en el tick anterior determina el "estado" de movimiento
        inten = intensidad[:, t - 1]
        speed_mult = 1.0 + inten * rng.uniform(2.0, 3.0)
        turn_std = TURN_STD_BASE + inten * (TURN_STD_CELO - TURN_STD_BASE)

        # Falsos positivos: 2% de eventos globales (moscas, arreo)
        if rng.random() < 0.02:
            speed_mult = speed_mult * 1.8
            turn_std = turn_std * 1.5

        # Actualizar rumbo con persistencia (correlated random walk)
        heading = heading + rng.normal(0, 1, n_animales) * turn_std
        speed = SPEED_BASE * speed_mult

        step_lat = speed * np.cos(heading)
        step_lon = speed * np.sin(heading)

        # Fuerza de cohesión hacia el centro del rodeo
        pull_lat = (center_lat - lat[:, t - 1]) * 0.008
        pull_lon = (center_lon - lon[:, t - 1]) * 0.008

        lat[:, t] = lat[:, t - 1] + step_lat + pull_lat
        lon[:, t] = lon[:, t - 1] + step_lon + pull_lon

    # Trayectoria "verdadera" (determina las labels is_celo/is_periestro,
    # ya calculadas más arriba y ajenas a lat/lon) vs. observada: un collar
    # real, aunque esté quieto, mide con ruido GNSS -- ver
    # gps_noise_common.py para la derivación empírica de gps_noise_std_m.
    lat_true, lon_true = lat, lon
    lat, lon = inject_gps_noise(lat_true, lon_true, gps_noise_std_m, rng)

    # ------------------------------------------------------------------
    # 3. Estado de Movimiento (MOVESTATE)
    # ------------------------------------------------------------------
    print("Generando estados de movimiento (MOVESTATE)...")
    hours_of_day = (np.arange(T) * MINUTES_PER_SAMPLE / 60.0) % 24.0

    # Ritmo circadiano: más actividad de día (6 a 20hs)
    activity_base = np.where(
        (hours_of_day >= 6) & (hours_of_day < 20),
        rng.uniform(0.4, 0.8, (n_animales, T)),  # de día, más chances de caminar/pastar
        rng.uniform(0.0, 0.3, (n_animales, T))   # de noche, más chances de dormir
    )

    # Probabilidad de descanso (moveState 0-1) vs actividad normal (moveState 2-6)
    # Baja la prob de descanso si está en celo (intensidad alta)
    is_resting = rng.random((n_animales, T)) > (activity_base + intensidad * 0.5)

    # Asignar estados base
    move_state = np.where(is_resting, rng.integers(0, 2, (n_animales, T)), rng.integers(2, 7, (n_animales, T)))

    # Agregar picos de alta actividad (moveState 7-9) por celo.
    # La probabilidad de un pico es proporcional a la intensidad de celo al
    # cuadrado (hasta 15% de los ticks en pico de celo).
    prob_high_move = intensidad ** 2 * 0.15
    high_move_mask = rng.random((n_animales, T)) < prob_high_move
    move_state[high_move_mask] = rng.integers(7, 10, high_move_mask.sum())

    # Falsos positivos de alta actividad (arreo, susto) independientes del celo
    fp_mask = rng.random((n_animales, T)) < 0.005  # 0.5%
    move_state[fp_mask] = rng.integers(7, 10, fp_mask.sum())

    # En producción moveState solo puede cambiar cuando llega un Heartbeat
    # real (ver gps_noise_common.quantize_movestate): un valor fresco cada
    # tick de 5 min, como se generó arriba, no es lo que el modelo va a
    # ver en inferencia.
    move_state = quantize_movestate(move_state, heartbeat_interval_ticks)

    # ------------------------------------------------------------------
    # 4. Batería
    # ------------------------------------------------------------------
    print("Simulando batería...")
    bateria = np.zeros((n_animales, T), dtype=np.float32)
    bateria[:, 0] = rng.uniform(85, 100, n_animales)
    drops = rng.uniform(0.0001, 0.0015, (n_animales, T))
    bateria = np.clip(bateria[:, [0]] - np.cumsum(drops, axis=1), 0, 100)

    # ------------------------------------------------------------------
    # 5. Volcado a DataFrame
    # ------------------------------------------------------------------
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
        "intensidad_celo": np.round(intensidad.flatten(), 4),
        "is_celo": is_celo.flatten(),
        "is_periestro": is_periestro.flatten(),
    })

    return df, metadata_list


def main():
    parser = argparse.ArgumentParser(description="Generador avanzado de telemetria bovina (VigIA) v3")
    parser.add_argument("--n-animales", type=int, default=200,
                         help="60 daba un test set de solo 720 muestras/57 positivos -- suficiente "
                              "para entrenar pero con métricas de evaluación poco confiables (ver README.md)")
    parser.add_argument("--dias", type=int, default=90, help="Días totales a simular para todo el rodeo")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out-dir", type=str, default="dataset")
    parser.add_argument("--gps-noise-std-m", type=float, default=DEFAULT_GPS_NOISE_STD_M,
                         help="Desvío estándar (metros, por eje) del ruido de medición GNSS "
                              "inyectado sobre la trayectoria simulada, ver gps_noise_common.py")
    parser.add_argument("--heartbeat-interval-ticks", type=int, default=DEFAULT_HEARTBEAT_INTERVAL_TICKS,
                         help="Cada cuántos ticks de 5 min cambia moveState (cadencia real de Heartbeat)")
    args = parser.parse_args()

    df, metadata = build_dataset(args.n_animales, args.dias, args.seed,
                                  gps_noise_std_m=args.gps_noise_std_m,
                                  heartbeat_interval_ticks=args.heartbeat_interval_ticks)

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"Exportando {len(df)} filas a {out_dir / 'telemetria.parquet'} ...")
    df.to_parquet(out_dir / "telemetria.parquet", index=False)

    with open(out_dir / "ground_truth_metadata.json", "w") as f:
        json.dump({
            "gps_noise_std_m": args.gps_noise_std_m,
            "heartbeat_interval_ticks": args.heartbeat_interval_ticks,
            "animales": metadata,
        }, f, indent=2, ensure_ascii=False)

    print("¡Generación completada con éxito!")
    print(f"Ticks en celo: {int(df['is_celo'].sum())} | en periestro: {int(df['is_periestro'].sum())}")


if __name__ == "__main__":
    main()