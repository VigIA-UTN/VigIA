"""
RNF01 a 25.000 animales (defecto del Ciclo 1, fila 433): el bosque se guardó
con n_jobs=-1, así que cada /predict de una sola ventana levantaba un hilo por
núcleo para 600 árboles (~77 ms contra ~19 ms con un hilo). La salida de un
bosque no depende de n_jobs: las probabilidades fijadas acá son las del modelo
con n_jobs=-1.
"""
import warnings

import numpy as np
import pytest

import app.main as main_app
from app.features_salud import compute_baseline, compute_features


def _ventana(n, move_state):
    return ([move_state(i) for i in range(n)],
            [-34.0 + (i % 12) * 1e-5 for i in range(n)],
            [-60.0 + (i % 12) * 1e-5 for i in range(n)])


@pytest.fixture
def modelo_real():
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")  # InconsistentVersionWarning de sklearn
        main_app.load_ml_model()
    yield main_app.model
    main_app.model = None


def test_predice_con_un_hilo(modelo_real):
    assert modelo_real.n_jobs == 1


@pytest.mark.parametrize("move_state, esperado", [
    (lambda i: 3 + (i % 3), [0.5716666666666667, 0.06166666666666667, 0.14166666666666666, 0.225]),
    (lambda i: 0, [0.0033333333333333335, 0.01, 0.21, 0.7766666666666666]),
], ids=["normal", "quieta"])
def test_mismas_probabilidades(modelo_real, move_state, esperado):
    perfil = compute_baseline(*_ventana(7 * 288, lambda i: 3 + (i % 3)))
    X = np.array([compute_features(*_ventana(288, move_state), perfil)])
    np.testing.assert_allclose(modelo_real.predict_proba(X)[0], esperado, rtol=1e-12)
