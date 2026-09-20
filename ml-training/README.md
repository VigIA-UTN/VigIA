# ml-training

Pipelines de generación de datasets sintéticos y entrenamiento **offline**
de los modelos de Machine Learning de VigIA. A diferencia de `ml-celo`,
`ml-salud` y `ml-geo` (microservicios FastAPI que sirven inferencia en
producción), `ml-training` no se despliega — corre localmente/en CI para
producir los artefactos (`.keras`, `.joblib`) que después se copian o
auto-exportan a esos microservicios.

Contiene dos pipelines independientes, cada uno con su propio dataset
sintético y su propio README:

- [`celo/`](celo/README.md): detección de celo (clasificador poblacional, LSTM).
- [`salud/`](salud/README.md): detección de desvíos sanitarios por perfil individual (RandomForest).

Ambos comparten:
- El mismo entorno virtual/`requirements.txt` en esta carpeta (a diferencia de los microservicios, que sí son imágenes Docker independientes).
- [`gps_noise_common.py`](gps_noise_common.py): ruido de medición GPS y cuantización de `moveState` para que ambos datasets sintéticos reflejen el ruido real de un collar y la cadencia real de heartbeat — ver la sección correspondiente en `celo/README.md` para el detalle y la justificación.
- [`tests/`](tests/): suite de regresión rápida (sin TensorFlow) para esa lógica compartida, corrida en CI.

Si en algún momento se cambia la cadencia real de heartbeat, ver
[`RUNBOOK_CAMBIO_HEARTBEAT.md`](RUNBOOK_CAMBIO_HEARTBEAT.md).
