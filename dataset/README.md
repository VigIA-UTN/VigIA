# Dataset de telemetría para detección de celo — VigIA

## 1. Origen del dataset

**Sintético**, generado con `generate_dataset.py`. No se encontró un dataset
público ya etiquetado como *celo / no-celo* en ventanas de 24hs con
aceleración cruda (x,y,z). Sí existen dos tipos de fuentes externas, usadas
como referencia para calibrar el generador:

| Fuente | Qué aporta | Licencia / acceso |
|---|---|---|
| Morales-Vargas et al. (2025), *"A dataset for detecting walking, grazing, and resting behaviors in free-grazing cattle using IoT collar IMU signals"*, Front. Vet. Sci. 12:1630083. DOI [10.3389/fvets.2025.1630083](https://doi.org/10.3389/fvets.2025.1630083). Repo: [github.com/WASP-lab/db-cow-walking](https://github.com/WASP-lab/db-cow-walking) | Vacas lecheras Holstein en pastoreo extensivo a campo abierto (Temuco, Chile). El propio paper señala que este sistema de manejo (pastoril, extensivo) es el mismo que se usa en Argentina, Uruguay, Brasil, Australia y Nueva Zelanda — a diferencia de otros datasets similares hechos en sistemas confinados o con razas poco representativas del campo local. Se usó para calibrar los órdenes de magnitud de amplitud/varianza de la aceleración durante caminata-pastoreo vs. reposo-rumia. | Artículo: CC BY 4.0. Repo público en GitHub (verificar licencia del repo antes de redistribuir datos derivados). |
| Alvarez et al. (2025), *"Estrus Detection and Optimal Insemination Timing in Holstein Cattle Using a Neck-Mounted Accelerometer Sensor System"*, DOI [10.3390/s25175245](https://doi.org/10.3390/s25175245) | Collar RUMI (acelerometro de cuello): sensibilidad 90,9%, especificidad 100% detectando celo por patrón de actividad. Confirma que la señal de acelerómetro es suficiente para esta tarea. | Open access (MDPI Sensors). |
| Frontiers (2023), *"Behavioral changes to detect estrus using ear-sensor accelerometer..."*, DOI [10.3389/fanim.2023.1149085](https://doi.org/10.3389/fanim.2023.1149085) | El reposo cae y los comportamientos "activo"/"muy activo" suben significativamente el día del celo vs. una semana antes. Duración típica del celo: 8–19hs. | Open access. |

Ninguna de estas tres fuentes se redistribuye: solo se usaron sus valores
reportados (rangos de amplitud, % de cambio de actividad, duración del celo)
como parámetros del generador. No hay conflicto de licencia porque no se
copian datos, solo se referencian magnitudes publicadas.

## 2. Proceso de generación sintética

Script: `generate_dataset.py`. Por cada caravana y cada día se simula una
ventana de 24hs (288 lecturas, una cada 5 minutos):

1. **Actividad basal circadiana**: más movimiento de día (pastoreo), menos de
   noche, siguiendo un patrón senoidal.
2. **Bouts de rumia**: intervalos de baja varianza en la señal (3 bouts de
   ~36min en días de celo, 6 bouts de ~1h en días normales — la reducción de
   rumia en celo está tomada de la literatura citada arriba).
3. **Ventana de celo** (solo si `label = celo`): entre 8 y 12hs continuas
   dentro de las 24hs con amplitud de movimiento 1.8x–2.6x mayor, más 3 a 8
   picos transitorios de alta amplitud simulando conducta de monta.
4. **Ruido gaussiano** por eje, más un offset de gravedad (~1g) en el eje z
   para simular la orientación del collar/caravana.
5. **Batería**: descenso lineal simulado con ruido leve (no afecta el label).

Cada muestra queda representada como un tensor `(288, 3)` — 288 timesteps ×
(accel_x, accel_y, accel_z) — más metadata (`caravana_id`, `fecha`, `label`,
`nivel_rumia_prom`, `bateria_prom`).

**Reproducibilidad**: toda la generación depende de `--seed` (default 42).
Correr `python3 generate_dataset.py --seed 42` siempre da exactamente el
mismo dataset. Los parámetros de tamaño (`--n-animales`, `--dias-por-animal`,
`--p-celo`) también quedan registrados en `dataset/dataset_summary.json`.

## 3. Estructura de archivos generados

```
dataset/
  X.npy                 # array (n_samples, 288, 3) float32 — ventanas de accel x,y,z
  y.npy                 # array (n_samples,) int8 — 1 = celo, 0 = no_celo
  metadata.csv           # sample_id, caravana_id, fecha, label, nivel_rumia_prom, bateria_prom
  dataset_summary.json    # resumen: distribución de clases, parámetros, seed
```

## 4. Distribución de clases (corrida de referencia, seed=42)

Parámetros: 60 caravanas × 30 días = 1800 muestras, `p_celo=0.5`.

| Clase | Cantidad | % |
|---|---|---|
| no_celo | 950 | 52.8% |
| celo | 850 | 47.2% |

Nota: en la realidad el celo ocurre ~1 día cada ~21 días (ciclo estral), por
lo que un dataset "naturalista" estaría fuertemente desbalanceado. Acá se
sobre-muestrea la clase celo intencionalmente (`p_celo=0.5` por muestra
generada) para entrenar un clasificador balanceado, como es práctica común
en detección de eventos poco frecuentes. Esto queda documentado y es
ajustable vía `--p-celo`.

## 5. Versionado

Recomendado (consistente con la infra AWS del proyecto):

- Bucket S3 versionado: `s3://vigia-datasets/celo-telemetria/v1/` con
  versioning habilitado en el bucket, subiendo `X.npy`, `y.npy`,
  `metadata.csv` y `dataset_summary.json` como un set atómico por versión.
- Alternativa/complemento: trackear con **DVC** (`dvc add dataset/`) en el
  repo del proyecto, apuntando el remoto DVC al mismo bucket S3 — así queda
  versionado junto con el código que lo generó y es reproducible con
  `dvc repro` o simplemente re-corriendo el script con el mismo seed.
- Cada versión subida debe ir acompañada de este README y del
  `dataset_summary.json` correspondiente para mantener trazabilidad de qué
  parámetros la generaron.

## 6. Limitaciones y próximos pasos

- Es un dataset sintético calibrado con literatura, no con datos reales de
  las caravanas VigIA. Cuando haya lecturas reales del hardware, este
  dataset debería recalibrarse o reemplazarse (comparar distribuciones,
  validar que el modelo entrenado con datos sintéticos generalice).
- Los parámetros de amplitud/duración están tomados de estudios en ganado
  lechero (Holstein); si las caravanas se despliegan sobre otras razas
  (Angus, Hereford, Braford, etc., comunes en el campo argentino), conviene
  revisar si el patrón de hiperactividad en celo se mantiene igual.
