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
  quede sincronizada.
- `train_model.py`: entrena el `RandomForestClassifier`, con el mismo
  estándar de rigor metodológico usado en `ml-training/celo` (partición
  por animal sin fuga de información, semillas fijas, 5 corridas con
  distinta semilla del estimador sobre la misma partición, reporte de
  media ± desvío estándar, PR-AUC y umbral óptimo de F1 para la métrica
  binaria "desvío detectado").

## Uso

```bash
python3 generate_dataset.py --n-animales 120 --dias-evaluacion 90 --seed 42 --out-dir dataset
python3 train_model.py --dataset-dir dataset --models-dir models
```

El modelo entrenado (y `features_salud.py`) se exportan automáticamente a
`ml-salud/app/`.

## Resultados de referencia (dataset sintético, 120 animales, 90 días de evaluación, seed 42)

Media ± desvío estándar sobre 5 corridas con distinta semilla del
estimador, misma partición de datos (80/20 por animal):

| Métrica | Valor |
|---|---|
| Accuracy (multiclase) | 0.991 ± 0.001 |
| F1 macro (multiclase) | 0.861 ± 0.009 |
| PR-AUC (desvío sí/no) | 0.898 ± 0.002 |
| Precisión (desvío, umbral óptimo) | 0.967 ± 0.010 |
| Recall (desvío, umbral óptimo) | 0.800 ± 0.008 |
| F1 (desvío, umbral óptimo) | 0.876 ± 0.007 |

La varianza entre semillas es baja (todos los desvíos estándar por
debajo de 0.01 salvo el F1 macro multiclase), consistente con lo
esperado de un ensamble de árboles frente a la varianza observada en la
LSTM de celo.

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
- No se hizo búsqueda de hiperparámetros (n_estimators/max_depth fijos);
  `ml-training/celo/optimize_rnn_ga.py` tiene un precedente de búsqueda
  con algoritmo genético que podría adaptarse acá si se justifica.
