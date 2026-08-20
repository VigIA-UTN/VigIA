# pyrefly: ignore [missing-import]
import pytest
# pyrefly: ignore [missing-import]
from fastapi.testclient import TestClient
from unittest.mock import patch, MagicMock
import sys
import os
# pyrefly: ignore [missing-import]
import numpy as np

# Añadir el directorio app al PYTHONPATH
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from app.main import app

# Cliente de pruebas
client = TestClient(app)

@pytest.fixture(autouse=True)
def mock_dependencies():
    """
    Mockea el modelo y el scaler de normalización para evitar necesitar
    TensorFlow y el modelo real durante los tests.
    """
    mock_scaler = MagicMock()

    # Simula StandardScaler.transform: mismo comportamiento que el scaler
    # real usado en train_multiseed.py, con mean=[0,0] y std=[1,1] (no-op),
    # para que los tests sigan razonando en términos de moveState "crudo".
    def transform_mock(X_flat):
        return X_flat

    mock_scaler.transform.side_effect = transform_mock

    with patch("app.main.model") as mock_model, \
         patch("app.main.scaler", mock_scaler):

        # Simula la predicción del modelo
        def predict_mock(X, verbose=0):
            # X_input pasó por scaler.transform (no-op en el mock).
            # X[0][0][0] corresponde al moveState del primer punto.
            val = X[0][0][0]
            if val > 5.0:
                # Alta actividad -> probabilidad de celo > 0.5
                return np.array([[0.85]])
            else:
                # Baja actividad -> probabilidad de celo <= 0.5
                return np.array([[0.20]])

        mock_model.predict.side_effect = predict_mock
        yield mock_model

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ml-celo"}

def test_predict_evento_celo_true(mock_dependencies):
    # Payload válido con alta actividad (moveState = 10)
    payload = [
        {"moveState": 10, "latitud": -34.0, "longitud": -59.0},
        {"moveState": 10, "latitud": -34.001, "longitud": -59.001}
    ]
    response = client.post("/api/internal/ml/celo/predict", json=payload)

    assert response.status_code == 200
    data = response.json()
    assert data["evento_celo"] is True
    assert data["confianza_modelo"] == 0.85
    assert data["ventana_optima_horas"] == 12

def test_predict_evento_celo_false(mock_dependencies):
    # Payload válido con baja actividad (moveState = 1)
    payload = [
        {"moveState": 1, "latitud": -34.0, "longitud": -59.0},
        {"moveState": 1, "latitud": -34.0, "longitud": -59.0}
    ]
    response = client.post("/api/internal/ml/celo/predict", json=payload)

    assert response.status_code == 200
    data = response.json()
    assert data["evento_celo"] is False
    assert data["confianza_modelo"] == 0.20
    assert data["ventana_optima_horas"] == 0

def test_predict_invalid_payload():
    # Payload inválido: falta campo 'moveState'
    payload = [
        {"latitud": -34.0, "longitud": -59.0}
    ]
    response = client.post("/api/internal/ml/celo/predict", json=payload)

    # 422 Unprocessable Entity provisto por FastAPI
    assert response.status_code == 422
    data = response.json()
    assert "detail" in data
    assert data["detail"][0]["type"] == "missing"
    assert data["detail"][0]["loc"] == ["body", 0, "moveState"]

def test_predict_empty_payload(mock_dependencies):
    # Payload vacío -> debe retornar error interno según lógica en main.py
    payload = []
    response = client.post("/api/internal/ml/celo/predict", json=payload)

    # El código devuelve 503 ante errores no manejados/ValueErrors
    assert response.status_code == 503
    data = response.json()

    # Formato de error estándar verificado
    assert data["error"] == "Internal Server Error"
    assert "La serie de telemetría está vacía" in data["mensaje"]
    assert "trace_id" in data
