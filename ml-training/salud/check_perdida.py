"""
check_perdida.py (Salud)
========================
¿Rellenar los uplinks perdidos cambia la predicción del modelo de salud?

El worker (SaludDetectorStrategyImpl) rellena las ventanas cortas por slot
de 5 min antes de mandarlas a ml-salud: moveState repite la lectura
anterior, y lat/lon van en línea recta entre las lecturas de cada lado del
hueco (en los bordes, la más cercana). Este script simula pérdidas sobre el
dataset sintético (un tick = un slot), rellena igual y compara la clase que
predice el modelo servido contra la de la ventana completa. Mide
estabilidad, no accuracy: la referencia es la respuesta del propio modelo
sin pérdida. Para comparar, también rellena repitiendo la posición
anterior ("repetir") en vez de la recta.

  (a) pérdida en el día evaluado, con el perfil completo;
  (b) pérdida en la calibración (7 días), evaluando días completos.

Resultado (2026-10-29, 300 animales, 27.000 días): dentro de los límites del
worker (90 % de los slots con dato, ningún hueco de más de 1 h) rellenar
convierte en alerta a lo sumo el 0,32 % de los días sanos; a cambio, el
modelo deja pasar parte de los días con desvío (5 % con 2 % de pérdida,
20-25 % en los límites), que sin rellenar no se evaluaban nunca. La recta
para la posición pierde menos desvíos que repetir la anterior. Volver a
correrlo si se reentrena el modelo o cambia features_salud.py.

Uso, con las versiones de ml-salud (scikit-learn del modelo servido):
    python ml-training/salud/check_perdida.py [--n 300] [--seed 42]
"""

import argparse
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

from features_salud import CALIB_PUNTOS_MINIMO, SAMPLES_PER_DAY, compute_baseline, compute_features

RAIZ = Path(__file__).resolve().parents[2]
DATASET = RAIZ / "ml-training" / "salud" / "dataset" / "telemetria.parquet"
MODELO = RAIZ / "ml-salud" / "app" / "models" / "modelo_salud_rf.joblib"


def esparcida(p):
    return lambda n, rng: rng.random(n) >= p


def hueco(k):
    def llegaron(n, rng):
        m = np.ones(n, dtype=bool)
        inicio = rng.integers(0, n - k + 1)
        m[inicio:inicio + k] = False
        return m
    return llegaron


# Cada escenario devuelve la máscara de ticks que llegaron en una serie de n puntos.
ESCENARIOS = [
    ("sin perdida", lambda n, rng: np.ones(n, dtype=bool)),
    ("1 punto", hueco(1)),
    ("2 % esparcido", esparcida(0.02)),
    ("5 % esparcido", esparcida(0.05)),
    ("10 % esparcido", esparcida(0.10)),
    ("hueco 30 min", hueco(6)),
    ("hueco 1 h", hueco(12)),
    ("hueco 2 h", hueco(24)),
]


def rellenar(ms, lat, lon, llegaron, posicion):
    """Como el worker: moveState de la lectura anterior (los primeros slots, la
    primera lectura); lat/lon en línea recta entre vecinas ("recta", np.interp
    repite la más cercana en los bordes) o repitiendo la anterior ("repetir")."""
    i = np.arange(len(ms))
    anterior = np.maximum.accumulate(np.where(llegaron, i, -1))
    anterior[anterior < 0] = np.argmax(llegaron)
    if posicion == "recta":
        k = np.flatnonzero(llegaron)
        return ms[anterior], np.interp(i, k, lat[k]), np.interp(i, k, lon[k])
    return ms[anterior], lat[anterior], lon[anterior]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--n", type=int, default=300, help="Animales a usar (el dataset tiene 300).")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    modelo = joblib.load(MODELO)
    df = pd.read_parquet(DATASET, columns=["caravana_id", "tick", "moveState", "latitud", "longitud"])
    df = df.sort_values(["caravana_id", "tick"])

    # Por animal: los primeros 7 días son la calibración y cada 288 ticks siguientes, un día evaluado.
    animales = []
    for _, g in df.groupby("caravana_id"):
        if len(animales) == args.n:
            break
        ms, lat, lon = (g[c].to_numpy(dtype=np.float64) for c in ("moveState", "latitud", "longitud"))
        c = CALIB_PUNTOS_MINIMO
        dias = [(ms[s:s + SAMPLES_PER_DAY], lat[s:s + SAMPLES_PER_DAY], lon[s:s + SAMPLES_PER_DAY])
                for s in range(c, len(ms) - SAMPLES_PER_DAY + 1, SAMPLES_PER_DAY)]
        animales.append(((ms[:c], lat[:c], lon[:c]), dias))

    def predecir(filas):
        return modelo.predict(np.array(filas))

    perfiles = [compute_baseline(*calib) for calib, _ in animales]
    ref = predecir([compute_features(*dia, perfil)
                    for (_, dias), perfil in zip(animales, perfiles) for dia in dias])
    sanos = ref == 0
    print(f"{len(animales)} animales, {len(ref)} días evaluados: {sanos.sum()} sanos y "
          f"{(~sanos).sum()} con desvío según el modelo sin pérdida.\n")
    print("parte  escenario        posición  cambia %  sano→desvío %  desvío→sano %  falsas ≤ 1 %")

    def reportar(parte, nombre, posicion, pred):
        falsas = np.mean(pred[sanos] > 0) * 100
        print(f"{parte:<6} {nombre:<16} {posicion:<9} {np.mean(pred != ref) * 100:8.2f} "
              f"{falsas:14.2f} {np.mean(pred[~sanos] == 0) * 100:14.2f}  {'sí' if falsas <= 1 else 'NO'}")

    for nombre, llegaron in ESCENARIOS:
        for posicion in ("recta", "repetir"):
            # La misma semilla para las dos posiciones: comparan las mismas pérdidas.
            rng = np.random.default_rng(args.seed)
            filas = [compute_features(*rellenar(*dia, llegaron(SAMPLES_PER_DAY, rng), posicion), perfil)
                     for (_, dias), perfil in zip(animales, perfiles) for dia in dias]
            reportar("(a)", nombre, posicion, predecir(filas))

            rng = np.random.default_rng(args.seed)
            filas = []
            for calib, dias in animales:
                perfil = compute_baseline(*rellenar(*calib, llegaron(CALIB_PUNTOS_MINIMO, rng), posicion))
                filas += [compute_features(*dia, perfil) for dia in dias]
            reportar("(b)", nombre, posicion, predecir(filas))


if __name__ == "__main__":
    main()
