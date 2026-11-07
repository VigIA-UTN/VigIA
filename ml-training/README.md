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
- [`gps_noise_common.py`](gps_noise_common.py): ruido de medición GPS y cuantización de `moveState` para que ambos datasets sintéticos reflejen el ruido real de una caravana y la cadencia real de heartbeat — ver la sección correspondiente en `celo/README.md` para el detalle y la justificación.
- [`evaluacion_common.py`](evaluacion_common.py) y el `evaluar_final.py` de cada pipeline: evaluación final multi-semilla sobre prueba y verificación del modelo empaquetado en cada microservicio. Los resultados están en [`resultados_finales/`](resultados_finales/README.md).
- [`tests/`](tests/): suite de regresión rápida (sin TensorFlow) para esa lógica compartida, corrida en CI.

Si en algún momento se cambia la cadencia real de heartbeat, ver
[`RUNBOOK_CAMBIO_HEARTBEAT.md`](RUNBOOK_CAMBIO_HEARTBEAT.md).

## Reentrenar y reemplazar un modelo (RNF05)

Resumen para alguien que no trabajó en ML. El detalle de cada pipeline está
en su README; acá van los puntos donde es fácil trabarse.

**Requisitos.** Python 3.11 (TensorFlow no tiene instalador para 3.13 o
posterior), Docker Desktop encendido y JDK 17. Los datasets ya están
versionados: para reentrenar **no** hace falta regenerarlos.

**Entorno (una vez).** Desde `ml-training`:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

Si PowerShell dice que la ejecución de scripts está deshabilitada, correr
`Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` y reintentar
(o usar `cmd` con `.venv\Scripts\activate.bat`). Con el venv activo el prompt
muestra `(.venv)`.

**Cada pipeline se corre desde su propia carpeta**, porque las rutas
(`--dataset-dir dataset`) son relativas, y tienen scripts distintos:

| Modelo | Carpeta | Script | Despliegue al microservicio |
|---|---|---|---|
| Celo (LSTM) | `ml-training/celo` | `train_multiseed.py` | **Manual**: copiar archivos y actualizar `CELO_THRESHOLD` ([celo/README.md](celo/README.md) §5) |
| Salud (RandomForest) | `ml-training/salud` | `train_model.py` | **Automático** a `ml-salud/app/models/` ([salud/README.md](salud/README.md)) |

El entrenamiento de celo tarda varios minutos (5 semillas, en CPU). Mientras
corre se imprime un aviso de TensorFlow (`use_unbounded_threadpool`, línea
`E0000`): es inofensivo, se puede ignorar. Termina cuando aparece el
`RESUMEN 5 CORRIDAS` y vuelve el prompt.

**Verificar.**

1. `python evaluar_final.py` en la carpeta del pipeline. Por defecto
   **sobrescribe** `resultados_finales/`, que está versionado. Para probar sin
   pisarlo: `--out-dir "$env:TEMP\eval_celo"`. En celo, además,
   `--umbral-servicio <valor>` con el `CELO_THRESHOLD` real (default 0.947);
   la línea "con CELO_THRESHOLD=…" del final solo es válida con el umbral correcto.
   Mientras el modelo del microservicio no sea uno de los entrenados, el script
   termina con `FALLA ... no coincide`: es lo esperado antes de copiarlo.
2. Tests del microservicio: `pip install -r requirements.txt` dentro de
   `ml-celo` (o `ml-salud`) y `pytest tests -q`. El venv de `ml-training`
   no trae esas dependencias.
3. Servicio con el modelo nuevo: desde `infra`, `docker compose up -d --build
   ml-celo ml-salud` y `GET http://localhost:8001/health` (celo) o `:8002/health`
   (salud) deben dar `model_loaded: true`. Si el contenedor ya estaba corriendo,
   reiniciarlo (`docker compose restart ml-celo`): el modelo se carga al arrancar.
4. Pipeline del backend: desde `backend`, `.\mvnw.cmd -B verify -pl backend-worker -am`
   (no hace falta instalar Maven; requiere Docker). Ojo: estos `*IT` simulan los
   ML (`MlSimulado`), así que pasan con cualquier modelo; para ver el modelo real
   contra el worker hay que levantar el stack y usar `simulador/simulador_celo.py`
   o `simulador_salud.py` (ver `docs/DESARROLLO.md`).

**Comparar con los resultados publicados.** Los números publicados se generaron
con scikit-learn 1.9.0 (ver `resultados_finales/README.md`). Con otras versiones
(por ejemplo 1.9.1) el reentrenamiento da pesos y métricas distintos, aunque
sigan cumpliendo el criterio de aceptación.

**Antes de commitear**, `git status` no debe mostrar archivos de
`ml-training/resultados_finales/` ni de `*/dataset/` (el reentrenamiento no
los toca, salvo que se haya regenerado el dataset o corrido `evaluar_final.py`
sin `--out-dir`), ni nada bajo `backend/`.
