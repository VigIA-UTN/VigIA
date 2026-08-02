# VigIA - Microservicio de Detección de Celo

Microservicio desarrollado en **Python (FastAPI)** que expone un endpoint de inferencia para detectar celo en ganado bovino.

## Funcionamiento

El microservicio recibe una serie temporal de 24 horas de datos de telemetría de un animal (estado de movimiento, distancia calculada entre coordenadas). Utiliza un modelo neuronal (LSTM) previamente entrenado para predecir si el patrón de movimiento corresponde a un estado de celo, el cual se caracteriza por un aumento drástico y sostenido de la actividad.

### Endpoint Principal
`POST /api/internal/ml/celo/predict`

**Payload esperado:** Una lista de objetos de telemetría.
**Respuesta:** Booleano indicando `evento_celo`, nivel de `confianza_modelo` y la `ventana_optima_horas` para la inseminación.

## Desarrollo y Pruebas Locales

1. Instalar dependencias:
```bash
pip install -r requirements.txt
```

2. Ejecutar el servidor:
```bash
uvicorn app.main:app --reload --port 8001
```

### Ejecutar Tests
El proyecto cuenta con una suite de pruebas (usando `pytest`) que valida los contratos de solicitud/respuesta y el manejo de errores (incluyendo mocks del modelo de TensorFlow para ejecución rápida en CI).

```bash
pytest tests/ -v
```

## Dependencias
- FastAPI & Uvicorn
- TensorFlow (solo para inferencia del modelo exportado `.keras`)
- NumPy & Pandas
- Pytest (Testing)
