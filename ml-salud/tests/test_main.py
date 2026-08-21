import pytest
from fastapi.testclient import TestClient
from unittest.mock import MagicMock
import numpy as np

from app.main import app
import app.main as main_app

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
    # Pequeña variación de lat/lon para que la serie no sea perfectamente
    # constante (std=0 es válido igual gracias al epsilon en features_salud,
    # pero así se ejercita el cálculo real de distancias).
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


def generate_payload(moveState=3):
    return {
        "serie": generate_serie_payload(moveState),
        "perfil_calibracion": generate_calib_payload(moveState=moveState),
    }


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ml-salud"}


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


def test_calibracion_insuficiente():
    """RF03: menos de 7 días de calibración debe rechazarse con 422, no
    con un 200 silencioso ni con un 503 genérico."""
    payload = {
        "serie": generate_serie_payload(),
        "perfil_calibracion": generate_calib_payload(n_dias=3),
    }
    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 422
    data = response.json()
    assert data["error"] == "Calibracion Insuficiente"
    assert data["calib_dias_minimo"] == CALIB_DIAS


def test_serie_incompleta():
    """La ventana a evaluar debe tener exactamente 288 puntos (24hs)."""
    payload = {
        "serie": generate_serie_payload()[:100],
        "perfil_calibracion": generate_calib_payload(),
    }
    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 503  # falla de precondición no distinguida de una falla interna


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
