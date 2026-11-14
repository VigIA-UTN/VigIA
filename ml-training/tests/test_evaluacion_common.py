"""
test_evaluacion_common.py
=========================
Tests de las utilidades de la evaluación final multi-semilla (ML02):
agregación media ± desvío, hashes para la verificación del modelo
empaquetado y la salida en Markdown/figura. Sin modelos ni TensorFlow.
"""

import sys
import zipfile
from pathlib import Path

import numpy as np

ML_TRAINING_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ML_TRAINING_DIR))

import evaluacion_common as ec  # noqa: E402


def test_media_desvio_usa_desvio_muestral():
    m = ec.media_desvio([0.7, 0.8, 0.9])
    assert np.isclose(m["mean"], 0.8)
    assert np.isclose(m["std"], 0.1)  # n-1; el poblacional daría 0.0816


def test_media_desvio_con_una_sola_semilla_da_desvio_cero():
    assert ec.media_desvio([0.5]) == {"mean": 0.5, "std": 0.0}


def test_agregar_matrices_celda_a_celda():
    agg = ec.agregar_matrices([[[10, 2], [1, 5]], [[12, 0], [3, 3]]])
    assert agg["mean"] == [[11.0, 1.0], [2.0, 4.0]]
    assert np.allclose(agg["std"], [[np.sqrt(2)] * 2] * 2)


def test_hash_de_miembro_zip_ignora_el_resto_del_archivo(tmp_path):
    # Mismo model.weights.h5 con distinto metadata.json (como dos .keras
    # guardados en fechas distintas): distinto archivo, mismos pesos.
    a, b = tmp_path / "a.keras", tmp_path / "b.keras"
    for path, fecha in ((a, "2026-09-18"), (b, "2026-10-27")):
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("metadata.json", f'{{"date_saved": "{fecha}"}}')
            z.writestr("model.weights.h5", b"pesos")
    assert ec.sha256_archivo(a) != ec.sha256_archivo(b)
    assert ec.sha256_miembro_zip(a, "model.weights.h5") == ec.sha256_miembro_zip(b, "model.weights.h5")


def test_buscar_coincidencia():
    hashes = {42: "aaa", 43: "bbb"}
    assert ec.buscar_coincidencia("bbb", hashes) == 43
    assert ec.buscar_coincidencia("zzz", hashes) is None


def test_ultimo_run_toma_el_mas_reciente(tmp_path):
    for nombre in ("20260918_195223_split42", "20261027_105620_split42", "20260819_200232_split42"):
        (tmp_path / nombre).mkdir()
    (tmp_path / "latest.json").write_text("{}")
    assert ec.ultimo_run(tmp_path).name == "20261027_105620_split42"


def test_tabla_matriz_md():
    md = ec.tabla_matriz_md({"mean": [[1, 2], [3, 4]], "std": [[0, 0.5], [1, 0]]}, ["no", "sí"])
    assert md.splitlines()[2] == "| **no** | 1.0 ± 0.0 | 2.0 ± 0.5 |"


def test_graficar_matriz_genera_png(tmp_path):
    out = tmp_path / "cm.png"
    ec.graficar_matriz({"mean": [[10, 1], [2, 5]], "std": [[1, 0], [0, 1]]}, ["no", "sí"], "t", out)
    assert out.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n"
