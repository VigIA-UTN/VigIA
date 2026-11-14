# Celo (LSTM): evaluación final sobre prueba

- Run evaluado: `20261027_105620_split42` (split_seed 42, semillas 42, 43, 44, 45, 46)
- Prueba: 3600 ventanas de 24 h (278 con celo), 40 animales que no están en entrenamiento ni en validación
- Umbral de decisión de cada semilla: el que maximiza F1 en **validación**, aplicado sin cambios a prueba
- Media ± desvío estándar muestral (n-1) entre semillas

## Clase celo

| Métrica | Media ± desvío |
|---|---|
| Precisión | 0.723 ± 0.112 |
| Recall | 0.794 ± 0.068 |
| F1 | 0.752 ± 0.068 |
| PR-AUC | 0.770 ± 0.053 |
| Umbral (val) | 0.7936 ± 0.1397 |

## Matriz de confusión (conteos, media ± desvío)

| Real \ Predicho | sin celo | celo |
|---|---|---|
| **sin celo** | 3231.8 ± 47.8 | 90.2 ± 47.8 |
| **celo** | 57.4 ± 19.0 | 220.6 ± 19.0 |

## Por semilla

| Semilla | Umbral | Precisión | Recall | F1 | PR-AUC | Matriz [[TN, FP], [FN, TP]] |
|---|---|---|---|---|---|---|
| init42 | 0.8924 | 0.614 | 0.853 | 0.714 | 0.722 | [[3173, 149], [41, 237]] |
| init43 | 0.5880 | 0.787 | 0.759 | 0.773 | 0.796 | [[3265, 57], [67, 211]] |
| init44 | 0.9470 | 0.868 | 0.781 | 0.822 | 0.814 | [[3289, 33], [61, 217]] |
| init45 | 0.7441 | 0.609 | 0.705 | 0.653 | 0.703 | [[3196, 126], [82, 196]] |
| init46 | 0.7966 | 0.738 | 0.871 | 0.799 | 0.813 | [[3236, 86], [36, 242]] |

## Modelo empaquetado en ml-celo

- `ml-celo/app/models/modelo_celo_lstm.keras` coincide con **init44**: mismo `model.weights.h5` (sha256 `c05f9cb7d8ddda56…`), mismo scaler (`658c662a60fe9ea4…`), probabilidades idénticas sobre las 3600 ventanas de prueba: **sí**
- Con el umbral del servicio (`CELO_THRESHOLD=0.947`): precisión 0.868, recall 0.781, F1 0.822, matriz [[3289, 33], [61, 217]]

## Criterio ML02

F1 media ≥ 0.7 y modelo empaquetado coincidente: **cumple** (F1 media 0.752).

Comando: `cd ml-training/celo && python evaluar_final.py`
