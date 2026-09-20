# Runbook: cambio de cadencia de heartbeat

Este documento está pensado para pegarse como prompt en una sesión futura
de Claude Code (o para seguirlo a mano) cuando cambie la frecuencia real
con la que los collares envían Heartbeat (el mensaje que trae `moveState`).

## 1. Cuándo usar este runbook

**Usalo cuando** cambie el intervalo real de heartbeat en producción (por
ejemplo, "pasamos de 30 minutos a 1 hora para ahorrar batería" o
viceversa).

**NO lo necesitás** para:
- Cambios en `--gps-noise-std-m` (el ruido de medición GPS). Eso se
  recalibra aparte si aparece más telemetría real de referencia — no
  depende de la cadencia de heartbeat.
- Cambios que no afecten la cadencia real de heartbeat en sí (por
  ejemplo, ajustes al umbral `CELO_THRESHOLD` sin reentrenar).

## 2. Por qué hace falta

`ml-training/celo/generate_dataset.py` y `ml-training/salud/generate_dataset.py`
cuantizan el `moveState` sintético en bloques fijos de
`heartbeat_interval_ticks` (default 6 = 30 min), para que el dataset de
entrenamiento refleje la granularidad real: en producción `moveState`
sólo puede cambiar cuando llega un Heartbeat real (`IngestaController.java`
hereda el último valor conocido en cada posición intermedia). Se dejó
este valor **fijo** (no un rango aleatorizado) a pedido explícito, para
que el dataset sea lo más fiel posible a la cadencia real prevista.

Si la cadencia real cambia y no se regenera el dataset, se reintroduce el
mismo tipo de desajuste train/serve que motivó agregar esta lógica: el
modelo aprendió una granularidad de `moveState` que ya no coincide con lo
que ve en inferencia.

## 3. Cómo traducir la nueva frecuencia a ticks

Cada tick del dataset sintético representa 5 minutos (`MINUTES_PER_SAMPLE`
en ambos `generate_dataset.py`). Entonces:

```
heartbeat_interval_ticks = round(nuevo_intervalo_minutos / 5)
```

Ejemplos: 15 min -> 3 ticks; 30 min -> 6 ticks (default actual); 1h -> 12
ticks; 2h -> 24 ticks. Si el intervalo no es múltiplo exacto de 5
minutos, redondear y dejarlo documentado en el commit/PR.

## 4. Comandos exactos

Reemplazar `<N>` por el valor calculado en el paso 3.

**Celo:**
```bash
cd ml-training/celo
python generate_dataset.py --n-animales 60 --dias 60 --seed 42 \
    --gps-noise-std-m 15.5 --heartbeat-interval-ticks <N>
python calibracion_dataset.py --skip-external-comparison   # gate rápido de ruido
python train_multiseed.py --dataset-dir dataset --models-dir models --split-seed 42 --init-seeds 42 43 44 45 46
```

**Salud:**
```bash
cd ml-training/salud
python generate_dataset.py --n-animales 120 --dias-evaluacion 90 --seed 42 \
    --gps-noise-std-m 15.5 --heartbeat-interval-ticks <N>
python ../celo/calibracion_dataset.py --sim-dataset dataset/telemetria.parquet --skip-external-comparison
python train_model.py --dataset-dir dataset --models-dir models --split-seed 42 --init-seeds 42 43 44 45 46
```

## 5. Deploy

- **Celo** (manual — `train_multiseed.py` no auto-exporta): elegir el
  seed con mejor PR-AUC/F1 de `models/<run>/summary_5seeds.json`, copiar
  `modelo_celo_lstm.keras` y `scaler_celo.joblib` de
  `models/<run>/init<seed>/` a `ml-celo/app/models/`, releer
  `best_threshold_f1` de ese `run_summary.json` y actualizar
  `CELO_THRESHOLD` en `infra/.env.example` (línea con el comentario de
  "Valor actual"), `infra/.env` local, y el comentario en
  `simulador/simulador.py`.
- **Salud**: `train_model.py` ya auto-exporta `init42` a
  `ml-salud/app/models/modelo_salud_rf.joblib` y copia
  `features_salud.py` — sin paso manual. No hay umbral configurable por
  env var para salud (severidad es argmax multiclase).

## 6. Checklist de verificación

1. `calibracion_dataset.py --skip-external-comparison` sobre ambos
   datasets regenerados: `gps_noise_floor.ok == true`.
2. `pytest ml-training/tests/ -v` en verde.
3. Antes/después con la muestra real de GPS quieto: cargar el modelo
   viejo y el nuevo directamente (sin pasar por la API) sobre un payload
   sintético que replique el ruido real de un collar quieto y `moveState`
   bajo, cuantizado con el nuevo `<N>`; confirmar que ninguno de los dos
   dispara un falso positivo de celo, y que un input de alta actividad
   real sigue superando el umbral.
4. `pytest ml-celo/tests/ -v` y `pytest ml-salud/tests/ -v` en verde (no
   deberían verse afectados, ambos mockean el modelo).

## 7. Qué actualizar además del código

- El valor default documentado en `ml-training/celo/README.md` (sección
  "Ruido de GPS y granularidad de moveState") y en el ejemplo de uso de
  `ml-training/salud/README.md`.
- Este mismo runbook, si el procedimiento cambia.
