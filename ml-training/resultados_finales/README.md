# Resultados finales de los modelos (ML02)

Evaluación final multi-semilla de los dos modelos de VigIA sobre el conjunto
de **prueba**, para el informe. Corresponde al caso **ML02 - Métricas finales
de ml-celo y ml-salud** de la planilla de Casos de Prueba (hoja Ciclo 1).
ml-geo no tiene modelo entrenado (es cálculo determinístico), así que no
entra acá.

| Modelo | Run evaluado | Prueba | Precisión | Recall | F1 | Modelo en el servicio |
|---|---|---|---|---|---|---|
| Celo (LSTM), clase celo | `20261027_105620_split42` | 3600 ventanas, 278 con celo, 40 animales | 0.723 ± 0.112 | 0.794 ± 0.068 | 0.752 ± 0.068 | `init44`, coincide |
| Salud (RandomForest), desvío detectado | `20260918_195113_split42` | 5400 días, 96 con desvío, 60 animales | 0.502 ± 0.009 | 0.550 ± 0.009 | 0.525 ± 0.008 | `init42`, coincide |

Media ± desvío estándar **muestral (n-1)** sobre 5 semillas de inicialización
(42 a 46) con la misma partición por animal (`split_seed 42`). Los
`summary_5seeds.json` del entrenamiento usan el desvío poblacional, por eso
ahí los desvíos son un poco más chicos.

El detalle está en la carpeta de cada modelo:

- [`celo/resumen.md`](celo/resumen.md): métricas, matriz de confusión media ± desvío, detalle por semilla y el modelo desplegado con `CELO_THRESHOLD=0.947`. Además [`celo/matriz_confusion.png`](celo/matriz_confusion.png) y [`celo/metricas.json`](celo/metricas.json).
- [`salud/resumen.md`](salud/resumen.md): desvío detectado (binario) y severidad 0-3 por clase, con las dos matrices de confusión. Además [`salud/matriz_confusion_desvio.png`](salud/matriz_confusion_desvio.png), [`salud/matriz_confusion_severidad.png`](salud/matriz_confusion_severidad.png) y [`salud/metricas.json`](salud/metricas.json).

## Criterios de la evaluación

- **Celo:** el umbral de cada semilla es el que maximiza F1 en validación, y se aplica sin cambios a prueba. Es el mismo criterio que `train_multiseed.py`.
- **Salud:** se decide igual que `ml-salud` en producción. La severidad es el argmax de `predict_proba` y "desvío detectado" es severidad > 0. No hay umbral ajustado. Las métricas "con umbral óptimo" de `salud/README.md` usaban un umbral elegido sobre prueba que el servicio no aplica, así que no son las finales.
- **Modelo empaquetado:** se considera que coincide si cumple todo lo siguiente.
  - Celo: el `model.weights.h5` dentro del `.keras` y el scaler son idénticos a los de una de las semillas evaluadas. No se compara el `.keras` entero porque guarda la fecha en que se guardó el archivo.
  - Salud: el `.joblib` es idéntico y `features_salud.py` es el mismo en los dos lados.
  - Las predicciones sobre todo el conjunto de prueba son idénticas.
- **Criterio de aceptación (ML02):** F1 media ≥ 0.70 en celo y ≥ 0.50 en desvío de salud, y el modelo empaquetado coincide. Si algo falla, el script sale con código 1.

## Cómo reproducirlo

Entorno: el venv de `ml-training` (`requirements.txt`). Estos resultados se
generaron con Python 3.11.9, numpy 2.4.6, scikit-learn 1.9.0, TensorFlow
2.21.0 y Keras 3.15.1 (versiones guardadas en cada `metricas.json`).

Si los runs ya están en `celo/models/` y `salud/models/`, alcanza con evaluar:

```bash
cd ml-training/celo && python evaluar_final.py
cd ml-training/salud && python evaluar_final.py
```

Sin argumentos, cada script evalúa el run más reciente de su `models/`. Se
puede fijar el run con `--run-dir models/<run>`.

Los modelos productivos sí están versionados, dentro de cada microservicio
(`ml-celo/app/models/`, `ml-salud/app/models/`). Los runs de entrenamiento
con las 5 semillas **no se versionan** (`models/` está en `.gitignore`; el de
salud pesa ~260 MB). En una copia limpia del repo hay que reentrenar primero.
Los sha256 de cada semilla quedan en `metricas.json`, así que se puede
confirmar que un reentrenamiento da exactamente los mismos modelos. El entrenamiento es
determinista: semillas fijas y `enable_op_determinism` en TensorFlow, y en
estas mismas versiones da los mismos pesos. Los datasets están versionados en
`celo/dataset/` y `salud/dataset/`.

```bash
cd ml-training/celo
python train_multiseed.py --dataset-dir dataset --models-dir models --split-seed 42 --init-seeds 42 43 44 45 46
python evaluar_final.py

cd ../salud
python train_model.py --dataset-dir dataset --models-dir models --split-seed 42 --init-seeds 42 43 44 45 46 --n-estimators 600 --max-depth 0
python evaluar_final.py
```

Ojo: `train_model.py` re-exporta `init42` a `ml-salud/app/models/`. Para
celo, el despliegue es manual (ver `celo/README.md` §5).

Para regenerar los datasets desde cero, ver la sección "Uso" de cada README.
`evaluar_final.py` además verifica que la partición de prueba reconstruida
sea la misma del entrenamiento (celo) y que las matrices por semilla
coincidan con las de `run_summary.json` (campo `consistente_con_entrenamiento`
en `metricas.json`).
