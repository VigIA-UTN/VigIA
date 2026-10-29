"""
ML01 - Contrato de los microservicios ML: ml-salud.

Casos del bloque ML01 de la planilla de Casos de Prueba (hoja Ciclo 1). Cada
test lleva en el nombre el número de caso (test_ML01_NN_...). Los casos que
cubren los tres servicios (ML01-10 en adelante) tienen su parte en
test_contrato_ml01.py de ml-celo y de ml-geo.

El contrato es el que consume el backend-worker: SaludPredictResponseDTO
(desvio_detectado, severidad, posible_causa, confianza_modelo),
PerfilCalibracionResponseDTO ({perfil}) y el formato de error común
{error, mensaje, trace_id}.
"""
import logging
from unittest.mock import MagicMock

import numpy as np
import pytest
from fastapi.testclient import TestClient

import app.main as main_app
from app.main import app
from app.features_salud import compute_baseline, FEATURE_VERSION

client = TestClient(app)

PREDICT = "/api/internal/ml/salud/predict"
PERFIL = "/api/internal/ml/salud/perfil"
SAMPLES_PER_DAY = 288
CALIB_DIAS = 7

# Campos de SaludPredictResponseDTO (backend-core-lib) y su tipo en el JSON.
CONTRATO_PREDICT = {"desvio_detectado": bool, "severidad": int,
                    "posible_causa": (str, type(None)), "confianza_modelo": float}
CONTRATO_ERROR = {"error": str, "mensaje": str, "trace_id": str}


def assert_contrato(data: dict, contrato: dict, extra=()):
    """Mismas claves que el contrato (más las `extra` permitidas) y cada valor del tipo esperado."""
    assert set(data) - set(extra) == set(contrato)
    for campo, tipo in contrato.items():
        valor = data[campo]
        assert isinstance(valor, tipo), f"{campo}={valor!r} no es {tipo}"
        if tipo in (int, float):
            assert not isinstance(valor, bool), f"{campo} es bool, se esperaba {tipo}"


def _punto(i, move_state=3):
    return {"moveState": move_state,
            "latitud": -34.0 + (i % 12) * 0.00001,
            "longitud": -60.0 + (i % 12) * 0.00001}


def serie_24h():
    return [_punto(i) for i in range(SAMPLES_PER_DAY)]


def calibracion(dias=CALIB_DIAS):
    return [_punto(i) for i in range(dias * SAMPLES_PER_DAY)]


def perfil_valido():
    """Perfil tal como lo devuelve /perfil, armado sin pasar por el endpoint."""
    calib = calibracion()
    return compute_baseline([p["moveState"] for p in calib], [p["latitud"] for p in calib],
                            [p["longitud"] for p in calib])


@pytest.fixture
def modelo(monkeypatch):
    """RandomForest cargado; cada test fija predict_proba."""
    mock = MagicMock()
    mock.classes_ = np.array([0, 1, 2, 3])
    mock.predict_proba.return_value = np.array([[0.9, 0.05, 0.03, 0.02]])
    monkeypatch.setattr(main_app, "model", mock)
    return mock


@pytest.fixture
def sin_modelo(monkeypatch):
    monkeypatch.setattr(main_app, "model", None)


# ---------------------------------------------------------------------------
# Contrato de /predict y /perfil
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("severidad, desvio, causa", [
    (0, False, None),
    (1, True, "Enfermedad leve / Inicio"),
    (2, True, "Enfermedad moderada / Cojera clínica"),
    (3, True, "Problema severo postrante o complicación de parto"),
])
def test_ML01_04_predict_respeta_el_contrato_para_cada_severidad(modelo, severidad, desvio, causa):
    proba = [0.02] * 4
    proba[severidad] = 0.94
    modelo.predict_proba.return_value = np.array([proba])

    response = client.post(PREDICT, json={"serie": serie_24h(), "perfil": perfil_valido()})

    assert response.status_code == 200
    data = response.json()
    assert_contrato(data, CONTRATO_PREDICT)
    assert data["severidad"] == severidad
    assert data["desvio_detectado"] is desvio
    assert data["posible_causa"] == causa
    assert data["confianza_modelo"] == pytest.approx(0.94)


def test_ML01_05_perfil_devuelve_el_perfil_de_calibracion(modelo):
    response = client.post(PERFIL, json={"calibracion": calibracion()})

    assert response.status_code == 200
    data = response.json()
    assert set(data) == {"perfil"}
    perfil = data["perfil"]
    assert perfil["n_dias_calibracion"] == CALIB_DIAS
    assert perfil["feature_version"] == FEATURE_VERSION
    # El perfil que devuelve /perfil es el que el backend reenvía a /predict tal cual.
    assert client.post(PREDICT, json={"serie": serie_24h(), "perfil": perfil}).status_code == 200


def test_ML01_05_perfil_con_calibracion_insuficiente_responde_422(modelo):
    response = client.post(PERFIL, json={"calibracion": calibracion(dias=3)},
                           headers={"X-Correlation-ID": "ml01-05"})

    assert response.status_code == 422
    data = response.json()
    assert_contrato(data, CONTRATO_ERROR, extra=("calib_dias_minimo", "samples_per_day"))
    assert data["error"] == "Calibracion Insuficiente"
    assert data["calib_dias_minimo"] == CALIB_DIAS
    assert data["samples_per_day"] == SAMPLES_PER_DAY
    assert data["trace_id"] == "ml01-05"


# ---------------------------------------------------------------------------
# Payload inválido: 422 con mensaje claro
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("cuerpo, en_mensaje", [
    ({"perfil": {}}, "serie: campo obligatorio"),
    ({"serie": [{"moveState": 1, "latitud": "x", "longitud": 1}], "perfil": {}}, "serie[0].latitud: debe ser un número"),
    ({"serie": [], "perfil": []}, "perfil: debe ser un objeto"),
])
def test_ML01_10_payload_invalido_responde_422_con_mensaje_claro(modelo, cuerpo, en_mensaje):
    response = client.post(PREDICT, json=cuerpo, headers={"X-Correlation-ID": "ml01-10"})

    assert response.status_code == 422
    data = response.json()
    assert_contrato(data, CONTRATO_ERROR, extra=("detail",))
    assert data["error"] == "Payload Invalido"
    assert en_mensaje in data["mensaje"]
    assert data["trace_id"] == "ml01-10"


@pytest.mark.parametrize("campo, valor, en_mensaje", [
    ("moveState", 10, "serie[0].moveState: debe ser menor o igual que 9"),
    ("latitud", 95.0, "serie[0].latitud: debe ser menor o igual que 90"),
    ("longitud", -181.0, "serie[0].longitud: debe ser mayor o igual que -180"),
])
def test_ML01_11_valores_fuera_de_rango_responden_422(modelo, campo, valor, en_mensaje):
    serie = serie_24h()
    serie[0][campo] = valor

    response = client.post(PREDICT, json={"serie": serie, "perfil": perfil_valido()})

    assert response.status_code == 422
    assert en_mensaje in response.json()["mensaje"]


def test_ML01_11_coordenada_no_finita_responde_422(modelo):
    cuerpo = b'{"serie": [{"moveState": 5, "latitud": -34, "longitud": Infinity}], "perfil": {"a": 1}}'
    response = client.post(PREDICT, content=cuerpo, headers={"Content-Type": "application/json"})

    assert response.status_code == 422
    assert "serie[0].longitud: debe ser un número finito" in response.json()["mensaje"]


@pytest.mark.parametrize("puntos", [0, 287, 289])
def test_ML01_12_serie_que_no_tiene_288_puntos_responde_422(modelo, puntos):
    serie = [_punto(i) for i in range(puntos)]

    response = client.post(PREDICT, json={"serie": serie, "perfil": perfil_valido()})

    assert response.status_code == 422
    assert response.json()["mensaje"] == (f"serie: debe tener exactamente 288 puntos (24 hs a 5 min); "
                                          f"se recibieron {puntos}")
    modelo.predict_proba.assert_not_called()


def test_ML01_12_perfil_vacio_responde_422(modelo):
    response = client.post(PREDICT, json={"serie": serie_24h(), "perfil": {}})

    assert response.status_code == 422
    assert "perfil: no se recibió el perfil de referencia" in response.json()["mensaje"]


def test_ML01_12_perfil_malformado_responde_422_indicando_que_falla(modelo):
    perfil = perfil_valido()
    del perfil["std_distancia_std"]
    perfil["pct_reposo_mean"] = "x"
    perfil["quarter_moveState_mean"] = [1, 2]

    response = client.post(PREDICT, json={"serie": serie_24h(), "perfil": perfil})

    assert response.status_code == 422
    mensaje = response.json()["mensaje"]
    assert "perfil: faltan las claves std_distancia_std" in mensaje
    assert "perfil.pct_reposo_mean: debe ser un número finito" in mensaje
    assert "perfil.quarter_moveState_mean: debe ser una lista de 4 números finitos" in mensaje
    modelo.predict_proba.assert_not_called()


# ---------------------------------------------------------------------------
# X-Correlation-ID
# ---------------------------------------------------------------------------

def test_ML01_13_devuelve_el_correlation_id_en_200_422_y_503(modelo, monkeypatch):
    headers = {"X-Correlation-ID": "ml01-13"}
    cuerpo = {"serie": serie_24h(), "perfil": perfil_valido()}

    ok = client.post(PREDICT, json=cuerpo, headers=headers)
    invalido = client.post(PREDICT, json={"perfil": {}}, headers=headers)
    perfil = client.post(PERFIL, json={"calibracion": calibracion()}, headers=headers)
    monkeypatch.setattr(main_app, "model", None)
    caido = client.post(PREDICT, json=cuerpo, headers=headers)

    assert [ok.status_code, invalido.status_code, perfil.status_code, caido.status_code] == [200, 422, 200, 503]
    for r in (ok, invalido, perfil, caido):
        assert r.headers["X-Correlation-ID"] == "ml01-13"
    assert caido.json()["trace_id"] == "ml01-13"


def test_ML01_13_sin_correlation_id_responde_unknown(modelo):
    response = client.post(PREDICT, json={"serie": serie_24h(), "perfil": perfil_valido()})

    assert response.headers["X-Correlation-ID"] == "unknown"


def test_ML01_14_loguea_el_correlation_id_en_cada_linea(modelo, caplog):
    caplog.set_level(logging.INFO, logger="ml-salud")

    client.post(PREDICT, json={"serie": serie_24h(), "perfil": perfil_valido()},
                headers={"X-Correlation-ID": "ml01-14-ok"})
    client.post(PREDICT, json={"perfil": {}}, headers={"X-Correlation-ID": "ml01-14-422"})

    ok = [r for r in caplog.records if r.correlation_id == "ml01-14-ok"]
    invalido = [r for r in caplog.records if r.correlation_id == "ml01-14-422"]
    assert any("Inferencia exitosa" in r.getMessage() for r in ok)
    assert any(r.getMessage() == f"POST {PREDICT} -> 200" for r in ok)
    assert any(r.levelno == logging.WARNING and "Payload inválido" in r.getMessage() for r in invalido)
    assert any(r.getMessage() == f"POST {PREDICT} -> 422" for r in invalido)


# ---------------------------------------------------------------------------
# Health y fallas del servicio
# ---------------------------------------------------------------------------

def test_ML01_15_health_responde_ok_con_el_modelo_cargado(modelo):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ml-salud",
                               "feature_version": FEATURE_VERSION, "model_loaded": True}


def test_ML01_16_health_sin_modelo_responde_503_degraded(sin_modelo):
    response = client.get("/health")

    assert response.status_code == 503
    assert response.json() == {"status": "degraded", "service": "ml-salud",
                               "feature_version": FEATURE_VERSION, "model_loaded": False}


def test_ML01_17_falla_del_servicio_responde_503_con_formato_comun(modelo):
    modelo.predict_proba.side_effect = RuntimeError("fallo de inferencia")

    response = client.post(PREDICT, json={"serie": serie_24h(), "perfil": perfil_valido()},
                           headers={"X-Correlation-ID": "ml01-17"})

    assert response.status_code == 503
    data = response.json()
    assert_contrato(data, CONTRATO_ERROR)
    assert data["error"] == "Internal Server Error"
    assert "fallo de inferencia" in data["mensaje"]
    assert data["trace_id"] == "ml01-17"


def test_ML01_17_sin_modelo_predict_responde_503(sin_modelo):
    response = client.post(PREDICT, json={"serie": serie_24h(), "perfil": perfil_valido()})

    assert response.status_code == 503
    assert "El modelo de machine learning no está cargado" in response.json()["mensaje"]
