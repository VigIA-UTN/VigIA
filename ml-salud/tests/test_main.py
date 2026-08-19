import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, MagicMock
import numpy as np
import sys

# Mock tf early to avoid errors if not installed in CI
sys.modules['tensorflow'] = MagicMock()

from app.main import app
import app.main as main_app

client = TestClient(app)

@pytest.fixture(autouse=True)
def mock_dependencies():
    # Mocking scaler
    main_app.scaler = MagicMock()
    main_app.scaler.transform.return_value = np.zeros((1, 288, 2))
    
    # Mocking model
    main_app.model = MagicMock()
    main_app.model.predict.return_value = np.array([[0.9, 0.05, 0.03, 0.02]])
    
    yield
    
    main_app.scaler = None
    main_app.model = None

def generate_payload():
    return {
        "serie": [{"moveState": 1, "latitud": -34.0, "longitud": -60.0} for _ in range(288)],
        "perfil_referencia": {"rumia_promedio_historico": 1.0}
    }

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ml-salud"}

def test_predict_normal():
    main_app.model.predict.return_value = np.array([[0.8, 0.1, 0.05, 0.05]])
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
    main_app.model.predict.return_value = np.array([[0.1, 0.8, 0.05, 0.05]])
    payload = generate_payload()
    
    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["desvio_detectado"] is True
    assert data["severidad"] == 1
    assert data["posible_causa"] == "Enfermedad leve / Inicio"

def test_predict_media():
    main_app.model.predict.return_value = np.array([[0.05, 0.1, 0.8, 0.05]])
    payload = generate_payload()
    
    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["severidad"] == 2
    assert "Enfermedad moderada" in data["posible_causa"]

def test_predict_alta():
    main_app.model.predict.return_value = np.array([[0.05, 0.05, 0.1, 0.8]])
    payload = generate_payload()
    
    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["severidad"] == 3
    assert "Problema severo" in data["posible_causa"]

def test_invalid_payload():
    payload = generate_payload()
    del payload["serie"][0]["moveState"] # Invalida el primer punto
    
    response = client.post("/api/internal/ml/salud/predict", json=payload)
    assert response.status_code == 422 # Pydantic validation error

def test_internal_error():
    # Forzar un error haciendo que model.predict falle
    main_app.model.predict.side_effect = Exception("Model failed")
    payload = generate_payload()
    
    response = client.post("/api/internal/ml/salud/predict", json=payload, headers={"X-Correlation-ID": "err-123"})
    assert response.status_code == 503
    data = response.json()
    assert data["error"] == "Internal Server Error"
    assert "Model failed" in data["mensaje"]
    assert data["trace_id"] == "err-123"
    assert response.headers["X-Correlation-ID"] == "err-123"
