"""
test_gps_noise_and_movestate.py
================================
Guarda de regresión rápida (sin TensorFlow) para el ruido de medición GPS
y la cuantización de moveState agregados a ambos generadores de dataset
(ver ml-training/gps_noise_common.py y la investigación de causa raíz del
falso positivo de celo en un collar físicamente quieto). Si alguien borra
la llamada a inject_gps_noise/quantize_movestate, o cambia los defaults
sin querer, estos tests deberían fallar antes de que llegue a producción.
"""

import importlib.util
import sys
from pathlib import Path

import numpy as np
import pandas as pd

ML_TRAINING_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ML_TRAINING_DIR))

import gps_noise_common as gnc  # noqa: E402


def _load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


celo_gen = _load_module("celo_generate_dataset", ML_TRAINING_DIR / "celo" / "generate_dataset.py")
salud_gen = _load_module("salud_generate_dataset", ML_TRAINING_DIR / "salud" / "generate_dataset.py")

# Misma ancla empírica que ml-training/celo/calibracion_dataset.py
# (REAL_STATIONARY_CONSECUTIVE_DIST_SCALED): distancias reales entre fixes
# consecutivos de un collar quieto, en unidades escaladas (grados*100000).
REAL_STATIONARY_MEAN_SCALED = 24.7
RAYLEIGH_MEAN_FACTOR = 1.772


def _dist_scaled(lat: np.ndarray, lon: np.ndarray) -> np.ndarray:
    d = np.zeros_like(lat)
    d[:, 1:] = np.sqrt(np.diff(lat, axis=1) ** 2 + np.diff(lon, axis=1) ** 2) * 100000
    return d


class TestInjectGpsNoise:
    def test_std_zero_no_op(self):
        rng = np.random.default_rng(1)
        lat = np.full((3, 100), -34.5)
        lon = np.full((3, 100), -58.5)
        lat_obs, lon_obs = gnc.inject_gps_noise(lat, lon, 0.0, rng)
        assert np.array_equal(lat_obs, lat)
        assert np.array_equal(lon_obs, lon)
        # no-op no debe devolver la misma referencia (para no mutar la
        # trayectoria "verdadera" que llamó con el mismo array)
        assert lat_obs is not lat

    def test_magnitude_matches_real_sample(self):
        rng = np.random.default_rng(7)
        n_animales, T = 20, 2000
        lat = np.full((n_animales, T), -34.5)
        lon = np.full((n_animales, T), -58.5)
        lat_obs, lon_obs = gnc.inject_gps_noise(lat, lon, gnc.DEFAULT_GPS_NOISE_STD_M, rng)
        dist = _dist_scaled(lat_obs, lon_obs)
        mean_dist = dist[:, 1:].mean()
        # Tolerancia amplia (+-25%) dado que la ancla real es n=12.
        assert REAL_STATIONARY_MEAN_SCALED * 0.75 <= mean_dist <= REAL_STATIONARY_MEAN_SCALED * 1.25


class TestQuantizeMovestate:
    def test_blocks_have_single_value(self):
        rng = np.random.default_rng(3)
        move_state = rng.integers(0, 10, size=(4, 288))
        quantized = gnc.quantize_movestate(move_state, 6)
        n_animales, T = quantized.shape
        block_idx = np.arange(T) // 6
        for a in range(n_animales):
            df = pd.DataFrame({"block": block_idx, "ms": quantized[a]})
            assert df.groupby("block")["ms"].nunique().max() == 1

    def test_interval_one_is_identity(self):
        move_state = np.array([[0, 1, 2, 3, 4, 5]])
        quantized = gnc.quantize_movestate(move_state, 1)
        assert np.array_equal(quantized, move_state)

    def test_anchor_is_first_tick_of_block(self):
        move_state = np.array([[9, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0]])
        quantized = gnc.quantize_movestate(move_state, 6)
        assert (quantized[0, 0:6] == 9).all()
        assert (quantized[0, 6:12] == 3).all()


class TestGenerateDatasetSmoke:
    """Genera datasets chicos (rápido) para celo y salud y verifica que el
    ruido/cuantización se propagan correctamente hasta el DataFrame final."""

    def test_celo_dataset_noise_and_quantization(self):
        df, _ = celo_gen.build_dataset(n_animales=3, dias=1, seed=42)
        reposo = df.loc[(df["moveState"] <= 1), :].copy()
        reposo["dist"] = np.sqrt(
            reposo.groupby("caravana_id")["latitud"].diff() ** 2
            + reposo.groupby("caravana_id")["longitud"].diff() ** 2
        ) * 100000
        reposo = reposo["dist"].dropna()
        assert len(reposo) > 0
        mean_dist = reposo.mean()
        assert 0.4 * REAL_STATIONARY_MEAN_SCALED <= mean_dist <= 2.5 * REAL_STATIONARY_MEAN_SCALED

        block = df.groupby("caravana_id").cumcount() // gnc.DEFAULT_HEARTBEAT_INTERVAL_TICKS
        nunique = df.groupby(["caravana_id", block])["moveState"].nunique()
        assert nunique.max() == 1

        # latitud/longitud (observadas) deben diferir de latitud_true/longitud_true
        assert not np.allclose(df["latitud"], df["latitud_true"])

    def test_salud_dataset_noise_and_quantization(self):
        df, _ = salud_gen.build_dataset(n_animales=3, dias_evaluacion=1, seed=42)
        block = df.groupby("caravana_id").cumcount() // gnc.DEFAULT_HEARTBEAT_INTERVAL_TICKS
        nunique = df.groupby(["caravana_id", block])["moveState"].nunique()
        assert nunique.max() == 1
        assert not np.allclose(df["latitud"], df["latitud_true"])
