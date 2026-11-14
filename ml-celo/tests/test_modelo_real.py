"""
RNF01 a 25.000 animales (defecto del Ciclo 1, fila 433): model.predict() arma un
pipeline de datos en cada llamada; con una sola ventana eso es casi todo el costo
(~41 ms contra ~5 ms de predict_on_batch). Estos tests usan el modelo real y fijan
la confianza de dos ventanas como las de loadtest/trayectoria.sql, tomada con
model.predict() antes del cambio.
"""
import math
import random
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

import app.main as main_app

ENDPOINT = "/api/internal/ml/celo/predict"


def ventana(tipo, seed=0, d=3, t0=5_900_000):
    """24 h de una vaca según loadtest/trayectoria.sql (carga_dlat/dlon, carga_salto, carga_move_state)."""
    rnd = random.Random(seed)
    serie = []
    for k in range(288):
        lat, lon = (d // 10 - 2) * 0.0003, (d % 10 - 4.5) * 0.0004
        if tipo == "celo":
            if k % 2 == 1:
                s, a = 0.000018 * (2.5 + 1.5 * rnd.random()), 2 * math.pi * rnd.random()
                lat, lon = lat + s * math.cos(a), lon + s * math.sin(a)
            move_state = 7 + int(rnd.random() * 3)
        else:
            fase1, fase2 = 2 * math.pi * (t0 + k) / 87.6 + d * 0.37, 2 * math.pi * (t0 + k) / 34.8 + d * 1.13
            lat += 0.000195 * math.sin(fase1) + 0.0000443 * math.sin(fase2)
            lon += 0.000195 * math.cos(fase1) + 0.0000443 * math.cos(fase2)
            r = rnd.random()
            move_state = (int(rnd.random() * 2) if r < 0.35
                          else 2 + int(rnd.random() * 5) if r < 0.95 else 7 + int(rnd.random() * 3))
        serie.append({"moveState": move_state, "latitud": -34.0 + lat, "longitud": -59.0 + lon})
    return serie


@pytest.fixture(scope="module")
def cliente():
    with TestClient(main_app.app) as c:  # el lifespan carga el modelo real
        assert main_app.model is not None
        yield c


@pytest.mark.parametrize("tipo, confianza", [("celo", 0.9500709772109985), ("normal", 0.184818834066391)])
def test_misma_confianza(cliente, tipo, confianza):
    respuesta = cliente.post(ENDPOINT, json=ventana(tipo))
    assert respuesta.status_code == 200
    assert respuesta.json()["confianza_modelo"] == pytest.approx(confianza, abs=1e-5)


def test_no_usa_predict(cliente):
    with patch.object(main_app.model, "predict", side_effect=AssertionError("predict() arma un pipeline por llamada")):
        assert cliente.post(ENDPOINT, json=ventana("normal")).status_code == 200
