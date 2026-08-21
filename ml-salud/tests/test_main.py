import pytest
from fastapi.testclient import TestClient
from unittest.mock import MagicMock
import numpy as np

from app.main import app
import app.main as main_app
from app.features_salud import compute_baseline, FEATURE_VERSION

client = TestClient(app)

CALIB_DIAS = 7
SAMPLES_PER_DAY = 288


@pytest.fixture(autouse=True)
def mock_dependencies():
    mock_model = MagicMock()
    mock_model.classes_ = np.array([0, 1, 2, 3])
    mock_model.predict_proba.return_value = np.array([[0.9, 0.05, 0.03, 0.02]])
    main_app.model = mock_model

    yield

    main_app.model = None


def _punto(i, moveState):
    return {
        "moveState": moveState,
        "latitud": -34.0 + (i % 12) * 0.00001,
        "longitud": -60.0 + (i % 12) * 0.00001,
    }


def generate_calib_payload(n_dias=CALIB_DIAS, moveState=3):
    n = n_dias * SAMPLES_PER_DAY
    return [_punto(i, moveState) for i in range(n)]


def generate_serie_payload(moveState=3):
    return [_punto(i, moveState) for i in range(SAMPLES_PER_DAY)]


def generate_perfil(moveState=3):
    """Perfil ya calculado, tal como lo devolvería /perfil -- así los
    tests de /predict no dependen de /perfil para armar su fixture."""
    calib = generate_calib_payload(moveState=moveState)
    move_state = [p["moveState"] for p in calib]
    lat = [p["latitud"] for p in calib]
    lon = [p["longitud"] for p in calib]
    return compute_baseline(move_state, lat, lon)


def generate_payload(moveState=3):
    return {
        "serie": generate_serie_payload(moveState),
        "perfil": generate_perfil(moveState),
    }


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["service"] == "ml-salud"
    assert data["feature_version"] == FEATURE_VERSION


# ---------------------------------------------------------------------
# /api/internal/ml/salud/perfil
# ---------------------------------------------------------------------

def test_perfil_ok():
    payload = {"calibracion": generate_calib_payload()}
    response = client.post("/api/internal/ml/salud/perfil", json=payload,
                            headers={"X-Correlation-ID": "perfil-123"})
    assert response.status_code == 200
    data = response.json()
    assert "perfil" in data
    assert data["perfil"]["n_dias_calibracion"] == CALIB_DIAS
    assert data["perfil"]["feature_version"] == FEATURE_VERSION
    assert response.headers["X-Correlation-ID"] == "perfil-123"


def test_perfil_calibracion_insuficiente():
    payload = {"calibracion": generate_calib_payload(n_dias=3)}
    response = client.post("/api/internal/ml/salud/perfil", json=payload)
    assert response.status_code == 422
    data = response.json()
    assert data["error"] == "Calibracion Insuficiente"
    assert data["calib_dias_minimo"] == CALIB_DIAS


# ---------------------------------------------------------------------
# /api/internal/ml/salud/predict
# ---------------------------------------------------------------------

def test_predict_normal():
    main_app.model.predict_proba.return_value = np.array([[0.8, 0.1, 0.05, 0.05]])
    payload = generate_payload()

    response = client.post("/api/internal/ml/salud/predict", json=payload, headers={"X-Correlation-ID": "test-123"})
    assert response.status_code == 200
    data = response.json()
    assert data["desvio_detectado"] is False
    assert data["severidad"] == 0
    assert data["posible_causa"] is None
    assert "confianza_modelo" in data
    assert response.headers["X-Correlation-ID"] == "test-123"


def test_predict_baja():
    main_app.model.predict_proba.return_value = np.array([[0.1, 0.8, 0.05, 0.05]])
    payload = generate_payload()

    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["desvio_detectado"] is True
    assert data["severidad"] == 1
    assert data["posible_causa"] == "Enfermedad leve / Inicio"


def test_predict_media():
    main_app.model.predict_proba.return_value = np.array([[0.05, 0.1, 0.8, 0.05]])
    payload = generate_payload()

    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["severidad"] == 2
    assert "Enfermedad moderada" in data["posible_causa"]


def test_predict_alta():
    main_app.model.predict_proba.return_value = np.array([[0.05, 0.05, 0.1, 0.8]])
    payload = generate_payload()

    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["severidad"] == 3
    assert "Problema severo" in data["posible_causa"]


def test_predict_perfil_vacio():
    """Si el backend no manda perfil (o manda uno vacío), no hay forma
    de predecir -- debe fallar, no inferir con datos incompletos."""
    payload = {"serie": generate_serie_payload(), "perfil": {}}
    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 503


def test_predict_perfil_malformado():
    """Un perfil sin las claves esperadas (ej. de una versión vieja de
    /perfil) no debe romper el proceso silenciosamente -- 503 explícito."""
    payload = {"serie": generate_serie_payload(), "perfil": {"algo_que_no_es": 1}}
    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 503


def test_serie_incompleta():
    """La ventana a evaluar debe tener exactamente 288 puntos (24hs)."""
    payload = {
        "serie": generate_serie_payload()[:100],
        "perfil": generate_perfil(),
    }
    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 503


def test_invalid_payload():
    payload = generate_payload()
    del payload["serie"][0]["moveState"]  # Invalida el primer punto

    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 422  # Pydantic validation error


def test_internal_error():
    main_app.model.predict_proba.side_effect = Exception("Model failed")
    payload = generate_payload()

    response = client.post("/api/internal/ml/salud/predict", json=payload, headers={"X-Correlation-ID": "err-123"})
    assert response.status_code == 503
    data = response.json()
    assert data["error"] == "Internal Server Error"
    assert "Model failed" in data["mensaje"]
    assert data["trace_id"] == "err-123"
    assert response.headers["X-Correlation-ID"] == "err-123"
