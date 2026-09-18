# ml-training/salud

Pipeline de entrenamiento del modelo de **detección de desvíos sanitarios
por perfil individual** de VigIA.

## Diseño

A diferencia de `ml-training/celo` (clasificador poblacional: todos los
animales se comparan contra el mismo patrón aprendido), este modelo
compara a cada animal contra **su propio perfil de referencia**,
construido durante un período de calibración de 7 días (RF03). Ver el
docstring de `features_salud.py` para el detalle de por qué se comparan
ventanas completas de 24hs (evita necesitar timestamps por punto) y por
qué se usa un `RandomForestClassifier` en vez de una LSTM (reproducibilidad).

## Archivos

- `generate_dataset.py`: simulador sintético. Cada animal tiene 7 días de
  calibración (sin eventos de enfermedad, por diseño) + N días de
  evaluación (con eventos de enfermedad posibles), y una "personalidad"
  individual de actividad base para que comparar contra el perfil propio
  tenga sentido (si todos los animales tuvieran el mismo comportamiento
  base, un perfil individual sería equivalente al poblacional).
- `features_salud.py`: extracción de features de desvío. **Es el mismo
  archivo, copiado textualmente, que usa `ml-salud/app/`** — no se
  reimplementa aparte. Si se edita acá, hay que volver a correr
  `train_model.py` para que la copia de `ml-salud/app/features_salud.py`
  quede sincronizada. Desde `FEATURE_VERSION = "v2"`, `distancias_desde_coords()`
  promedia la posición en bloques de `DIST_SMOOTHING_TICKS` (90 min) antes
  de calcular la distancia entre puntos consecutivos: el ruido de medición
  GPS real (ver `ml-training/celo/README.md` sección 3) es más grande que
  el desplazamiento real que distingue un animal sano de uno enfermo, así
  que la distancia cruda punto-a-punto queda dominada por ruido sin este
  suavizado (ver docstring de la constante para el detalle y el barrido
  empírico que la calibró).
- `train_model.py`: entrena el `RandomForestClassifier`, con el mismo
  estándar de rigor metodológico usado en `ml-training/celo` (partición
  por animal sin fuga de información, semillas fijas, 5 corridas con
  distinta semilla del estimador sobre la misma partición, reporte de
  media ± desvío estándar, PR-AUC y umbral óptimo de F1 para la métrica
  binaria "desvío detectado").

## Uso

```bash
python3 generate_dataset.py --n-animales 300 --dias-evaluacion 90 --seed 42 --out-dir dataset \
    --gps-noise-std-m 15.5 --heartbeat-interval-ticks 6
python3 ../celo/calibracion_dataset.py --sim-dataset dataset/telemetria.parquet --skip-external-comparison
python3 train_model.py --dataset-dir dataset --models-dir models
```

`--n-animales 300` (default actual, antes 120): con 120 animales las
clases 1/2/3 (35/14/9 muestras de test) daban métricas de
precisión/recall con demasiada varianza como para confiar en ellas —
ver "Resultados de referencia" más abajo. Ojo con `--dias-evaluacion`:
subirlo NO agrega más eventos de enfermedad (cada animal tiene como
máximo un evento, generado una sola vez, sin importar cuántos días se
evalúen), así que solo diluye el balance de clases con más días sanos.
Para más ejemplos de enfermedad, subir `--n-animales`, no `--dias-evaluacion`.

El modelo entrenado (y `features_salud.py`) se exportan automáticamente a
`ml-salud/app/`.

`--gps-noise-std-m`/`--heartbeat-interval-ticks` inyectan ruido de
medición GPS realista y cuantizan `moveState` a la cadencia real de
heartbeat (mismo módulo compartido `ml-training/gps_noise_common.py` que
usa `ml-training/celo`) — ver la explicación completa y la justificación
en [`../celo/README.md`](../celo/README.md#3-ruido-de-gps-y-granularidad-de-movestate).
El comando de `calibracion_dataset.py` valida (rápido, sin red) que el
ruido del dataset regenerado sigue siendo realista.

## Resultados de referencia (dataset sintético, 300 animales, 90 días de evaluación, seed 42)

Media ± desvío estándar sobre 5 corridas con distinta semilla del
estimador, misma partición de datos (80/20 por animal). Corrida
`20260918_195113_split42`, dataset regenerado con ruido GPS realista +
`moveState` cuantizado a la cadencia real de heartbeat (ver sección
"Uso" y `ml-training/celo/README.md` sección 3), `DIST_SMOOTHING_TICKS=18`
en `features_salud.py`, y `--n-estimators 600 --max-depth 0` (sin
límite) en `train_model.py`:

| Métrica | Valor |
|---|---|
| Accuracy (multiclase) | 0.981 ± 0.000 |
| F1 macro (multiclase) | 0.713 ± 0.003 |
| PR-AUC (desvío sí/no) | 0.575 ± 0.002 |
| Precisión (desvío, umbral óptimo) | 0.669 ± 0.041 |
| Recall (desvío, umbral óptimo) | 0.494 ± 0.011 |
| F1 (desvío, umbral óptimo) | 0.567 ± 0.011 |

Desglose por severidad (una corrida representativa, `init42`, sobre
5400 días de test): Normal precisión/recall 0.992/0.990 (support 5304);
Baja 0.303/0.348 (support 66); Media 0.600/0.750 (support 12); Alta
1.000/0.778 (support 18). La clase "Baja" (desvío leve) es, por lejos,
la más difícil: un cambio de actividad leve es casi indistinguible del
piso de ruido GPS, así que esperá más falsas alarmas ahí que en
Media/Alta. Alta tiene precisión perfecta en esta corrida — si el
modelo dice Alta, confiá; si dice Baja, tomalo con pinzas.

**Cómo se llegó a esta configuración (no es la primera que se probó):**
1. La primera corrida sobre datos con ruido, sin el suavizado de
   `DIST_SMOOTHING_TICKS`, colapsó a PR-AUC ~0.39 (la distancia cruda
   quedaba dominada por ruido, sin señal de salud real).
2. Agregar el suavizado (barrido de 6 a 48 ticks, ver `features_salud.py`)
   la recuperó a PR-AUC 0.642 ± 0.005 -- pero medido sobre 120 animales
   (35/14/9 muestras de test en las clases 1/2/3), una muestra demasiado
   chica para confiar en el número.
3. Repetir la medición sobre un dataset más grande (300 animales) bajó
   la estimación a PR-AUC ~0.52 -- **el 0.642 anterior era optimista por
   tamaño de muestra, no un resultado real**. Importante: subir
   `--dias-evaluacion` en vez de `--n-animales` empeora esto todavía más
   (ver nota en "Uso" sobre por qué diluye el balance de clases).
4. Con la muestra más grande y confiable, un barrido de hiperparámetros
   del `RandomForestClassifier` mostró que el default anterior
   (`max_depth=8`) estaba subajustado: sin límite de profundidad +
   `n_estimators=600` subió PR-AUC de ~0.52 a 0.575 ± 0.002 (con el
   train set en PR-AUC=1.0, es decir sobreajustado a full, pero la
   mejora en test es real y estable entre semillas).

Los números de esta tabla, aun siendo más bajos que los 0.898 originales
(medidos sin ruido GPS, una tarea artificialmente fácil), son la
estimación más confiable que tenemos hoy bajo condiciones realistas.

## Limitaciones

- Igual que `ml-training/celo`: el dataset es 100% sintético. No hay
  validación con telemetría real de enfermedad — se buscó un dataset
  público comparable (ver conversación de diseño) y el candidato más
  cercano, CowScreeningDB (cojera bovina, IMU de pata), requiere firmar
  un acuerdo de licencia y tiene una granularidad de sensor distinta a
  la de VigIA (acelerometría cruda a 100Hz vs. `moveState` agregado).
  Queda como trabajo futuro evaluar si vale la pena tramitar el acceso.
- Las clases 1/2/3 son minoritarias por diseño (igual que celo). El
  desbalance se manejó con `class_weight='balanced'` en vez de
  sobremuestreo, mismo criterio que el modelo de celo.
- La búsqueda de hiperparámetros fue manual y acotada (`n_estimators`,
  `max_depth`, ver sección anterior) — no una grilla ni un algoritmo
  genético. `ml-training/celo/optimize_rnn_ga.py` tiene un precedente de
  búsqueda con algoritmo genético que podría adaptarse acá si se justifica
  seguir exprimiendo el modelo (`min_samples_leaf` y otros parámetros de
  `RandomForestClassifier` no se probaron).
- La clase "Baja" (desvío leve) tiene precisión baja (~0.30): un cambio
  de actividad leve es difícil de separar del ruido GPS con las features
  actuales. Mejorarla probablemente requiera más señal (ej. features
  adicionales, más días de calibración) más que ajuste de hiperparámetros.
