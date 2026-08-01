# Dataset Avanzado de Telemetría para Detección de Celo — VigIA

Este documento explica cómo generamos los datos sintéticos de entrenamiento para nuestro modelo de Machine Learning, replicando el comportamiento real de vacas de cría extensiva mediante reglas biológicas y dinámica de rodeo.

## 1. Fundamento Biológico y Referencias

Al no existir un dataset público con acelerometría cruda + GPS ya etiquetado para celo, construimos un simulador continuo de rodeo basado rigurosamente en literatura veterinaria y papers de IoT ganadero:

- **Ciclo Estral Bovino:** Promedio de 21 ± 4 días.
- **Duración del Estro (Celo):** Promedio de 15 ± 6 horas.
- **Dinámica GPS / Proximidad:** Durante el celo y el periestro, la vaca aumenta significativamente su distancia recorrida y pierde linealidad en su trayectoria (movimientos erráticos).
- **Edge Computing (Collar Lansitec):** El collar no envía acelerometría cruda por LoRaWAN para ahorrar batería. En su lugar, analiza los datos internamente y envía un estado de vibración (`vibstate` de 0 a 9) en cada Heartbeat.
  - `0`: Descanso / Rumia.
  - `1-6`: Caminata / Pastoreo normal.
  - `7-9`: Picos de alta actividad (celo, monta, arreo).

*(Referencias: Morales-Vargas et al. 2025; Protocol Spec Lansitec 990-01206)*

## 2. El Simulador de Telemetría (`generate_dataset.py`)

A diferencia de simuladores simplificados que generan "días" aislados, nuestro simulador corre **una línea de tiempo continua** para todo el rodeo en simultáneo (ej. 60 vacas durante 60 días con lecturas cada 5 minutos). 

### Reglas de Simulación:
1. **El Reloj Biológico:** Se sortea para cada animal en qué día inicia su ciclo. A partir de allí, cada ~21 días entra en un celo de ~15 horas de duración.
2. **Dinámica del Rodeo (GPS):** Todas las vacas realizan un paseo aleatorio (*random walk*) con una ligera fuerza de cohesión hacia el centro del grupo para simular el rebaño.
3. **Comportamiento en Celo:** Si el reloj biológico indica celo, el simulador inyecta multiplicadores de velocidad y genera picos frecuentes de `vibstate` alto (7-9).
4. **Falsos Positivos (Ruido):** Aleatoriamente inyectamos eventos donde el rodeo entero se agita de repente (simulando moscas, arreo o calor), generando picos de `vibstate` falsos.

## 3. Preparación y Entrenamiento (`train_baseline_rnn.py`)

1. **Ingeniería de Features (Feature Engineering):** 
   - El dataset contiene `latitud` y `longitud`. El script calcula matemáticamente la distancia euclidiana entre la posición actual y la anterior para generar la variable **`distancia_recorrida`**.
   - Agrupa los datos en ventanas temporales de 24 horas (288 registros). La red recibe tensores de forma `(288, 2)` con las features: `vibstate` y `distancia_recorrida`.
2. **El Etiquetado (Target):**
   - Si durante esa ventana de 24 horas el animal tuvo celo, se marca como `1`. Caso contrario, `0`.
3. **Separación Estricta (Split):**
   - Se usa **`GroupShuffleSplit`**. Los animales que se usan para Entrenar (Train) nunca se usan para Validar (Val) ni para Testear (Test).

## 4. Pasos para Entrenar el Modelo

Para reproducir el entrenamiento y exportar el modelo `.keras` al microservicio, seguí estos pasos en tu terminal (estando dentro de la carpeta `ml-training`):

1. **Activar el entorno virtual:**
   ```powershell
   .\.venv\Scripts\Activate.ps1
   ```
2. **Instalar dependencias (solo la primera vez):**
   ```powershell
   pip install -r requirements.txt
   ```
3. **Generar el Dataset Sintético:**
   Esto creará la carpeta `dataset/` con el archivo `telemetria.parquet`.
   ```powershell
   python generate_dataset.py
   ```
4. **Entrenar la Red Neuronal (RNN):**
   Esto entrenará la LSTM y exportará automáticamente el modelo final y el archivo de normalización a la carpeta `ml-celo/app/models/`.
   ```powershell
   python train_baseline_rnn.py
   ```
