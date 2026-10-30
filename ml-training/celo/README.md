# Dataset Avanzado de Telemetría para Detección de Celo — VigIA

Este documento explica cómo generamos los datos sintéticos de entrenamiento para nuestro modelo de Machine Learning, replicando el comportamiento real de vacas de cría extensiva mediante reglas biológicas y dinámica de rodeo.

## 1. Fundamento Biológico y Referencias

Al no existir un dataset público con acelerometría cruda + GPS ya etiquetado para celo, construimos un simulador continuo de rodeo basado rigurosamente en literatura veterinaria y papers de IoT ganadero:

- **Ciclo Estral Bovino:** Promedio de 21 ± 4 días.
- **Duración del Estro (Celo):** Promedio de 15 ± 6 horas.
- **Dinámica GPS / Proximidad:** Durante el celo y el periestro, la vaca aumenta significativamente su distancia recorrida y pierde linealidad en su trayectoria (movimientos erráticos).
- **Edge Computing (Caravana Lansitec):** La caravana no envía acelerometría cruda por LoRaWAN para ahorrar batería. En su lugar, analiza los datos internamente y envía un estado de vibración (`vibstate` de 0 a 9) en cada Heartbeat.
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

## 3. Ruido de GPS y granularidad de `moveState`

Una caravana real, aunque esté físicamente quieta, mide con ruido GNSS: la
distancia entre dos fixes consecutivos de una caravana estacionaria ronda
los ~15m de desvío estándar por eje (derivado empíricamente de una
muestra real de 12 lecturas). Hasta que se agregó esto, `generate_dataset.py`
generaba la posición "verdadera" del random walk como si fuera también
la posición observada, sin ruido de medición — un desplazamiento
sintético mucho más chico que el ruido GPS real, incluso durante el pico
de celo. Esto hacía que una caravana real quieta pareciera, para el modelo,
más "activo" que el pico de celo sintético, generando falsos positivos
de celo con confianza ~0.99 en dispositivos que no se estaban moviendo.

También se corrigió un desajuste train/serve relacionado: en producción
`moveState` sólo puede cambiar cuando llega un Heartbeat real (cadencia
actual: 30 minutos = 6 ticks de 5 min; ver el forward-fill de `moveState`
en `IngestaController.java`), pero el generador asignaba un valor fresco
en cada tick de 5 minutos.

Ambos se manejan en `ml-training/gps_noise_common.py` (compartido con
`ml-training/salud`) y se controlan con dos flags:

- `--gps-noise-std-m` (default 15.5): desvío estándar, en metros por eje,
  del ruido de medición GNSS inyectado sobre la trayectoria simulada.
- `--heartbeat-interval-ticks` (default 6 = 30 min): cada cuántos ticks
  de 5 minutos se permite que cambie `moveState`.

**Si la cadencia real de heartbeat cambia en producción**, hay que
regenerar el dataset y reentrenar (el `moveState` sintético quedaría con
una granularidad distinta a la real) — ver
`ml-training/RUNBOOK_CAMBIO_HEARTBEAT.md` para el procedimiento exacto.

Para validar que el ruido del dataset regenerado sigue siendo realista,
correr (rápido, sin red):
```powershell
python calibracion_dataset.py --skip-external-comparison
```
Esto compara la distancia media entre fixes consecutivos en ticks de
reposo (`moveState<=1`) contra la muestra real, y falla (`exit 1`) si el
ratio se sale de un rango tolerante (0.5x-2.0x).

## 4. Preparación y Entrenamiento (`train_multiseed.py`)

1. **Ingeniería de Features (Feature Engineering):**
   - El dataset contiene `latitud` y `longitud` (ya con el ruido GPS
     inyectado, ver sección 3). El script calcula la distancia euclidiana
     entre la posición actual y la anterior para generar la variable
     **`distancia_recorrida`**.
   - Agrupa los datos en ventanas temporales de 24 horas (288 registros). La red recibe tensores de forma `(288, 2)` con las features: `moveState` y `distancia_recorrida`.
2. **El Etiquetado (Target):**
   - Si durante esa ventana de 24 horas el animal tuvo celo, se marca como `1`. Caso contrario, `0`.
3. **Separación Estricta (Split):**
   - Se usa **`GroupShuffleSplit`**. Los animales que se usan para Entrenar (Train) nunca se usan para Validar (Val) ni para Testear (Test).
4. **Reproducibilidad:** se corren 5 semillas de inicialización sobre la
   misma partición (`--init-seeds`), reportando media ± desvío estándar
   en `summary_5seeds.json`.
5. **Umbral de decisión:** en cada corrida se elige el umbral que maximiza
   el F1 de la clase celo sobre la curva Precision-Recall de
   **validación** (`best_threshold_f1` en `run_summary.json`) y se aplica
   fijo a prueba: el conjunto de prueba no interviene en ninguna decisión.
   Elegirlo sobre prueba, como se hacía antes del 27/10/2026, es fuga de
   información y sobreestima las métricas.

Dos scripts de solo lectura complementan el entrenamiento (no reentrenan):

- `reporte_paper.py --run models/<run>`: métricas agregadas, composición de
  la partición, hiperparámetros y las figuras del paper en `figuras/`.
- `importancia_permutacion.py --run models/<run>`: importancia por
  permutación de cada feature sobre el conjunto de prueba.

## 5. Pasos para Entrenar el Modelo

Para reproducir el entrenamiento, seguí estos pasos en tu terminal (estando dentro de la carpeta `ml-training/celo`):

1. **Activar el entorno virtual** (compartido con `ml-training/salud`):
   ```powershell
   ..\.venv\Scripts\Activate.ps1
   ```
2. **Instalar dependencias (solo la primera vez):**
   ```powershell
   pip install -r ..\requirements.txt
   ```
3. **Generar el Dataset Sintético:**
   Esto creará la carpeta `dataset/` con el archivo `telemetria.parquet`.
   ```powershell
   python generate_dataset.py --n-animales 200 --dias 90 --seed 42
   ```
   Con menos animales el test set queda demasiado chico para confiar en
   las métricas de evaluación (ver "Resultados de referencia" más abajo:
   con 60 animales el PR-AUC medido salía sistemáticamente más alto de lo
   real, ~0.85 vs. ~0.77 con 200 animales sobre el mismo enfoque).
4. **Validar el ruido GPS del dataset generado** (ver sección 3):
   ```powershell
   python calibracion_dataset.py --skip-external-comparison
   ```
5. **Entrenar la Red Neuronal (RNN):**
   ```powershell
   python train_multiseed.py --dataset-dir dataset --models-dir models --split-seed 42 --init-seeds 42 43 44 45 46
   ```
6. **Elegir el mejor run y desplegarlo manualmente.** A diferencia de
   `ml-training/salud/train_model.py`, este script **no** exporta
   automáticamente al microservicio. Mirá `models/<run>/summary_5seeds.json`
   y `models/<run>/init<seed>/run_summary.json`, elegí el seed con mejor
   PR-AUC de **validación** (`pr_auc_celo_val`; elegirlo por la PR-AUC de
   prueba es la misma fuga que elegir ahí el umbral). El desplegado,
   `init44`, es el mejor en validación (0.833). Copiá manualmente:
   ```powershell
   copy models\<run>\init<seed>\modelo_celo_lstm.keras ..\..\ml-celo\app\models\modelo_celo_lstm.keras
   copy models\<run>\init<seed>\scaler_celo.joblib     ..\..\ml-celo\app\models\scaler_celo.joblib
   ```
   Después, releé `best_threshold_f1` de ese `run_summary.json` y
   actualizá `CELO_THRESHOLD` en `infra/.env.example` (y `infra/.env`
   local, y el docstring y el banner de `simulador/simulador_celo.py`).

## 6. Resultados de referencia

> Los resultados finales para el informe (media ± desvío muestral,
> matriz de confusión media ± desvío y verificación de que el modelo de
> `ml-celo` es el evaluado) están en
> [`../resultados_finales/`](../resultados_finales/README.md) y se generan
> con `python evaluar_final.py`.

Corrida `20261027_105620_split42` (200 animales, 90 días, dataset con
ruido GPS + moveState cuantizado, umbral elegido en validación). Es un
reentrenamiento de `20260918_195223_split42` con las mismas semillas: las
predicciones de las 5 semillas son idénticas, así que el modelo desplegado
(`init44`) es el mismo y solo cambió cómo se elige el umbral. Test set:
3600 ventanas de 24hs, 278 con celo real.

Semilla `init44` (la desplegada):

| Métrica | Valor |
|---|---|
| PR-AUC | 0.8135 |
| Precisión (umbral elegido en validación, 0.947) | 0.868 |
| Recall | 0.781 |
| F1 | 0.822 |

Media ± desvío de las 5 semillas: PR-AUC 0.770 ± 0.047, precisión
0.723 ± 0.100, recall 0.794 ± 0.061, F1 0.752 ± 0.061, umbral
0.794 ± 0.125.

La importancia por permutación (`importancia_permutacion.py`) muestra que,
con el ruido GPS actual, el modelo depende exclusivamente de `moveState`:
permutarlo baja la PR-AUC a 0.078 (nivel aleatorio) y permutar la distancia
no la cambia.

**Margen del umbral en la demo:** el modelo satura cerca de P(celo) ≈ 0.95,
así que el umbral queda muy cerca de su máximo. Con un `Random` sin semilla,
~5% de las ventanas de celo que siembra `DataInitializer` para el Acto 2
quedaban por debajo de 0.947; por eso se siembran con una semilla fija
(`SEMILLA_HISTORIAL_CELO = 817`, P = 0.9534). **Si se reentrena el modelo o
cambia `CELO_THRESHOLD`, hay que volver a elegir esa semilla** contra el
modelo nuevo.

**Por qué el número cambió durante el desarrollo de este fix:** la
primera corrida post-ruido se midió sobre 60 animales (test set de solo
720 muestras, 57 positivas) y dio PR-AUC 0.846 — un número mucho más
optimista de lo real, con el umbral óptimo variando muchísimo entre
semillas (0.540 a 0.895, desvío estándar 0.123) por el tamaño chico del
test set. Repetir la medición con 200 animales (3600 muestras, 278
positivas) bajó la estimación a PR-AUC 0.770 ± 0.047 en promedio (mejor
semilla: 0.8135) — todavía sólido, pero la lección es la misma que en
`ml-training/salud`: **no confiar en métricas medidas sobre un test set
chico**, aunque el resultado agregado (media ± desvío de 5 semillas)
parezca razonable.
