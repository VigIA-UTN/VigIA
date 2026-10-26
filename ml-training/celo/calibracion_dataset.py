"""
calibracion_dataset.py
=====================
Compara el simulador de VigIA (generate_dataset.py) contra el dataset
público de comportamiento bovino a campo abierto de Chile
(Morales-Vargas et al. 2025, db-cow-walking), para documentar con
rigor estadístico la sub-sección "Calibración empírica del simulador".

Dos comparaciones, con el nivel de rigor que los datos disponibles
realmente permiten (el dataset público NO trae GPS/velocidad ni un
estado moveState 0-9; trae acelerometría cruda + etiqueta de
comportamiento por evento):

(A) Presupuesto de tiempo por estado de actividad: se compara la
    proporción de tiempo en reposo/actividad normal/alta actividad
    del simulador (moveState 0-1 / 2-6 / 7-9) contra la proporción de
    tiempo Reposo/Pastoreo/Caminata+Otros reportada empíricamente en
    Chile, mediante un test chi-cuadrado de bondad de ajuste.

(B) Separabilidad ordinal de la intensidad de movimiento: se calcula,
    para cada evento etiquetado del dataset público, una métrica de
    intensidad de movimiento a partir de la acelerometría cruda (SD de
    la magnitud de aceleración dinámica, MPU9250 body-frame), y se
    verifica con test de Kolmogorov-Smirnov que Reposo < Pastoreo <
    Caminata son distribuciones distinguibles (real). Se compara este
    ordenamiento contra el mismo ordenamiento que produce el simulador
    en su proxy de intensidad (distancia recorrida / moveState).
"""

import argparse
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

# pyrefly: ignore [missing-import]
import numpy as np
import pandas as pd
# pyrefly: ignore [missing-import]
from scipy import stats

BASE_REPO_URL = "https://github.com/WASP-lab/db-cow-walking.git"

# Rutas por defecto relativas a este script (ml-training/celo/), para que
# funcione igual en cualquier máquina/checkout del repo sin editar nada.
SCRIPT_DIR = Path(__file__).resolve().parent
SIM_DATASET = SCRIPT_DIR / "dataset" / "telemetria.parquet"
RESULTS_OUT = SCRIPT_DIR / "calibracion_resultados.json"

BEHAVIOR_DIRS = {
    "Resting": "Resting",
    "Grazing": "Grazing",
    "Walking": "Walking",
    "Misc": "Miscellaneous behaviors",
}

# ----------------------------------------------------------------------
# Validación del piso de ruido GPS (ver investigación de causa raíz del
# falso positivo de celo): derivado de una muestra real de 12 lecturas
# consecutivas de una caravana físicamente quieta. Distancias entre fixes
# consecutivos en unidades "escaladas" (grados*100000, mismo criterio que
# DIST_SCALE_FACTOR en ml-celo/app/main.py y ml-salud/app/features_salud.py).
# n=12, una sola caravana -- ancla empírica débil pero es la única
# disponible; agregar más muestras reales acá si se consiguen.
REAL_STATIONARY_CONSECUTIVE_DIST_SCALED = np.array(
    [37.4, 20.3, 4.27, 31.6, 15.19, 16.63, 24.8, 37.3, 33.4, 23.5, 27.6]
)
# Para dos fixes con ruido gaussiano i.i.d. por eje (sigma por eje), la
# distancia esperada entre ellos sigue una Rayleigh de escala sqrt(2)*sigma,
# con media = sqrt(2)*sigma*sqrt(pi/2) = sigma*sqrt(pi) ~ 1.772*sigma.
RAYLEIGH_MEAN_FACTOR = 1.772


def load_intensity_for_behavior(behavior_dir: Path):
    """Para cada evento (csv), calcula SD de la magnitud de aceleración
    dinámica (MPU9250 body-frame) como proxy de intensidad de movimiento."""
    intensities = []
    for csv_path in behavior_dir.glob("*.csv"):
        try:
            df = pd.read_csv(csv_path, usecols=["MPU9250_AX", "MPU9250_AY", "MPU9250_AZ"])
        except Exception:
            continue
        if len(df) < 5:
            continue
        mag = np.sqrt(df["MPU9250_AX"] ** 2 + df["MPU9250_AY"] ** 2 + df["MPU9250_AZ"] ** 2)
        # aceleración dinámica = magnitud - componente estática (gravedad ~ media del evento)
        dyn = mag - mag.mean()
        intensities.append(float(dyn.std()))
    return np.array(intensities)


def clone_base_dataset(dest_dir: Path):
    """Clona (shallow) el dataset público a dest_dir."""
    print(f"Clonando {BASE_REPO_URL} (--depth 1) en {dest_dir} ...")
    subprocess.run(
        ["git", "clone", "--depth", "1", BASE_REPO_URL, str(dest_dir)],
        check=True,
    )


def _sort_col_for(sim_dataset_path: Path) -> str:
    """celo/generate_dataset.py ordena por 'fecha_hora'; salud/generate_dataset.py
    (mismo formato de parquet, otro generador) usa 'tick' -- detectar cuál
    columna trae el dataset para poder correr este mismo script contra
    cualquiera de los dos sin código separado."""
    import pyarrow.parquet as pq
    schema_cols = pq.ParquetFile(sim_dataset_path).schema.names
    return "fecha_hora" if "fecha_hora" in schema_cols else "tick"


def _load_sim_dataset_sorted(sim_dataset_path: Path) -> pd.DataFrame:
    sort_col = _sort_col_for(sim_dataset_path)
    df = pd.read_parquet(sim_dataset_path, columns=["caravana_id", sort_col, "latitud", "longitud", "moveState"])
    return df.sort_values(["caravana_id", sort_col])


def _consecutive_dist_scaled(df_sorted: pd.DataFrame) -> pd.Series:
    """Distancia euclídea entre fixes GPS consecutivos por caravana, en las
    mismas unidades 'escaladas' (grados*100000) que usan ml-celo/app/main.py
    y ml-salud/app/features_salud.py."""
    dist = np.sqrt(
        df_sorted.groupby("caravana_id")["latitud"].diff() ** 2
        + df_sorted.groupby("caravana_id")["longitud"].diff() ** 2
    ) * 100000
    return dist.fillna(0)


def validate_gps_noise_floor(sim_dataset_path: Path) -> dict:
    """Compara el piso de ruido GPS del dataset regenerado (distancia media
    entre fixes consecutivos EN TICKS DE REPOSO, moveState<=1) contra la
    muestra real de una caravana físicamente quieta. Gate tolerante (n=12,
    ver comentario en REAL_STATIONARY_CONSECUTIVE_DIST_SCALED): si esto
    falla en el futuro (ej. alguien borra la llamada a inject_gps_noise en
    generate_dataset.py), es una señal fuerte de que el ruido dejó de estar
    presente y el falso positivo original podría reaparecer."""
    df_sorted = _load_sim_dataset_sorted(sim_dataset_path)
    df_sorted["dist"] = _consecutive_dist_scaled(df_sorted)

    reposo = df_sorted.loc[df_sorted["moveState"] <= 1, "dist"]
    # El primer tick de cada caravana no tiene "anterior" (diff -> 0 tras
    # fillna) y no es ruido real; se excluye para no sesgar la media a la baja.
    reposo = reposo[reposo > 0]

    sim_mean = float(reposo.mean()) if len(reposo) else 0.0
    real_mean = float(REAL_STATIONARY_CONSECUTIVE_DIST_SCALED.mean())
    sim_sigma_implicito = sim_mean / RAYLEIGH_MEAN_FACTOR
    real_sigma_implicito = real_mean / RAYLEIGH_MEAN_FACTOR
    ratio = sim_mean / real_mean if real_mean else float("inf")

    ks_stat, ks_p = stats.ks_2samp(REAL_STATIONARY_CONSECUTIVE_DIST_SCALED, reposo.to_numpy())

    ok = 0.5 <= ratio <= 2.0
    print(f"\n=== Piso de ruido GPS (ticks en reposo, moveState<=1) ===")
    print(f"  Real (n=12, una caravana quieta): media={real_mean:.2f} (sigma implícito~{real_sigma_implicito:.2f})")
    print(f"  Simulado (n={len(reposo)}): media={sim_mean:.2f} (sigma implícito~{sim_sigma_implicito:.2f})")
    print(f"  Ratio simulado/real: {ratio:.2f} (gate: 0.5-2.0) -> {'OK' if ok else 'FALLA'}")
    print(f"  KS-test (informativo, n real=12 -> poder estadístico bajo): stat={ks_stat:.4f}, p={ks_p:.2e}")

    return {
        "n_real": int(len(REAL_STATIONARY_CONSECUTIVE_DIST_SCALED)),
        "n_simulado": int(len(reposo)),
        "media_real": real_mean,
        "media_simulada": sim_mean,
        "sigma_implicito_real": real_sigma_implicito,
        "sigma_implicito_simulado": sim_sigma_implicito,
        "ratio_sim_over_real": ratio,
        "ks_stat": float(ks_stat),
        "ks_p_value": float(ks_p),
        "ok": bool(ok),
    }


def run_analysis(base_dir: Path, sim_dataset_path: Path = SIM_DATASET):
    results = {}

    print("=== Cargando dataset base (db-cow-walking) ===")
    per_behavior_intensity = {}
    per_behavior_n_events = {}
    per_behavior_total_seconds = {}
    for label, dirname in BEHAVIOR_DIRS.items():
        d = base_dir / dirname
        files = list(d.glob("*.csv"))
        per_behavior_n_events[label] = len(files)
        total_rows = 0
        for f in files:
            with open(f) as fh:
                total_rows += sum(1 for _ in fh) - 1
        per_behavior_total_seconds[label] = total_rows / 10.0  # 10 Hz
        per_behavior_intensity[label] = load_intensity_for_behavior(d)
        print(f"  {label}: {len(files)} eventos, "
              f"{per_behavior_total_seconds[label]/60:.1f} min, "
              f"intensidad media={per_behavior_intensity[label].mean():.3f} "
              f"(sd={per_behavior_intensity[label].std():.3f})")

    total_seconds_all = sum(per_behavior_total_seconds.values())
    real_time_budget = {k: v / total_seconds_all for k, v in per_behavior_total_seconds.items()}
    print(f"\nPresupuesto de tiempo real: {json.dumps(real_time_budget, indent=2)}")

    # ------------------------------------------------------------------
    # (A) Chi-cuadrado de bondad de ajuste: presupuesto de tiempo
    # ------------------------------------------------------------------
    print("\n=== Cargando dataset simulado VigIA ===")
    sim = pd.read_parquet(sim_dataset_path, columns=["moveState"])
    n_total = len(sim)
    n_reposo = int(((sim["moveState"] >= 0) & (sim["moveState"] <= 1)).sum())
    n_normal = int(((sim["moveState"] >= 2) & (sim["moveState"] <= 6)).sum())
    n_alta = int(((sim["moveState"] >= 7) & (sim["moveState"] <= 9)).sum())
    sim_time_budget = {
        "Resting": n_reposo / n_total,
        "Grazing_Walking": n_normal / n_total,
        "Alta_actividad": n_alta / n_total,
    }
    print(f"Presupuesto de tiempo simulado (VigIA moveState): {json.dumps(sim_time_budget, indent=2)}")

    # Mapeo de categorías comparables: Reposo <-> Reposo (0-1);
    # (Pastoreo+Caminata+Otros) real <-> (normal 2-6 + alta 7-9) simulado.
    # No son categorías idénticas (ver limitaciones en el texto), pero es
    # el mapeo más razonable dado que el simulador solo tiene 3 franjas
    # de intensidad, mientras el dataset base reporta 4 comportamientos.
    real_reposo = real_time_budget["Resting"]
    real_actividad = 1 - real_reposo  # Grazing+Walking+Misc

    sim_reposo = sim_time_budget["Resting"]
    sim_actividad = sim_time_budget["Grazing_Walking"] + sim_time_budget["Alta_actividad"]

    # Chi-cuadrado de bondad de ajuste: observado = simulador, esperado = proporciones reales
    observed = np.array([n_reposo, n_normal + n_alta])
    expected_prop = np.array([real_reposo, real_actividad])
    expected = expected_prop * observed.sum()
    chi2, p_value = stats.chisquare(f_obs=observed, f_exp=expected)

    print(f"\nChi-cuadrado (reposo vs actividad, real vs simulado): "
          f"chi2={chi2:.2f}, p={p_value:.2e}")

    results["time_budget"] = {
        "real_base": real_time_budget,
        "simulado_vigia": sim_time_budget,
        "real_reposo_vs_actividad": {"reposo": real_reposo, "actividad": real_actividad},
        "simulado_reposo_vs_actividad": {"reposo": sim_reposo, "actividad": sim_actividad},
        "chi2": chi2,
        "p_value": p_value,
    }

    # ------------------------------------------------------------------
    # (B) KS-test: separabilidad ordinal Reposo < Pastoreo < Caminata (real)
    # ------------------------------------------------------------------
    print("\n=== KS-test intensidad de movimiento (dataset real) ===")
    ks_results = {}
    pairs = [("Resting", "Grazing"), ("Grazing", "Walking"), ("Resting", "Walking")]
    for a, b in pairs:
        stat_, p_ = stats.ks_2samp(per_behavior_intensity[a], per_behavior_intensity[b])
        ks_results[f"{a}_vs_{b}"] = {"ks_stat": float(stat_), "p_value": float(p_)}
        print(f"  {a} vs {b}: KS={stat_:.4f}, p={p_:.2e}")

    medians = {k: float(np.median(v)) for k, v in per_behavior_intensity.items()}
    print(f"\nMedianas de intensidad (real): {medians}")
    ordinal_ok_real = medians["Resting"] < medians["Grazing"] < medians["Walking"]
    print(f"Orden Reposo < Pastoreo < Caminata (real): {ordinal_ok_real}")

    # Proxy simulado de intensidad: distancia recorrida media por franja de moveState
    print("\n=== Proxy de intensidad simulado (VigIA) ===")
    sim_full = _load_sim_dataset_sorted(sim_dataset_path)
    sim_full["dist"] = _consecutive_dist_scaled(sim_full)
    dist_por_franja = {
        "Reposo(0-1)": float(sim_full.loc[sim_full["moveState"] <= 1, "dist"].mean()),
        "Normal(2-6)": float(sim_full.loc[(sim_full["moveState"] >= 2) & (sim_full["moveState"] <= 6), "dist"].mean()),
        "AltaActividad(7-9)": float(sim_full.loc[sim_full["moveState"] >= 7, "dist"].mean()),
    }
    print(f"Distancia recorrida media por franja (simulado): {dist_por_franja}")
    ordinal_ok_sim = dist_por_franja["Reposo(0-1)"] < dist_por_franja["Normal(2-6)"] < dist_por_franja["AltaActividad(7-9)"]
    print(f"Orden Reposo < Normal < Alta actividad (simulado): {ordinal_ok_sim}")

    results["ordinal_intensity"] = {
        "real_ks_tests": ks_results,
        "real_medianas": medians,
        "orden_correcto_real": ordinal_ok_real,
        "simulado_distancia_media_por_franja": dist_por_franja,
        "orden_correcto_simulado": ordinal_ok_sim,
    }

    results["n_eventos_por_comportamiento"] = per_behavior_n_events
    results["minutos_por_comportamiento"] = {k: v / 60 for k, v in per_behavior_total_seconds.items()}

    return results


def main():
    parser = argparse.ArgumentParser(
        description="Calibración empírica del simulador VigIA. El chequeo de piso de ruido GPS "
                     "(rápido, sin red) corre siempre; la comparación contra el dataset público "
                     "db-cow-walking (clona a una carpeta temporal, la borra al terminar) es opcional."
    )
    parser.add_argument("--sim-dataset", type=Path, default=SIM_DATASET,
                         help=f"Parquet de telemetría simulada a validar (default: {SIM_DATASET}). "
                              "Sirve tanto para el dataset de celo (columna fecha_hora) como el de "
                              "salud (columna tick) -- se detecta automáticamente.")
    parser.add_argument("--skip-external-comparison", action="store_true",
                         help="No clonar/comparar contra el dataset público db-cow-walking; correr "
                              "sólo el chequeo (rápido, sin red) de piso de ruido GPS. Útil como gate "
                              "previo al deploy.")
    parser.add_argument("--keep-dataset", action="store_true",
                         help="No borrar la carpeta temporal con el dataset clonado al finalizar "
                              "(útil para inspeccionar los CSV a mano o iterar sin re-clonar). "
                              "Sin efecto si se pasa --skip-external-comparison.")
    parser.add_argument("--out", type=Path, default=RESULTS_OUT,
                         help=f"Dónde guardar el JSON de resultados (default: {RESULTS_OUT})")
    args = parser.parse_args()

    results = {"gps_noise_floor": validate_gps_noise_floor(args.sim_dataset)}

    if not args.skip_external_comparison:
        tmp_dir = Path(tempfile.mkdtemp(prefix="db-cow-walking-"))
        try:
            clone_base_dataset(tmp_dir)
            results.update(run_analysis(tmp_dir, sim_dataset_path=args.sim_dataset))
        finally:
            if args.keep_dataset:
                print(f"\n--keep-dataset activo: el dataset clonado queda en {tmp_dir}")
            else:
                shutil.rmtree(tmp_dir, ignore_errors=True)
                print(f"\nCarpeta temporal del dataset público borrada ({tmp_dir}). Repo limpio.")
    else:
        print("\n--skip-external-comparison activo: se omite la comparación contra db-cow-walking.")

    with open(args.out, "w") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    print(f"\nResultados guardados en {args.out}")

    if not results["gps_noise_floor"]["ok"]:
        print("\nERROR: el piso de ruido GPS del dataset simulado no es realista "
              "(ver 'gps_noise_floor' en el JSON de resultados).")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
