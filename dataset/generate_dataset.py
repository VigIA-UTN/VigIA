"""
generate_dataset.py
====================
Generador sintético y reproducible del dataset de telemetría (aceleración x,y,z)
para entrenar el modelo de deteccion de celo de VigIA.

Por que sintetico:
No se encontro un dataset publico ya etiquetado como celo / no-celo en ventanas
de 24hs con acelerometria cruda. Lo que si existe, y se usa aqui como
referencia de calibracion, es:

  - Un dataset publico (CC BY) de comportamiento bovino a campo abierto:
    vacas lecheras Holstein pastoreando en sistema extensivo en Temuco,
    Chile, con acelerometro triaxial de cuello (Morales-Vargas et al. 2025,
    DOI 10.3389/fvets.2025.1630083, repo:
    github.com/WASP-lab/db-cow-walking). El propio paper senala que este
    tipo de sistema de pastoreo extensivo es el mismo que se usa en
    Argentina, Uruguay, Brasil, Australia y Nueva Zelanda, a diferencia de
    otros datasets similares colectados en sistemas confinados (EEUU,
    Espana) o con razas poco representativas del campo local (Japanese
    Black en Japon). De ahi se toman los ordenes de magnitud de amplitud y
    varianza de la aceleracion durante caminata/pastoreo (alta variabilidad)
    vs. reposo/rumia (senal estable) que calibran el generador.
  - Papers que documentan cuantitativamente los cambios fisiologicos durante
    el celo:
      * Actividad general: aumenta marcadamente durante el estro
        (comportamientos "activo" y "muy activo" suben, el reposo cae)
        [Frontiers, 2023, fanim.2023.1149085].
      * Rumia: se reduce durante el celo (consistente con el campo
        nivel_rumia de la entidad Telemetrias).
      * Collares comerciales basados en acelerometro de cuello (ej. RUMI)
        logran ~90-100% de sensibilidad/especificidad detectando este
        patron [Alvarez et al. 2025, DOI 10.3390/s25175245].
      * Duracion tipica del celo: 8-19hs dentro del ciclo estral (~21 dias).

Estos valores (factor de aumento de actividad, caida de rumia, duracion de la
ventana de celo dentro de las 24hs) son los parametros que calibran el
generador. Estan documentados en README_dataset.md junto con las fuentes.

Reproducibilidad: toda la aleatoriedad depende de --seed. Correr el script
con el mismo seed y los mismos parametros produce exactamente el mismo dataset.
"""

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd

SAMPLES_PER_DAY = 288  # 1 lectura cada 5 minutos durante 24hs
HOURS = np.linspace(0, 24, SAMPLES_PER_DAY, endpoint=False)


def circadian_activity(hours: np.ndarray) -> np.ndarray:
    """Nivel de actividad basal (0-1) segun hora del dia: mas actividad de
    dia (pastoreo), menos de noche (descanso/rumia)."""
    day_pattern = 0.5 + 0.5 * np.sin((hours - 6) / 24 * 2 * np.pi)
    day_pattern = np.clip(day_pattern, 0.05, 1.0)
    return day_pattern


def rumination_mask(hours: np.ndarray, rng: np.random.Generator, n_bouts: int, bout_len_h: float) -> np.ndarray:
    """Marca (booleana) los instantes en que el animal esta rumiando
    (baja varianza de movimiento)."""
    mask = np.zeros_like(hours, dtype=bool)
    for _ in range(n_bouts):
        start = rng.uniform(0, 24 - bout_len_h)
        mask |= (hours >= start) & (hours < start + bout_len_h)
    return mask


def simulate_day(rng: np.random.Generator, is_celo: bool) -> dict:
    activity = circadian_activity(HOURS)

    # Rumia: menos bouts y mas cortos durante celo
    if is_celo:
        rum_mask = rumination_mask(HOURS, rng, n_bouts=3, bout_len_h=0.6)
    else:
        rum_mask = rumination_mask(HOURS, rng, n_bouts=6, bout_len_h=1.0)

    # Amplitud de movimiento base
    amplitude = 0.15 + 0.35 * activity
    amplitude[rum_mask] *= 0.25  # durante rumia el movimiento es minimo

    if is_celo:
        # Ventana de celo dentro de las 24hs (8 a 19hs de duracion tipica)
        celo_start = rng.uniform(0, 24 - 8)
        celo_dur = rng.uniform(8, 12)
        celo_mask = (HOURS >= celo_start) & (HOURS < celo_start + celo_dur)
        amplitude[celo_mask] *= rng.uniform(1.8, 2.6)  # hiperactividad

    # Series x, y, z: gravedad + movimiento + ruido
    noise_x = rng.normal(0, 1, SAMPLES_PER_DAY) * amplitude
    noise_y = rng.normal(0, 1, SAMPLES_PER_DAY) * amplitude
    noise_z = rng.normal(0, 1, SAMPLES_PER_DAY) * amplitude

    accel_x = noise_x
    accel_y = noise_y
    accel_z = 1.0 + noise_z  # ~1g de gravedad en el eje vertical del collar/caravana

    # Picos de monta (transitorios de alta amplitud), solo si es celo
    if is_celo:
        n_spikes = rng.integers(3, 9)
        spike_idx = rng.choice(SAMPLES_PER_DAY, size=n_spikes, replace=False)
        spike_amp = rng.uniform(3.0, 5.5, size=n_spikes)
        accel_x[spike_idx] += rng.choice([-1, 1], n_spikes) * spike_amp
        accel_y[spike_idx] += rng.choice([-1, 1], n_spikes) * spike_amp

    nivel_rumia = float(np.clip(rum_mask.mean() * (0.6 if is_celo else 1.0), 0, 1))

    return {
        "accel_x": accel_x.astype(np.float32),
        "accel_y": accel_y.astype(np.float32),
        "accel_z": accel_z.astype(np.float32),
        "nivel_rumia": nivel_rumia,
    }


def build_dataset(n_animales: int, dias_por_animal: int, p_celo: float, seed: int):
    rng = np.random.default_rng(seed)

    X = np.zeros((n_animales * dias_por_animal, SAMPLES_PER_DAY, 3), dtype=np.float32)
    rows = []
    idx = 0
    start_date = pd.Timestamp("2026-01-01")

    for caravana_id in range(1, n_animales + 1):
        bateria = rng.uniform(85, 100)
        for d in range(dias_por_animal):
            is_celo = rng.random() < p_celo
            day = simulate_day(rng, is_celo)
            X[idx, :, 0] = day["accel_x"]
            X[idx, :, 1] = day["accel_y"]
            X[idx, :, 2] = day["accel_z"]

            bateria = max(0.0, bateria - rng.uniform(0.05, 0.3))
            rows.append(
                {
                    "sample_id": idx,
                    "caravana_id": caravana_id,
                    "fecha": (start_date + pd.Timedelta(days=d)).date().isoformat(),
                    "label": "celo" if is_celo else "no_celo",
                    "nivel_rumia_prom": round(day["nivel_rumia"], 4),
                    "bateria_prom": round(bateria, 2),
                }
            )
            idx += 1

    metadata = pd.DataFrame(rows)
    y = (metadata["label"] == "celo").astype(np.int8).to_numpy()
    return X, y, metadata


def main():
    parser = argparse.ArgumentParser(description="Genera el dataset sintetico de telemetria para deteccion de celo")
    parser.add_argument("--n-animales", type=int, default=60)
    parser.add_argument("--dias-por-animal", type=int, default=30)
    parser.add_argument("--p-celo", type=float, default=0.5, help="proporcion objetivo de dias etiquetados como celo")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out-dir", type=str, default="dataset")
    args = parser.parse_args()

    X, y, metadata = build_dataset(args.n_animales, args.dias_por_animal, args.p_celo, args.seed)

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    np.save(out_dir / "X.npy", X)
    np.save(out_dir / "y.npy", y)
    metadata.to_csv(out_dir / "metadata.csv", index=False)

    dist = metadata["label"].value_counts(normalize=True).round(4).to_dict()
    counts = metadata["label"].value_counts().to_dict()

    summary = {
        "n_samples": int(len(metadata)),
        "window_size": SAMPLES_PER_DAY,
        "sampling_interval_min": 5,
        "features": ["accel_x", "accel_y", "accel_z"],
        "class_counts": counts,
        "class_distribution": dist,
        "seed": args.seed,
        "params": {
            "n_animales": args.n_animales,
            "dias_por_animal": args.dias_por_animal,
            "p_celo": args.p_celo,
        },
    }
    with open(out_dir / "dataset_summary.json", "w") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    print(json.dumps(summary, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
