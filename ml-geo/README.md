# VigIA - Microservicio de Análisis Geoespacial (ml-geo)

Microservicio desarrollado en **Python (FastAPI)** que procesa el historial de coordenadas GPS del rodeo para generar el mapa de calor de pastoreo y medir la cohesión de cada animal con el grupo.

A diferencia de `ml-celo` y `ml-salud`, no carga ningún modelo entrenado: todos los cálculos son determinísticos y usan solo la biblioteca estándar.

## Funcionamiento

El microservicio recibe el historial de posiciones del rodeo (por defecto 7 días) y devuelve dos cosas en una sola pasada.

### Mapa de calor (densidad)

Se toma el *bounding box* de las coordenadas y se divide en una grilla de `GRID_SIZE x GRID_SIZE` (20x20 = 400 celdas). Cada celda reporta su centro y una `intensidad` normalizada: `visitas_de_la_celda / visitas_de_la_celda_más_visitada`, o sea `0..1` relativo al máximo del período. Se devuelven **todas** las celdas, incluidas las de intensidad `0`, para que el frontend pueda dibujar la grilla completa.

La grilla se ajusta a los animales filtrados (`animal_ids` / `animal_id`), para no perder detalle cuando se pide un solo animal o un rodeo: si cubriera todo el campo, el animal quedaría concentrado en una o dos celdas.

Si todos los puntos caen en la misma coordenada, el *bounding box* se expande `±0.0001` grados para evitar una división por cero.

### Cohesión: grupo principal, alerta e índice

El umbral de aislamiento (`umbral`) es el que manda el backend en `umbral_aislamiento_m`, configurado por establecimiento. Si no llega, se usa `UMBRAL_AISLAMIENTO_METROS` (100 m).

**Grupo principal.** Dos animales están "juntos" si están a `umbral` metros o menos (Haversine). Los animales juntos, directa o encadenadamente, forman un grupo. El **grupo principal** es el grupo con más animales. Un animal está **fuera del grupo** si no pertenece al grupo principal, aunque esté acompañado: un subgrupo que se aleja junto queda afuera completo.

Si dos grupos empatan en tamaño no hay grupo principal. En ese caso cada animal se compara contra el centroide del resto y queda afuera si está a más de `umbral`. Con una división pareja del rodeo esto marca a todos.

Esta definición (`_clasificar` en `app/main.py`) es la única. La usan tanto la alerta como el tiempo fuera del grupo.

**Alerta e índice (última posición).** Con la **última posición conocida** de cada animal:

- `alerta_aislamiento`: el animal está fuera del grupo principal. La lista de animales alejados son los que tienen `true`; el backend la expone como `animales_alejados`.
- `distancia_centroide_m`: distancia al centroide del grupo principal (en empate, al centroide del resto).
- `indice_cohesion`: esa distancia normalizada a `0..1`:

```text
indice_cohesion = umbral / (umbral + distancia_centroide_m)
```

- `1.0` → el animal está sobre el centroide del grupo.
- `0.5` → está a exactamente `umbral` metros del centroide.
- `→ 0` → cada vez más lejos.

En un rodeo compacto `indice_cohesion < 0.5` coincide con `alerta_aislamiento: true`, pero **no es un invariante**. En un rodeo estirado en fila, el animal de la punta sigue enlazado al grupo (sin alerta) y puede estar a más de `umbral` del centroide (índice < 0.5). La alerta es la que define si el animal se alejó; el índice es una medida continua para graficar la tendencia.

**Tiempo fuera del grupo (todo el historial).** Las caravanas no transmiten en el mismo instante, así que el historial se agrupa en intervalos de 5 minutos (`INTERVALO_COHESION_S`). En cada intervalo se usa la posición promedio de cada animal que transmitió y se clasifica con la misma regla. `tiempo_fuera_grupo_s` suma 300 s por cada intervalo en que el animal quedó afuera. Los intervalos en los que el animal no transmitió no suman: los huecos sin datos no cuentan como tiempo fuera.

Las alertas se calculan **siempre sobre el historial completo**, sin importar el filtro: el aislamiento solo tiene sentido contra todo el rodeo. Con un único animal en el historial no hay grupo contra el que medir, así que se reporta `indice_cohesion: 1.0`, `distancia_centroide_m: 0.0`, `alerta_aislamiento: false` y `tiempo_fuera_grupo_s: 0.0`.

### Endpoint Principal

`POST /api/internal/ml/geo/analyze`

**Payload esperado:**

```json
{
  "animal_id": "7",
  "animal_ids": ["7", "8", "9"],
  "historial_7d": [
    { "animal_id": "7", "lat": -34.9430, "lng": -59.2255, "timestamp": "2026-09-23T14:05:00" }
  ],
  "umbral_aislamiento_m": 150.0
}
```

- `historial_7d` es obligatorio. Con la lista vacía se devuelve `mapa_calor: []` y `alertas: []`, sin error. A pesar del nombre, la ventana la decide quien llama: `backend-worker` (job de cohesión) manda las últimas 24 h.
- `umbral_aislamiento_m` (opcional) es el umbral de aislamiento del establecimiento, en metros. Debe ser `> 0`; si no, devuelve 422. Si no se envía, se usa `UMBRAL_AISLAMIENTO_METROS`. Solo afecta a las alertas: el mapa de calor no depende del umbral.
- `animal_ids` (opcional) son los animales a incluir en el **mapa de calor**, por ejemplo los de un rodeo. `animal_id` (opcional) es el atajo para un solo animal y se ignora si viene `animal_ids`. Sin ninguno de los dos, el mapa usa todo el historial. Con una lista vacía o un id sin puntos, `mapa_calor` queda vacío pero las alertas se calculan igual.
- `timestamp` acepta cualquier ISO-8601, con o sin `Z`/offset. Se ordena **por instante**, no por string; los valores sin zona se asumen UTC. Un timestamp no parseable devuelve 422.

**Respuesta:**

```json
{
  "mapa_calor": [{ "lat": -34.9425, "lng": -59.2250, "intensidad": 0.42 }],
  "alertas": [
    {
      "animal_id": "7",
      "indice_cohesion": 0.25,
      "distancia_centroide_m": 300.0,
      "alerta_aislamiento": true,
      "tiempo_fuera_grupo_s": 2700.0
    }
  ]
}
```

Hay una entrada en `alertas` por cada animal del historial, esté alejado o no.

Ante un fallo inesperado responde **503** con `{ "error", "mensaje", "trace_id" }`, y propaga el header `X-Correlation-ID` recibido, igual que el resto de los microservicios ML.

## Configuración

| Variable | Default | Descripción |
|---|---|---|
| `UMBRAL_AISLAMIENTO_METROS` | `100.0` | Umbral de aislamiento **por defecto**: distancia máxima entre dos animales para considerarlos juntos. Solo se usa si el request no trae `umbral_aislamiento_m`. El default es del orden del diámetro de un rodeo en pastoreo. |

Se declara en `infra/docker-compose.yml` y se documenta en `infra/.env.example`. El umbral real de cada establecimiento lo configura el productor y viaja en el request.

`GRID_SIZE` (20) e `INTERVALO_COHESION_S` (300) son constantes de módulo en `app/main.py`. La primera altera la resolución del mapa que dibuja el frontend; la segunda, la granularidad de `tiempo_fuera_grupo_s`. Por eso se versionan con el código.

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

La suite (`pytest`) cubre densidad, cohesión/aislamiento, grupo principal, tiempo fuera del grupo, filtros por rodeo y el manejo de errores. No necesita red ni ningún modelo externo.

```bash
pytest tests/ -v
```

## Limitaciones conocidas

- **La grilla es un bounding box, no el campo real.** Se construye a partir de los puntos GPS, así que sus esquinas pueden caer fuera del potrero (monte, laguna, un camino). Recortarla contra el polígono de `Perimetro` (PostGIS, ya existe por establecimiento) es la mejora natural.
- **Las celdas no son cuadradas.** La grilla se divide en grados y un grado de longitud mide menos que uno de latitud, proporcionalmente al coseno de la latitud. La resolución tampoco es fija en metros: depende de la extensión del área recibida.
- **Los grupos se encadenan.** Como basta estar a `umbral` de *algún* animal del grupo, un rodeo estirado en fila cuenta como un solo grupo aunque sus puntas estén a cientos de metros. Por eso alerta e índice pueden no coincidir (ver arriba).
- **El empate marca a todos.** Si el rodeo se parte en dos mitades iguales no hay grupo principal, y con la regla de respaldo todos quedan alejados.
- **Intervalos con pocas caravanas.** En cada intervalo solo participan los animales que transmitieron. Si las caravanas transmiten cada más de 5 minutos, muchos intervalos tienen pocos animales y la clasificación se vuelve menos confiable (por ejemplo, dos caravanas a 150 m en un rodeo extendido se ven como un empate). Si pasa, la mejora es arrastrar la última posición de cada caravana durante algunos intervalos.
- **El consumo para alertas de abigeato no está implementado.** `backend-api` llama a este servicio on-demand para el mapa de calor, y `backend-worker` en batch para la cohesión (`AISLAMIENTO_SOCIAL` y `registros_cohesion`). `distancia_centroide_m` es la entrada prevista para generar también alertas `ABIGEATO`.

## Dependencias

- FastAPI & Uvicorn
- Pydantic (validación del contrato)
- Pytest & HTTPX (Testing)

Los cálculos usan únicamente `math` y `collections` de la biblioteca estándar: no hay NumPy, Pandas ni scikit-learn.
