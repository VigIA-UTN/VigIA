"""
evaluacion_common.py
====================
Utilidades compartidas por la evaluación final multi-semilla de celo y salud
(celo/evaluar_final.py y salud/evaluar_final.py, caso ML02 de la planilla de
Casos de Prueba): agregación media ± desvío entre semillas, hashes para
verificar que el modelo empaquetado en el microservicio es uno de los
evaluados, y la salida (JSON, resumen en Markdown y figura de la matriz de
confusión) que va a ml-training/resultados_finales/.

Sin TensorFlow: se puede importar y testear en el entorno liviano.
"""

import hashlib
import json
import platform
import sys
import zipfile
from datetime import datetime
from pathlib import Path

import numpy as np

ML_TRAINING_DIR = Path(__file__).resolve().parent
RESULTADOS_DIR = ML_TRAINING_DIR / "resultados_finales"


def media_desvio(valores):
    """Media y desvío estándar MUESTRAL (n-1) entre semillas.

    Con 5 corridas, el desvío poblacional (n) subestima la variabilidad; los
    summary_5seeds.json del entrenamiento usan n, por eso los desvíos de acá
    son algo mayores que los de esos archivos."""
    arr = np.asarray(valores, dtype=float)
    std = float(arr.std(ddof=1)) if arr.size > 1 else 0.0
    return {"mean": float(arr.mean()), "std": std}


def agregar_matrices(matrices):
    """Media y desvío (n-1) celda a celda de las matrices de confusión."""
    arr = np.asarray(matrices, dtype=float)
    std = arr.std(axis=0, ddof=1) if arr.shape[0] > 1 else np.zeros(arr.shape[1:])
    return {"mean": arr.mean(axis=0).tolist(), "std": std.tolist()}


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_archivo(path: Path) -> str:
    return sha256_bytes(Path(path).read_bytes())


def sha256_miembro_zip(path: Path, miembro: str) -> str:
    """Hash de un archivo dentro de un zip. Un .keras es un zip cuyo
    metadata.json guarda la fecha de guardado: dos copias del mismo modelo
    tienen distinto hash de archivo pero el mismo model.weights.h5."""
    with zipfile.ZipFile(path) as z:
        return sha256_bytes(z.read(miembro))


def buscar_coincidencia(hash_servicio: str, hashes_por_semilla: dict):
    """Devuelve la semilla cuyo hash coincide con el del servicio, o None."""
    for seed, h in hashes_por_semilla.items():
        if h == hash_servicio:
            return seed
    return None


def versiones(*modulos):
    v = {"python": platform.python_version()}
    for nombre in modulos:
        try:
            v[nombre] = __import__(nombre).__version__
        except Exception:  # pragma: no cover - solo informativo
            v[nombre] = None
    return v


def ruta_legible(path: Path) -> str:
    """Ruta relativa a la raíz del repo si está adentro; si no, absoluta."""
    path = Path(path).resolve()
    try:
        return path.relative_to(ML_TRAINING_DIR.parent).as_posix()
    except ValueError:
        return str(path)


def comando_invocado() -> str:
    return "python " + " ".join([Path(sys.argv[0]).name] + sys.argv[1:])


def ultimo_run(models_dir: Path) -> Path:
    """Run de entrenamiento más reciente (los nombres empiezan con la fecha)."""
    runs = sorted(p for p in Path(models_dir).iterdir() if p.is_dir())
    if not runs:
        raise FileNotFoundError(f"No hay runs entrenados en {models_dir}")
    return runs[-1]


def fmt(m, decimales=3):
    return f"{m['mean']:.{decimales}f} ± {m['std']:.{decimales}f}"


def tabla_matriz_md(agg, etiquetas, decimales=1):
    """Matriz de confusión media ± desvío como tabla Markdown (filas = real)."""
    lineas = ["| Real \\ Predicho | " + " | ".join(etiquetas) + " |",
              "|---" * (len(etiquetas) + 1) + "|"]
    for i, et in enumerate(etiquetas):
        celdas = [f"{agg['mean'][i][j]:.{decimales}f} ± {agg['std'][i][j]:.{decimales}f}"
                  for j in range(len(etiquetas))]
        lineas.append(f"| **{et}** | " + " | ".join(celdas) + " |")
    return "\n".join(lineas)


def graficar_matriz(agg, etiquetas, titulo, path: Path):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    mean = np.asarray(agg["mean"], dtype=float)
    std = np.asarray(agg["std"], dtype=float)
    # Normalizada por fila (recall por clase) para el color; el texto lleva
    # los conteos medios ± desvío.
    filas = mean.sum(axis=1, keepdims=True)
    norm = np.divide(mean, filas, out=np.zeros_like(mean), where=filas > 0)

    fig, ax = plt.subplots(figsize=(1.6 * len(etiquetas) + 2, 1.4 * len(etiquetas) + 1.5))
    ax.imshow(norm, cmap="Blues", vmin=0, vmax=1)
    for i in range(len(etiquetas)):
        for j in range(len(etiquetas)):
            ax.text(j, i, f"{mean[i, j]:.1f}\n± {std[i, j]:.1f}", ha="center", va="center",
                    color="white" if norm[i, j] > 0.5 else "black", fontsize=10)
    ax.set_xticks(range(len(etiquetas)), etiquetas)
    ax.set_yticks(range(len(etiquetas)), etiquetas)
    ax.set_xlabel("Predicho")
    ax.set_ylabel("Real")
    ax.set_title(titulo)
    fig.tight_layout()
    path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(path, dpi=150)
    plt.close(fig)


def guardar_json(data, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    data = {"generado": datetime.now().isoformat(timespec="seconds"), **data}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
