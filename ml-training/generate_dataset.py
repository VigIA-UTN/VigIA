"""
generate_dataset.py (v2)
=========================
Generador sintético y reproducible del dataset de telemetría para VigIA,
basado en estudios de comportamiento bovino y dinámica del rodeo en pastoreo.

Cambios respecto a v1:
- Random walk CORRELACIONADO (con heading/rumbo persistente) en vez de pasos
  independientes: una vaca real no cambia de dirección bruscamente cada 5 min.
- Rampa de actividad SUAVE (sigmoide) entre periestro y celo, en vez de un
  escalón binario: la RNN puede aprender la tendencia de "acercamiento" al celo.
- Amplitudes de aceleración CALIBRADAS con datos reales de ear-tag/collar en
  pastoreo libre (dataset público db-cow-walking, Morales-Vargas et al. 2025,
  Universidad de La Frontera, Chile). Ver constantes en CALIBRATED_* abajo.
- Detección de MONTA sin depender de proximidad entre animales: se modela
  como un evento de orientación (pitch alto, tren delantero levantado) +
  pico de varianza dinámica simultáneo, resumidos en la ventana de 5 min
  (igual que reportaría un ear-tag LoRaWAN real, que no manda raw accel).
  Se agregan también eventos de "pitch alto sin spike" durante pastoreo
  normal (cabeza agachada), para que el modelo NO aprenda "pitch alto = celo"
  de forma espuria.

Reglas biológicas aplicadas:
- Ciclo estral: ~21 días.
- Duración del celo: ~15 hs.
- Dinámica: aumento de actividad, reducción de rumia, trayectorias más
  erráticas (menos lineales), y eventos de monta.

El dataset resultante (`telemetria.parquet`) emula 1 a 1 las columnas de la
base de datos de producción (latitud, longitud, accel_x, accel_y, accel_z,
nivel_rumia, bateria, etc.), mas las etiquetas de celo, periestro y monta.
"""

import argparse
import json
from pathlib import Path
import numpy as np
import pandas as pd

SAMPLES_PER_DAY = 288  # 1 lectura cada 5 minutos (24 * 60 / 5)
MINUTES_PER_SAMPLE = 5

# --- Constantes calibradas con datos reales (db-cow-walking, ear-tag/collar,
#     pastoreo libre, Temuco, Chile). Magnitudes de aceleración cruda en g,
#     convertidas desde m/s^2 (/9.81). Representan el DESVÍO ESTÁNDAR de la
#     magnitud de aceleración dentro de una ventana, por comportamiento. ---
CALIBRATED_STD_RESTING = 0.024   # rumia / descanso: casi estático
CALIBRATED_STD_WALKING = 0.138   # caminata
CALIBRATED_STD_GRAZING = 0.212   # pastoreo activo (cabeza en movimiento)
CALIBRATED_STD_MISC = 0.140      # sacudidas, rascado, etc.

# Multiplicador de celo sobre el baseline de pastoreo (se aplica ADEMÁS de
# la elevada varianza propia del pastoreo/caminata en el pico de estro).
CELO_AMPLITUDE_MULTIPLIER = (2.2, 3.2)


def build_dataset(n_animales: int, dias: int, seed: int):
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

    # ------------------------------------------------------------------
    # 3. Acelerometría, Rumia y Eventos de Monta (calibrado con datos reales)
    # ------------------------------------------------------------------
    print("Generando acelerometría, rumia y eventos de monta...")
    hours_of_day = (np.arange(T) * MINUTES_PER_SAMPLE / 60.0) % 24.0
    # Ritmo circadiano asimétrico: sube rápido 06-12hs, baja más lento 12-20hs
    # (calibrado con perfil observado en ear-tags, PMC11154254)
    activity_base = np.where(
        (hours_of_day >= 6) & (hours_of_day < 12),
        (hours_of_day - 6) / 6,
        np.where(
            (hours_of_day >= 12) & (hours_of_day < 20),
            1.0 - 0.7 * (hours_of_day - 12) / 8,
            0.15,
        )
    )
    activity_base = np.clip(activity_base, 0.1, 1.0)
    activity = np.tile(activity_base, (n_animales, 1))

    prob_rumia = 1.0 - activity
    # Antes: multiplicador arbitrario 0.2 / 0.7. Ahora, basado en la relación
    # real de varianzas resting vs. grazing (~0.024 / 0.212 ≈ 0.11-0.13):
    prob_rumia = prob_rumia * (1.0 - intensidad * 0.85)  # cae ~85% en pico de celo

    rumia_raw = rng.random((n_animales, T)) < prob_rumia

    # Amplitud (desvío) base por tick: interpolamos entre resting y grazing
    # según el nivel de actividad, luego sumamos el extra de celo.
    amplitude = CALIBRATED_STD_RESTING + activity * (CALIBRATED_STD_GRAZING - CALIBRATED_STD_RESTING)
    amplitude = np.where(rumia_raw, CALIBRATED_STD_RESTING, amplitude)
    celo_extra = rng.uniform(*CELO_AMPLITUDE_MULTIPLIER, size=(n_animales, T))
    amplitude = amplitude * (1.0 + intensidad * (celo_extra - 1.0))

    accel_x = rng.normal(0, 1, (n_animales, T)) * amplitude
    accel_y = rng.normal(0, 1, (n_animales, T)) * amplitude
    accel_z = 1.0 + rng.normal(0, 1, (n_animales, T)) * amplitude

    # --- Pitch (orientación) base: en reposo casi todo el peso es accel_z.
    # Un evento de monta implica levantar el tren delantero -> parte de la
    # componente de gravedad "migra" de Z hacia X (pitch ~30-45°). ---
    pitch_deg = np.zeros((n_animales, T), dtype=np.float32)
    is_monta = np.zeros((n_animales, T), dtype=bool)

    # Probabilidad de monta por tick, proporcional a la intensidad de celo,
    # ya que representa la fracción de esos 5 minutos con un evento de monta.
    prob_monta = intensidad ** 2 * 0.12  # techo ~12% de los ticks en pico de celo
    monta_draw = rng.random((n_animales, T)) < prob_monta
    is_monta[monta_draw] = True

    # Falsos positivos de pitch: cabeza agachada pastando, SIN spike dinámico.
    # Objetivo: que el modelo aprenda que pitch alto solo no alcanza.
    prob_pitch_pastoreo = np.where(rumia_raw, 0.0, activity * 0.03)
    pitch_pastoreo_draw = rng.random((n_animales, T)) < prob_pitch_pastoreo

    n_monta = int(monta_draw.sum())
    if n_monta > 0:
        pitch_deg[monta_draw] = rng.uniform(30, 45, n_monta)
        # Redistribuir gravedad: Z baja, X sube, en proporción al pitch
        theta = np.radians(pitch_deg[monta_draw])
        idx = np.argwhere(monta_draw)
        accel_z_vals = np.cos(theta)
        accel_x_vals = np.sin(theta) * rng.choice([-1, 1], n_monta)
        for k, (a, t) in enumerate(idx):
            accel_z[a, t] = accel_z_vals[k] + rng.normal(0, 0.05)
            accel_x[a, t] += accel_x_vals[k]
            # Pico dinámico simultáneo (resumen agregado de la ventana)
            spike_amp = rng.uniform(3.0, 6.0)
            accel_x[a, t] += rng.choice([-1, 1]) * spike_amp * 0.5
            accel_y[a, t] += rng.choice([-1, 1]) * spike_amp * 0.5

    n_pitch_pastoreo = int(pitch_pastoreo_draw.sum())
    if n_pitch_pastoreo > 0:
        pitch_deg[pitch_pastoreo_draw] = rng.uniform(20, 40, n_pitch_pastoreo)
        theta = np.radians(pitch_deg[pitch_pastoreo_draw])
        idx = np.argwhere(pitch_pastoreo_draw)
        accel_z_vals = np.cos(theta)
        accel_x_vals = np.sin(theta) * rng.choice([-1, 1], n_pitch_pastoreo)
        for k, (a, t) in enumerate(idx):
            accel_z[a, t] = accel_z_vals[k] + rng.normal(0, 0.05)
            accel_x[a, t] += accel_x_vals[k]
            # SIN spike dinámico extra: la varianza sigue siendo la de pastoreo normal

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
        "accel_x": np.round(accel_x.flatten().astype(np.float32), 4),
        "accel_y": np.round(accel_y.flatten().astype(np.float32), 4),
        "accel_z": np.round(accel_z.flatten().astype(np.float32), 4),
        "pitch_deg": np.round(pitch_deg.flatten(), 2),
        "nivel_rumia": np.where(rumia_raw.flatten(), 1.0, 0.0).astype(np.float32),
        "bateria": np.round(bateria.flatten().astype(np.float32), 2),
        "intensidad_celo": np.round(intensidad.flatten(), 4),
        "is_celo": is_celo.flatten(),
        "is_periestro": is_periestro.flatten(),
        "is_monta": is_monta.flatten(),
    })

    return df, metadata_list


def main():
    parser = argparse.ArgumentParser(description="Generador avanzado de telemetria bovina (VigIA) v2")
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
    print(f"Eventos de monta generados: {int(df['is_monta'].sum())}")
    print(f"Ticks en celo: {int(df['is_celo'].sum())} | en periestro: {int(df['is_periestro'].sum())}")


if __name__ == "__main__":
    main()