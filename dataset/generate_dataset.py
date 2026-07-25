"""
generate_dataset.py
====================
Generador sintético y reproducible del dataset de telemetría para VigIA,
basado en estudios de comportamiento bovino y dinámica del rodeo en pastoreo.

Reglas biológicas aplicadas:
- Ciclo estral: ~21 días.
- Duración del celo: ~15 hs.
- Dinámica: Aumento de actividad, reducción de rumia, trayectorias más erráticas
  (menos lineales), y aumento de interacciones (mayor proximidad a otros animales).

El dataset resultante (`telemetria.parquet`) emula 1 a 1 las columnas de la
base de datos de producción (latitud, longitud, accel_x, accel_y, accel_z,
nivel_rumia, bateria, etc.), mas las etiquetas de celo y periestro.
"""

import argparse
import json
from pathlib import Path
import numpy as np
import pandas as pd

SAMPLES_PER_DAY = 288  # 1 lectura cada 5 minutos (24 * 60 / 5)
MINUTES_PER_SAMPLE = 5

def build_dataset(n_animales: int, dias: int, seed: int):
    rng = np.random.default_rng(seed)
    T = dias * SAMPLES_PER_DAY
    
    print(f"Simulando {n_animales} animales por {dias} días ({T} intervalos de 5 mins)...")
    
    # 1. Calendario de Celos
    is_celo = np.zeros((n_animales, T), dtype=bool)
    periestro = np.zeros((n_animales, T), dtype=bool)
    
    metadata_list = []
    
    for n in range(n_animales):
        # Ciclo estral y duración (animal-specific variations)
        ciclo_dias = rng.normal(21, 4)
        duracion_celo_hs = rng.normal(15, 6)
        duracion_celo_ticks = max(1, int(duracion_celo_hs * 60 / MINUTES_PER_SAMPLE))
        
        # El primer celo puede ocurrir en cualquier momento de los primeros 25 dias
        t_start = rng.uniform(0, 25) * SAMPLES_PER_DAY
        
        eventos = []
        while t_start < T:
            start_idx = int(t_start)
            end_idx = min(T, start_idx + duracion_celo_ticks)
            
            if start_idx < T:
                is_celo[n, start_idx:end_idx] = True
                
                # Periestro: ventana extendida (ej. 12hs antes y después)
                peri_ticks = int(12 * 60 / MINUTES_PER_SAMPLE)
                p_start = max(0, start_idx - peri_ticks)
                p_end = min(T, end_idx + peri_ticks)
                periestro[n, p_start:p_end] = True
                
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

    # Evitar solapamiento en etiquetas
    periestro = periestro & ~is_celo

    # 2. Dinámica de Movimiento (GPS Vectorizado)
    lat = np.zeros((n_animales, T), dtype=np.float32)
    lon = np.zeros((n_animales, T), dtype=np.float32)
    
    lat[:, 0] = -34.0 + rng.uniform(-0.01, 0.01, n_animales)
    lon[:, 0] = -60.0 + rng.uniform(-0.01, 0.01, n_animales)
    
    print("Calculando trayectorias (GPS)...")
    for t in range(1, T):
        center_lat = np.mean(lat[:, t-1])
        center_lon = np.mean(lon[:, t-1])
        
        # Movimiento aleatorio base (pastoreo)
        step_lat = rng.normal(0, 0.00002, n_animales)
        step_lon = rng.normal(0, 0.00002, n_animales)
        
        # Multiplicador de movimiento según estado
        multiplier = np.ones(n_animales)
        multiplier[is_celo[:, t-1]] = 3.5      # Actividad muy alta
        multiplier[periestro[:, t-1]] = 1.5    # Actividad ligeramente elevada
        
        # Días de "falsos positivos" por otros factores (movimiento de rodeo, moscas)
        if rng.random() < 0.02:  # 2% chance de evento global
            multiplier *= 2.0
            
        step_lat *= multiplier
        step_lon *= multiplier
        
        # Fuerza de cohesión (pull towards herd center) para que no se escapen
        pull_lat = (center_lat - lat[:, t-1]) * 0.01
        pull_lon = (center_lon - lon[:, t-1]) * 0.01
        
        lat[:, t] = lat[:, t-1] + step_lat + pull_lat
        lon[:, t] = lon[:, t-1] + step_lon + pull_lon

    # 3. Acelerometría y Rumia
    print("Generando acelerometría y rumia...")
    hours_of_day = (np.arange(T) * MINUTES_PER_SAMPLE / 60.0) % 24.0
    # Ritmo circadiano: 1 de día, 0 de noche
    activity_base = 0.5 + 0.5 * np.sin((hours_of_day - 6) / 24 * 2 * np.pi)
    activity_base = np.clip(activity_base, 0.05, 1.0)
    activity = np.tile(activity_base, (n_animales, 1))
    
    prob_rumia = 1.0 - activity
    prob_rumia[is_celo] *= 0.2  # Rumia cae drásticamente en celo
    prob_rumia[periestro] *= 0.7
    
    rumia_raw = rng.random((n_animales, T)) < prob_rumia
    
    amplitude = 0.15 + 0.35 * activity
    amplitude[rumia_raw] *= 0.25
    amplitude[is_celo] *= rng.uniform(1.8, 3.0, np.sum(is_celo))
    
    accel_x = rng.normal(0, 1, (n_animales, T)) * amplitude
    accel_y = rng.normal(0, 1, (n_animales, T)) * amplitude
    accel_z = 1.0 + rng.normal(0, 1, (n_animales, T)) * amplitude
    
    # Eventos cortos de monta (picos erráticos)
    celo_indices = np.argwhere(is_celo)
    if len(celo_indices) > 0:
        n_spikes = int(len(celo_indices) * 0.08) # 8% chance en un tick de celo
        spike_choices = rng.choice(len(celo_indices), n_spikes, replace=False)
        for idx in spike_choices:
            a, t = celo_indices[idx]
            spike_amp = rng.uniform(3.0, 6.0)
            accel_x[a, t] += rng.choice([-1, 1]) * spike_amp
            accel_y[a, t] += rng.choice([-1, 1]) * spike_amp

    # 4. Batería
    print("Simulando batería...")
    bateria = np.zeros((n_animales, T), dtype=np.float32)
    bateria[:, 0] = rng.uniform(85, 100, n_animales)
    drops = rng.uniform(0.0001, 0.0015, (n_animales, T))
    bateria = np.clip(bateria[:, [0]] - np.cumsum(drops, axis=1), 0, 100)

    # 5. Volcado a DataFrame
    print("Convirtiendo a formato columnar (Pandas)...")
    caravana_id = np.repeat(np.arange(1, n_animales + 1), T)
    start_ts = pd.Timestamp("2026-01-01")
    fecha_hora = start_ts + pd.to_timedelta(np.tile(np.arange(T) * 5, n_animales), unit="m")

    df = pd.DataFrame({
        "caravana_id": caravana_id,
        "fecha_hora": fecha_hora,
        "latitud": np.round(lat.flatten(), 6),
        "longitud": np.round(lon.flatten(), 6),
        "accel_x": np.round(accel_x.flatten().astype(np.float32), 4),
        "accel_y": np.round(accel_y.flatten().astype(np.float32), 4),
        "accel_z": np.round(accel_z.flatten().astype(np.float32), 4),
        "nivel_rumia": np.where(rumia_raw.flatten(), 1.0, 0.0).astype(np.float32),
        "bateria": np.round(bateria.flatten().astype(np.float32), 2),
        "is_celo": is_celo.flatten(),
        "is_periestro": periestro.flatten()
    })

    return df, metadata_list


def main():
    parser = argparse.ArgumentParser(description="Generador avanzado de telemetria bovina (VigIA)")
    parser.add_argument("--n-animales", type=int, default=60)
    parser.add_argument("--dias", type=int, default=60, help="Días totales a simular para todo el rodeo")
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

if __name__ == "__main__":
    main()
