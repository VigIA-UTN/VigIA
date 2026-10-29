"""
ML01 - Contrato de los microservicios ML: ml-celo.

Casos del bloque ML01 de la planilla de Casos de Prueba (hoja Ciclo 1). Cada
test lleva en el nombre el número de caso (test_ML01_NN_...). Los casos que
cubren los tres servicios (ML01-10 en adelante) tienen su parte en
test_contrato_ml01.py de ml-salud y de ml-geo.

El contrato es el que consume el backend-worker: CeloPredictResponseDTO
(evento_celo, confianza_modelo, ventana_optima_horas) y el formato de error
común {error, mensaje, trace_id}.
"""
import logging
import os
import sys
from unittest.mock import MagicMock

# pyrefly: ignore [missing-import]
import numpy as np
# pyrefly: ignore [missing-import]
import pytest
# pyrefly: ignore [missing-import]
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from app import main as main_app
from app.main import app

client = TestClient(app)

ENDPOINT = "/api/internal/ml/celo/predict"
SAMPLES_PER_DAY = 288

# Campos de CeloPredictResponseDTO (backend-core-lib) y su tipo en el JSON.
CONTRATO_PREDICT = {"evento_celo": bool, "confianza_modelo": float, "ventana_optima_horas": int}
CONTRATO_ERROR = {"error": str, "mensaje": str, "trace_id": str}


def assert_contrato(data: dict, contrato: dict, extra=()):
    """Mismas claves que el contrato (más las `extra` permitidas) y cada valor del tipo esperado."""
    assert set(data) - set(extra) == set(contrato)
    for campo, tipo in contrato.items():
        valor = data[campo]
        assert isinstance(valor, tipo), f"{campo}={valor!r} no es {tipo}"
        if tipo in (int, float):
            assert not isinstance(valor, bool), f"{campo} es bool, se esperaba {tipo}"


def serie_24h(move_state=3, paso=0.0):
    """Ventana de 24 hs a 5 min: el largo que exige ml-celo."""
    return [{"moveState": move_state, "latitud": -34.0 - i * paso, "longitud": -59.0 - i * paso}
            for i in range(SAMPLES_PER_DAY)]


@pytest.fixture
def modelo(monkeypatch):
    """Modelo cargado que devuelve la probabilidad que fije cada test, sin TensorFlow."""
    mock = MagicMock()
    mock.predict.return_value = np.array([[0.20]])
    monkeypatch.setattr(main_app, "model", mock)
    monkeypatch.setattr(main_app, "scaler", None)
    return mock


@pytest.fixture
def sin_modelo(monkeypatch):
    monkeypatch.setattr(main_app, "model", None)


# ---------------------------------------------------------------------------
# Contrato de /predict
# ---------------------------------------------------------------------------

def test_ML01_01_predict_celo_detectado_respeta_el_contrato(modelo):
    modelo.predict.return_value = np.array([[0.85]])

    response = client.post(ENDPOINT, json=serie_24h(9, paso=0.001))

    assert response.status_code == 200
    data = response.json()
    assert_contrato(data, CONTRATO_PREDICT)
    assert data["evento_celo"] is True
    assert data["confianza_modelo"] == pytest.approx(0.85)
    assert data["ventana_optima_horas"] == 12


def test_ML01_02_predict_sin_celo_respeta_el_contrato(modelo):
    modelo.predict.return_value = np.array([[0.20]])

    response = client.post(ENDPOINT, json=serie_24h(1))

    assert response.status_code == 200
    data = response.json()
    assert_contrato(data, CONTRATO_PREDICT)
    assert data["evento_celo"] is False
    assert data["confianza_modelo"] == pytest.approx(0.20)
    assert data["ventana_optima_horas"] == 0


@pytest.mark.parametrize("probabilidad, evento", [(0.946, False), (0.947, False), (0.948, True)])
def test_ML01_03_predict_decide_con_el_umbral_configurado(modelo, monkeypatch, probabilidad, evento):
    """CELO_THRESHOLD (0.947 en infra) es estricto: P(celo) igual al umbral no es celo."""
    monkeypatch.setattr(main_app, "CELO_THRESHOLD", 0.947)
    modelo.predict.return_value = np.array([[probabilidad]])

    data = client.post(ENDPOINT, json=serie_24h()).json()

    assert data["evento_celo"] is evento
    assert data["ventana_optima_horas"] == (12 if evento else 0)
    assert data["confianza_modelo"] == pytest.approx(probabilidad)


# ---------------------------------------------------------------------------
# Payload inválido: 422 con mensaje claro
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("payload, en_mensaje", [
    ([{"latitud": -34.0, "longitud": -59.0}], "body[0].moveState: campo obligatorio"),
    ([{"moveState": "alto", "latitud": -34.0, "longitud": -59.0}], "body[0].moveState: debe ser un número entero"),
    ({"serie": []}, "body: debe ser una lista"),
])
def test_ML01_10_payload_invalido_responde_422_con_mensaje_claro(modelo, payload, en_mensaje):
    response = client.post(ENDPOINT, json=payload, headers={"X-Correlation-ID": "ml01-10"})

    assert response.status_code == 422
    data = response.json()
    assert_contrato(data, CONTRATO_ERROR, extra=("detail",))
    assert data["error"] == "Payload Invalido"
    assert en_mensaje in data["mensaje"]
    assert data["trace_id"] == "ml01-10"


def test_ML01_10_json_mal_formado_responde_422(modelo):
    response = client.post(ENDPOINT, content=b"[{roto", headers={"Content-Type": "application/json"})

    assert response.status_code == 422
    assert "el cuerpo no es un JSON válido" in response.json()["mensaje"]


@pytest.mark.parametrize("campo, valor, en_mensaje", [
    ("moveState", 10, "body[0].moveState: debe ser menor o igual que 9"),
    ("moveState", -1, "body[0].moveState: debe ser mayor o igual que 0"),
    ("latitud", -91.0, "body[0].latitud: debe ser mayor o igual que -90"),
    ("longitud", 180.5, "body[0].longitud: debe ser menor o igual que 180"),
])
def test_ML01_11_valores_fuera_de_rango_responden_422(modelo, campo, valor, en_mensaje):
    serie = serie_24h()
    serie[0][campo] = valor

    response = client.post(ENDPOINT, json=serie)

    assert response.status_code == 422
    assert en_mensaje in response.json()["mensaje"]


def test_ML01_11_coordenada_no_finita_responde_422(modelo):
    """Python acepta NaN en el JSON de entrada; el 422 tiene que salir igual, no un 500."""
    cuerpo = b'[{"moveState": 5, "latitud": NaN, "longitud": -59.0}]'
    response = client.post(ENDPOINT, content=cuerpo, headers={"Content-Type": "application/json",
                                                              "X-Correlation-ID": "ml01-11"})

    assert response.status_code == 422
    assert "body[0].latitud: debe ser un número finito" in response.json()["mensaje"]
    assert response.headers["X-Correlation-ID"] == "ml01-11"


def test_ML01_11_valores_en_el_limite_del_rango_son_validos(modelo):
    serie = serie_24h()
    serie[0] = {"moveState": 0, "latitud": -90.0, "longitud": -180.0}
    serie[1] = {"moveState": 9, "latitud": 90.0, "longitud": 180.0}

    assert client.post(ENDPOINT, json=serie).status_code == 200


@pytest.mark.parametrize("puntos", [0, 1, 287, 289])
def test_ML01_12_serie_que_no_tiene_288_puntos_responde_422(modelo, puntos):
    serie = [{"moveState": 3, "latitud": -34.0, "longitud": -59.0}] * puntos

    response = client.post(ENDPOINT, json=serie)

    assert response.status_code == 422
    data = response.json()
    assert data["error"] == "Payload Invalido"
    assert data["mensaje"] == (f"serie: debe tener exactamente 288 puntos (24 hs a 5 min); "
                               f"se recibieron {puntos}")
    modelo.predict.assert_not_called()


# ---------------------------------------------------------------------------
# X-Correlation-ID
# ---------------------------------------------------------------------------

def test_ML01_13_devuelve_el_correlation_id_en_200_422_y_503(modelo, monkeypatch):
    headers = {"X-Correlation-ID": "ml01-13"}

    ok = client.post(ENDPOINT, json=serie_24h(), headers=headers)
    invalido = client.post(ENDPOINT, json=[{"latitud": 1}], headers=headers)
    monkeypatch.setattr(main_app, "model", None)
    caido = client.post(ENDPOINT, json=serie_24h(), headers=headers)

    assert [ok.status_code, invalido.status_code, caido.status_code] == [200, 422, 503]
    for r in (ok, invalido, caido):
        assert r.headers["X-Correlation-ID"] == "ml01-13"
    assert caido.json()["trace_id"] == "ml01-13"


def test_ML01_13_sin_correlation_id_responde_unknown(modelo):
    response = client.post(ENDPOINT, json=serie_24h())

    assert response.headers["X-Correlation-ID"] == "unknown"


def test_ML01_14_loguea_el_correlation_id_en_cada_linea(modelo, caplog):
    caplog.set_level(logging.INFO, logger="ml-celo")

    client.post(ENDPOINT, json=serie_24h(), headers={"X-Correlation-ID": "ml01-14-ok"})
    client.post(ENDPOINT, json=[{"latitud": 1}], headers={"X-Correlation-ID": "ml01-14-422"})

    ok = [r for r in caplog.records if r.correlation_id == "ml01-14-ok"]
    invalido = [r for r in caplog.records if r.correlation_id == "ml01-14-422"]
    assert any("Inferencia exitosa" in r.getMessage() for r in ok)
    assert any(r.getMessage() == f"POST {ENDPOINT} -> 200" for r in ok)
    assert any(r.levelno == logging.WARNING and "Payload inválido" in r.getMessage() for r in invalido)
    assert any(r.getMessage() == f"POST {ENDPOINT} -> 422" for r in invalido)


# ---------------------------------------------------------------------------
# Health y fallas del servicio
# ---------------------------------------------------------------------------

def test_ML01_15_health_responde_ok_con_el_modelo_cargado(modelo):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ml-celo", "model_loaded": True}


def test_ML01_16_health_sin_modelo_responde_503_degraded(sin_modelo):
    response = client.get("/health")

    assert response.status_code == 503
    assert response.json() == {"status": "degraded", "service": "ml-celo", "model_loaded": False}


def test_ML01_17_falla_del_servicio_responde_503_con_formato_comun(modelo):
    modelo.predict.side_effect = RuntimeError("fallo de inferencia")

    response = client.post(ENDPOINT, json=serie_24h(), headers={"X-Correlation-ID": "ml01-17"})

    assert response.status_code == 503
    data = response.json()
    assert_contrato(data, CONTRATO_ERROR)
    assert data["error"] == "Internal Server Error"
    assert "fallo de inferencia" in data["mensaje"]
    assert data["trace_id"] == "ml01-17"


def test_ML01_17_sin_modelo_predict_responde_503(sin_modelo):
    response = client.post(ENDPOINT, json=serie_24h())

    assert response.status_code == 503
    assert "El modelo de machine learning no está cargado" in response.json()["mensaje"]
