# Salud (RandomForest): evaluación final sobre prueba

- Run evaluado: `20260918_195113_split42` (split_seed 42, semillas 42, 43, 44, 45, 46)
- Prueba: 5400 días-animal, 60 animales que no están en entrenamiento
- Decisión igual a ml-salud: severidad = argmax de `predict_proba`, desvío detectado = severidad > 0 (sin umbral ajustado)
- Media ± desvío estándar muestral (n-1) entre semillas

## Desvío detectado (binario)

| Métrica | Media ± desvío |
|---|---|
| Precisión | 0.502 ± 0.009 |
| Recall | 0.550 ± 0.009 |
| F1 | 0.525 ± 0.008 |

| Real \ Predicho | sin desvío | desvío |
|---|---|---|
| **sin desvío** | 5251.6 ± 1.1 | 52.4 ± 1.1 |
| **desvío** | 43.2 ± 0.8 | 52.8 ± 0.8 |

## Severidad (0-3)

| Clase | Casos en prueba | Precisión | Recall | F1 |
|---|---|---|---|---|
| Normal (0) | 5304 | 0.992 ± 0.000 | 0.990 ± 0.000 | 0.991 ± 0.000 |
| Baja (1) | 66 | 0.299 ± 0.011 | 0.345 ± 0.013 | 0.321 ± 0.012 |
| Media (2) | 12 | 0.600 ± 0.000 | 0.750 ± 0.000 | 0.667 ± 0.000 |
| Alta (3) | 18 | 1.000 ± 0.000 | 0.778 ± 0.000 | 0.875 ± 0.000 |

F1 macro: 0.713 ± 0.003

| Real \ Predicho | Normal (0) | Baja (1) | Media (2) | Alta (3) |
|---|---|---|---|---|
| **Normal (0)** | 5251.6 ± 1.1 | 52.4 ± 1.1 | 0.0 ± 0.0 | 0.0 ± 0.0 |
| **Baja (1)** | 41.2 ± 0.8 | 22.8 ± 0.8 | 2.0 ± 0.0 | 0.0 ± 0.0 |
| **Media (2)** | 2.0 ± 0.0 | 1.0 ± 0.0 | 9.0 ± 0.0 | 0.0 ± 0.0 |
| **Alta (3)** | 0.0 ± 0.0 | 0.0 ± 0.0 | 4.0 ± 0.0 | 14.0 ± 0.0 |

## Por semilla

| Semilla | Precisión desvío | Recall desvío | F1 desvío | F1 macro severidad | Matriz desvío [[TN, FP], [FN, TP]] |
|---|---|---|---|---|---|
| init42 | 0.505 | 0.552 | 0.527 | 0.714 | [[5252, 52], [43, 53]] |
| init43 | 0.491 | 0.542 | 0.515 | 0.710 | [[5250, 54], [44, 52]] |
| init44 | 0.510 | 0.552 | 0.530 | 0.715 | [[5253, 51], [43, 53]] |
| init45 | 0.509 | 0.562 | 0.535 | 0.717 | [[5252, 52], [42, 54]] |
| init46 | 0.495 | 0.542 | 0.517 | 0.711 | [[5251, 53], [44, 52]] |

## Modelo empaquetado en ml-salud

- `ml-salud/app/models/modelo_salud_rf.joblib` coincide con **init42**: mismo archivo (sha256 `8197b4a150e76d48…`), predicciones idénticas sobre los 5400 días de prueba y mismo `features_salud.py`: **sí**
- Ese modelo: desvío P 0.505, R 0.552, F1 0.527, matriz [[5252, 52], [43, 53]]

## Criterio ML02

F1 media de desvío ≥ 0.5 y modelo empaquetado coincidente: **cumple** (F1 media 0.525).

Comando: `cd ml-training/salud && python evaluar_final.py`
