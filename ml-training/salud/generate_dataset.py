"""
generate_dataset.py (Salud)
=========================
Generador sintético y reproducible del dataset de telemetría para VigIA,
orientado a la detección de problemas de salud.

Severidades:
0: Normal
1: Bajo (ej. cojera leve, inicio de enfermedad)
2: Medio (ej. cojera clínica, enfermedad moderada)
3: Alto (ej. enfermedad grave que postra al animal, o estrés agudo/problema de parto)
"""

import argparse
import json
from pathlib import Path
import numpy as np
import pandas as pd

SAMPLES_PER_DAY = 288  # 1 lectura cada 5 minutos
MINUTES_PER_SAMPLE = 5

def build_dataset(n_animales: int, dias: int, seed: int):
    rng = np.random.default_rng(seed)
    T = dias * SAMPLES_PER_DAY

    print(f"Simulando {n_animales} animales por {dias} días ({T} intervalos de 5 mins)...")

    # Matrices base
    severidad = np.zeros((n_animales, T), dtype=int)
    tipo_alto = np.zeros((n_animales, T), dtype=int) # 0: N/A, 1: Inmovilidad, 2: Hiperactividad (parto)

    metadata_list = []

    # Generar eventos de enfermedad (desbalance: mayoría de días normales)
    for n in range(n_animales):
        # 40% de los animales tendrán un evento en este periodo
        eventos = []
        if rng.random() < 0.40:
            # Seleccionar severidad: 50% Baja, 35% Media, 15% Alta
            r = rng.random()
            if r < 0.50:
                sev = 1
            elif r < 0.85:
                sev = 2
            else:
                sev = 3
            
            # Duración del evento: 1 a 5 días
            duracion_dias = rng.uniform(1, 5)
            duracion_ticks = int(duracion_dias * SAMPLES_PER_DAY)
            
            # Inicio del evento
            t_start = rng.integers(0, max(1, T - duracion_ticks))
            t_end = t_start + duracion_ticks
            
            severidad[n, t_start:t_end] = sev
            
            if sev == 3:
                # 50% inmovilidad severa, 50% hiperactividad (problema parto)
                t_alto = 1 if rng.random() < 0.5 else 2
                tipo_alto[n, t_start:t_end] = t_alto
                
            eventos.append({
                "start_tick": int(t_start),
                "end_tick": int(t_end),
                "severidad": sev,
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
        
        # Modificadores por defecto (Normal)
        speed_mult = np.ones(n_animales)
        turn_std = np.full(n_animales, TURN_STD_BASE)
        
        # Aplicar reglas por severidad
        # Bajo (1): 80% velocidad
        mask_bajo = (sev == 1)
        speed_mult[mask_bajo] = 0.8
        
        # Medio (2): 40% velocidad
        mask_medio = (sev == 2)
        speed_mult[mask_medio] = 0.4
        
        # Alto (3) - Tipo 1 (Inmovilidad): 10% velocidad
        mask_alto_1 = (sev == 3) & (t_alto == 1)
        speed_mult[mask_alto_1] = 0.1
        
        # Alto (3) - Tipo 2 (Hiperactividad): 250% velocidad, rumbo muy errático
        mask_alto_2 = (sev == 3) & (t_alto == 2)
        speed_mult[mask_alto_2] = 2.5
        turn_std[mask_alto_2] = TURN_STD_BASE * 3

        # Actualizar rumbo con persistencia
        heading = heading + rng.normal(0, 1, n_animales) * turn_std
        speed = SPEED_BASE * speed_mult

        step_lat = speed * np.cos(heading)
        step_lon = speed * np.sin(heading)

        # Fuerza de cohesión hacia el centro del rodeo
        pull_lat = (center_lat - lat[:, t - 1]) * 0.008
        pull_lon = (center_lon - lon[:, t - 1]) * 0.008

        lat[:, t] = lat[:, t - 1] + step_lat + pull_lat
        lon[:, t] = lon[:, t - 1] + step_lon + pull_lon

    # Estado de Movimiento (MOVESTATE)
    print("Generando estados de movimiento (MOVESTATE)...")
    hours_of_day = (np.arange(T) * MINUTES_PER_SAMPLE / 60.0) % 24.0

    # Ritmo circadiano: más actividad de día (6 a 20hs)
    activity_base = np.where(
        (hours_of_day >= 6) & (hours_of_day < 20),
        rng.uniform(0.4, 0.8, (n_animales, T)),
        rng.uniform(0.0, 0.3, (n_animales, T))
    )

    # Modificar activity_base según severidad (afecta la probabilidad de descanso)
    # Valores de activity_base más altos = más probabilidad de movimiento (moveState > 1)
    # Restar a activity_base aumenta el descanso.
    activity_base[severidad == 1] -= 0.15
    activity_base[severidad == 2] -= 0.40
    activity_base[(severidad == 3) & (tipo_alto == 1)] -= 0.80 # Casi todo el tiempo reposo
    activity_base[(severidad == 3) & (tipo_alto == 2)] += 0.80 # Casi nunca reposo

    activity_base = np.clip(activity_base, 0.0, 1.0)

    # Probabilidad de descanso (moveState 0-1) vs actividad normal (moveState 2-6)
    is_resting = rng.random((n_animales, T)) > activity_base

    move_state = np.where(is_resting, rng.integers(0, 2, (n_animales, T)), rng.integers(2, 7, (n_animales, T)))

    # Alta actividad (moveState 7-9) inducida por hiperactividad (Alto tipo 2)
    mask_hiper = (severidad == 3) & (tipo_alto == 2)
    prob_high_move = np.where(mask_hiper, 0.6, 0.005) # 60% chance de alta actividad si es hiperactivo, 0.5% por defecto
    high_move_mask = rng.random((n_animales, T)) < prob_high_move
    move_state[high_move_mask] = rng.integers(7, 10, high_move_mask.sum())

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
        "moveState": move_state.flatten(),
        "bateria": np.round(bateria.flatten().astype(np.float32), 2),
        "severidad": severidad.flatten()
    })

    return df, metadata_list


def main():
    parser = argparse.ArgumentParser(description="Generador sintético VigIA - Salud")
    parser.add_argument("--n-animales", type=int, default=100)
    parser.add_argument("--dias", type=int, default=120)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out-dir", type=str, default="dataset")
    args = parser.parse_args()

    df, metadata = build_dataset(args.n_animales, args.dias, args.seed)

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"Exportando {len(df)} filas a {out_dir / 'telemetria.parquet'} ...")
    df.to_parquet(out_dir / "telemetria.parquet", index=False)

    with open(out_dir / "ground_truth_metadata.json", "w") as f:
        json.dump(metadata, f, indent=2, ensure_ascii=False)

    print("¡Generación completada con éxito!")
    print("Distribución de severidad (en ticks):")
    print(df['severidad'].value_counts())

if __name__ == "__main__":
    main()
