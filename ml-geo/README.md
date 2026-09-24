# VigIA - Microservicio de Análisis Geoespacial (ml-geo)

Microservicio desarrollado en **Python (FastAPI)** que procesa el historial de coordenadas GPS del rodeo para generar el mapa de calor de pastoreo y medir la cohesión de cada animal con el grupo.

A diferencia de `ml-celo` y `ml-salud`, no carga ningún modelo entrenado: todos los cálculos son determinísticos y usan solo la biblioteca estándar.

## Funcionamiento

El microservicio recibe el historial de posiciones del rodeo (por defecto 7 días) y devuelve dos cosas en una sola pasada.

### Mapa de calor (densidad)

Se toma el *bounding box* de las coordenadas y se divide en una grilla de `GRID_SIZE x GRID_SIZE` (20x20 = 400 celdas). Cada celda reporta su centro y una `intensidad` normalizada: `visitas_de_la_celda / visitas_de_la_celda_más_visitada`, o sea `0..1` relativo al máximo del período. Se devuelven **todas** las celdas, incluidas las de intensidad `0`, para que el frontend pueda dibujar la grilla completa.

La grilla se ajusta a los animales filtrados (`animal_ids` / `animal_id`), para no perder detalle cuando se pide un solo animal o un rodeo: si cubriera todo el campo, el animal quedaría concentrado en una o dos celdas.

Si todos los puntos caen en la misma coordenada, el *bounding box* se expande `±0.0001` grados para evitar una división por cero.

### Índice de cohesión y alerta de aislamiento

Para cada animal se toma su **última posición conocida** y se mide la distancia (fórmula de Haversine, en metros) al centroide de las últimas posiciones del **resto** del rodeo. Esa distancia se reporta cruda en `distancia_centroide_m`.

El `indice_cohesion` la normaliza a `0..1`:

```text
indice_cohesion = UMBRAL_AISLAMIENTO_METROS / (UMBRAL_AISLAMIENTO_METROS + distancia_centroide_m)
```

- `1.0` → el animal está sobre el grupo.
- `0.5` → está exactamente sobre el umbral de aislamiento.
- `→ 0` → cada vez más aislado.

Por construcción `indice_cohesion < 0.5` es equivalente a `distancia_centroide_m > UMBRAL_AISLAMIENTO_METROS`. `alerta_aislamiento` se deriva de la distancia para no depender de la igualdad de números flotantes.

Las alertas se calculan **siempre sobre el historial completo**, sin importar el filtro: el aislamiento solo tiene sentido contra todo el rodeo. Con un único animal en el historial no hay grupo contra el que medir, así que se reporta `indice_cohesion: 1.0`, `distancia_centroide_m: 0.0` y `alerta_aislamiento: false`.

### Endpoint Principal

`POST /api/internal/ml/geo/analyze`

**Payload esperado:**

```json
{
  "animal_id": "7",
  "animal_ids": ["7", "8", "9"],
  "historial_7d": [
    { "animal_id": "7", "lat": -34.9430, "lng": -59.2255, "timestamp": "2026-09-23T14:05:00" }
  ]
}
```

- `historial_7d` es obligatorio. Con la lista vacía se devuelve `mapa_calor: []` y `alertas: []`, sin error.
- `animal_ids` (opcional) son los animales a incluir en el **mapa de calor**, por ejemplo los de un rodeo. `animal_id` (opcional) es el atajo para un solo animal y se ignora si viene `animal_ids`. Sin ninguno de los dos, el mapa usa todo el historial. Con una lista vacía o un id sin puntos, `mapa_calor` queda vacío pero las alertas se calculan igual.
- `timestamp` acepta cualquier ISO-8601, con o sin `Z`/offset. Se ordena **por instante**, no por string; los valores sin zona se asumen UTC. Un timestamp no parseable devuelve 422.

**Respuesta:**

```json
{
  "mapa_calor": [{ "lat": -34.9425, "lng": -59.2250, "intensidad": 0.42 }],
  "alertas": [{ "animal_id": "7", "indice_cohesion": 0.25, "distancia_centroide_m": 300.0, "alerta_aislamiento": true }]
}
```

Ante un fallo inesperado responde **503** con `{ "error", "mensaje", "trace_id" }`, y propaga el header `X-Correlation-ID` recibido, igual que el resto de los microservicios ML.

## Configuración

| Variable | Default | Descripción |
|---|---|---|
| `UMBRAL_AISLAMIENTO_METROS` | `100.0` | Distancia al centroide del resto del rodeo a partir de la cual un animal se considera aislado. El default es del orden del diámetro de un rodeo en pastoreo: más lejos que eso el animal dejó de seguir al grupo. |

Se declara en `infra/docker-compose.yml` y se documenta en `infra/.env.example`. Es un valor único para todos los establecimientos; hacerlo configurable por campo (para que lo ajuste el productor) es una HU pendiente.

`GRID_SIZE` (20) es una constante de módulo en `app/main.py`: cambiarla altera la resolución del mapa que dibuja el frontend, así que se versiona con el código.

## Desarrollo y Pruebas Locales

1. Instalar dependencias:
```bash
pip install -r requirements.txt
```

2. Ejecutar el servidor:
```bash
uvicorn app.main:app --reload --port 8003
```

### Ejecutar Tests

La suite (`pytest`) cubre densidad, cohesión/aislamiento, filtros por rodeo y el manejo de errores. No necesita red ni ningún modelo externo.

```bash
pytest tests/ -v
```

## Limitaciones conocidas

- **La grilla es un bounding box, no el campo real.** Se construye a partir de los puntos GPS, así que sus esquinas pueden caer fuera del potrero (monte, laguna, un camino). Recortarla contra el polígono de `Perimetro` (PostGIS, ya existe por establecimiento) es la mejora natural.
- **Las celdas no son cuadradas.** La grilla se divide en grados y un grado de longitud mide menos que uno de latitud, proporcionalmente al coseno de la latitud. La resolución tampoco es fija en metros: depende de la extensión del área recibida.
- **El aislamiento se mide contra el centroide, con umbral absoluto.** Un animal a 150 m de un rodeo desparramado alerta igual que a 150 m de un rodeo apretado. Normalizar contra la dispersión propia del rodeo sería más fino.
- **El consumo para alertas de abigeato no está implementado.** Hoy solo `backend-api` llama a este servicio, on-demand, para el mapa de calor. `distancia_centroide_m` es la entrada prevista para que `backend-worker` genere alertas `ABIGEATO` en batch.

## Dependencias

- FastAPI & Uvicorn
- Pydantic (validación del contrato)
- Pytest & HTTPX (Testing)

Los cálculos usan únicamente `math` y `collections` de la biblioteca estándar: no hay NumPy, Pandas ni scikit-learn.
