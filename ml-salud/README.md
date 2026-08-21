# VigIA - Microservicio de Detección de Desvíos Sanitarios

Microservicio desarrollado en **Python (FastAPI)** que expone un endpoint de inferencia para detectar desvíos de salud en ganado bovino, comparando la telemetría de cada animal contra **su propio perfil de referencia individual**.

## Funcionamiento

A diferencia del modelo de celo (`ml-celo`), que evalúa un evento puntual comparado contra la población general, este microservicio construye un perfil de comportamiento normal por animal durante un **período de calibración de 7 días** (requisito de negocio RF03), y evalúa cada ventana diaria posterior comparándola contra ese perfil propio (desvío individual, no poblacional).

El modelo es **híbrido**: se calculan features estadísticas de desvío (z-scores de actividad y desplazamiento respecto a la línea base del animal, ver `app/features_salud.py`) y sobre esas features se entrena un `RandomForestClassifier` de scikit-learn — no una red neuronal. Este archivo (`features_salud.py`) es una copia textual del que usa el pipeline de entrenamiento (`ml-training/salud/train_model.py`): si se cambia la lógica de features, hay que re-entrenar y volver a copiar el archivo (ver comentario al inicio de ese módulo).

### Endpoint Principal
`POST /api/internal/ml/salud/predict`

**Payload esperado:**
```json
{
  "serie": [ /* 288 puntos (24hs a 5 min) de la ventana a evaluar */ ],
  "perfil_calibracion": [ /* telemetría cruda de al menos 7 días (2016 puntos) del mismo animal */ ]
}
```

**Respuesta:** `severidad` (0=Normal, 1=Baja, 2=Media, 3=Alta), `desvio_detectado` (booleano), `posible_causa` y `confianza_modelo`.

Si `perfil_calibracion` no cubre los 7 días mínimos exigidos por RF03, el endpoint responde `422` (`Calibracion Insuficiente`) en vez de intentar predecir sobre un perfil inválido — es responsabilidad del backend no invocar este endpoint para un animal que todavía no completó su calibración.

## Desarrollo y Pruebas Locales

1. Instalar dependencias:
```bash
pip install -r requirements.txt
```

2. Ejecutar el servidor:
```bash
uvicorn app.main:app --reload --port 8002
```

### Ejecutar Tests
```bash
pytest tests/ -v
```

## Dependencias
- FastAPI & Uvicorn
- scikit-learn (entrenamiento y carga del modelo `.joblib`)
- NumPy
- Pytest (Testing)

Nota: este microservicio **ya no depende de TensorFlow** (el modelo anterior, LSTM, fue reemplazado por el clasificador híbrido descripto arriba).

## Limitaciones conocidas

- El perfil de calibración se recibe y se recalcula en cada request (no se cachea del lado del microservicio). Para producción, cachear el perfil ya calculado por animal evitaría recomputar sobre ~2000 puntos en cada llamada; queda como mejora futura, no bloqueante para esta iteración.
- El dataset de entrenamiento es sintético (ver `ml-training/salud/`); no hay, al momento de esta versión, validación con telemetría real de eventos de salud etiquetados.
