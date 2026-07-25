# Dataset Avanzado de Telemetría para Detección de Celo — VigIA

Este documento explica cómo generamos los datos sintéticos de entrenamiento para nuestro modelo de Machine Learning, replicando el comportamiento real de vacas de cría extensiva mediante reglas biológicas y dinámica de rodeo.

## 1. Fundamento Biológico y Referencias

Al no existir un dataset público con acelerometría cruda + GPS ya etiquetado para celo, construimos un simulador continuo de rodeo basado rigurosamente en literatura veterinaria y papers de IoT ganadero:

- **Ciclo Estral Bovino:** Promedio de 21 ± 4 días.
- **Duración del Estro (Celo):** Promedio de 15 ± 6 horas.
- **Dinámica GPS / Proximidad:** Durante el celo y el periestro, la vaca aumenta significativamente su distancia recorrida y pierde linealidad en su trayectoria (movimientos erráticos). Además, incrementa su proximidad e interacción con otros animales (monta).
- **Acelerometría y Rumia:** El tiempo estacionario (rumia/pastoreo) disminuye drásticamente, la varianza de la aceleración aumenta y aparecen picos de muy alta amplitud por breves segundos debido a los saltos de monta.

*(Referencias: Morales-Vargas et al. 2025; Cabezas et al. 2022; Álvarez et al. 2025)*

## 2. El Simulador de Telemetría (`generate_dataset.py`)

A diferencia de simuladores simplificados que generan "días" aislados, nuestro simulador corre **una línea de tiempo continua** para todo el rodeo en simultáneo (ej. 100 vacas durante 90 días con lecturas cada 5 minutos). 

### Reglas de Simulación:
1. **El Reloj Biológico:** Se sortea para cada animal en qué día inicia su ciclo. A partir de allí, cada ~21 días entra en un celo de ~15 horas de duración.
2. **Dinámica del Rodeo (GPS):** Todas las vacas realizan un paseo aleatorio (*random walk*) con una ligera fuerza de cohesión hacia el centro del grupo para simular el rebaño.
3. **Comportamiento en Celo:** Si el reloj biológico indica celo (o periestro), el simulador le inyecta multiplicadores de velocidad y de aceleración a ese animal específico. Su GPS se vuelve errático y su collar detecta impactos súbitos.
4. **Falsos Positivos (Ruido):** Aleatoriamente inyectamos eventos donde el rodeo entero se agita de repente (simulando moscas, arreo o calor), para que el modelo aprenda que "moverse mucho" no siempre es sinónimo de celo.

El simulador exporta todo esto en un único archivo tabular idéntico al formato que usa nuestra base de datos en producción.

## 3. Preparación y Entrenamiento (`train_baseline_rnn.py`)

El script de entrenamiento actúa como nuestro "Científico de Datos", consumiendo la telemetría plana y preparando todo para la red neuronal.

1. **Ingeniería de Features (Feature Engineering):** 
   - El dataset contiene `latitud` y `longitud`. El script calcula matemáticamente la distancia euclidiana entre la posición actual y la anterior para generar la variable **`distancia_recorrida`**.
   - Agrupa los datos en ventanas temporales de 24 horas (288 registros). A la aceleración tridimensional `(x, y, z)` y a la distancia recorrida le suma una quinta variable: `magnitud total`. La red recibe tensores de forma `(288, 5)`.
2. **El Etiquetado (Target):**
   - Si durante esa ventana de 24 horas el animal tuvo al menos una lectura etiquetada como celo, el día entero se marca como `1`. Caso contrario, `0`.
3. **Separación Estricta (Split):**
   - Para evitar el *data leakage* (que la red haga trampa memorizando cómo camina una vaca en particular), se divide el dataset usando **`GroupShuffleSplit`**. Los animales que se usan para Entrenar (Train) nunca se usan para Validar (Val) ni para Testear (Test).

## 4. Estructura de Salida

Al correr `python generate_dataset.py`, obtendrás:

- `telemetria.parquet`: El dataset completo columnar. Ocupa muy poco espacio pero contiene millones de filas con los sensores exactos (lat, lon, accel_x, y, z, rumia, bateria).
- `ground_truth_metadata.json`: Un archivo confidencial que guarda el "resumen" de a qué vaca y en qué momento exacto le tocó el celo, útil para validar métricas.
- `dataset_summary.json`: Un JSON con estadísticas generales de la simulación (proporciones, semillas, parámetros) usado para trazabilidad en DVC/S3.

> **Requisito para correr:** `pip install numpy pandas scikit-learn tensorflow pyarrow`
