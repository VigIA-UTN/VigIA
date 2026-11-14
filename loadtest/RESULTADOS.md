# Prueba de carga: línea de base (25.000 sensores, 500 clientes)

Cuatro corridas del 17/10/2026 sobre el stack de Docker local, con el código después de las tareas 1 a 6 de `tasks/plan-carga.md` y los arreglos de sesión compartida y `ExitOnOutOfMemoryError`. Los datos crudos de cada corrida están en `resultados/<corrida>/`: `muestras.csv` (observador), `resumen.txt`, `k6-resumen.txt`, `k6.csv.gz` y `bitacora.txt`.

## Configuración

| | |
|---|---|
| Máquina | Windows 11 con Docker Desktop: 32 CPU y 15,4 GB compartidos por todos los contenedores, k6 incluido. Sin límites por contenedor |
| Versiones | TimescaleDB 2.29.2 (PostgreSQL 16, `max_connections` 100), Java 17, Spring Boot 3.3.1, k6 1.8.1 |
| JVM | API `-Xmx512m`, worker `-Xmx128m` (`backend/Dockerfile`) |
| Pools | Hikari por defecto: 10 conexiones en la API y 10 en el worker |
| ML | ml-celo, ml-salud y ml-geo: un proceso uvicorn cada uno (con `--reload`) |
| Jobs | Los horarios de Railway: celo y salud cada 6 h, geo cada 15 min, anestro a las 2 AM. Celo, salud y geo corren de a uno |
| Sensores | 25.000: un GNSS cada 5 min y un heartbeat por hora cada uno, ~90 uplinks/s |
| Clientes | 500 productores: 1.500 streams SSE abiertos y una recarga de animales, mapa y alertas cada ~60 s, ~25 requests/s |
| Datos | Semilla de 500 × 50 con 24 h de historial y las últimas 6 h pendientes para celo y salud (1.875.000 filas). El worker se reinicia al empezar, así el ciclo de 6 h corre dentro de la prueba |

**Vara del webhook** (timeout por defecto de TTN, 5 s): errores < 0,1 %, p99 < 1 s y ninguno de 5 s o más. **Clientes:** p95 < 2 s.

## Corridas

| | Estable | +1 ms a la base | RabbitMQ pausado | Ráfaga |
|---|---|---|---|---|
| Duración | 90 min | 90 min | 15 min (pausa de 60 s en el minuto 5) | 3 min (se cortó sola) |
| Uplinks | 487.480 | 487.500 | 81.135 | 54.214, subiendo hasta ~640/s |
| Webhook p50 / p95 / p99 | 7 / 18 / 102 ms | 24 / 32 / 130 ms | 8 / 89 / 664 ms | 13 ms / 1,41 s / 6,94 s |
| Webhook máximo | 2,9 s | 1,9 s | 4,96 s | 10 s (timeout) |
| Errores del webhook | 0 | 1 (500) | 1 (500) | 317 (0,58 %, timeouts) |
| Minutos que cumplen la vara | 88 de 91 | 88 de 91 | 13 de 16 | 1 de 4 |
| Navegación p95: animales / mapa / alertas | 57 / 132 / 5 ms | 225 / 351 / 11 ms | 58 / 133 / 5 ms | 142 / 308 / 12 ms |
| Hikari API: máx. activas (p95), máx. en espera | 6 (3), 0 | **10 (8)**, 3 | 4 (3), 0 | 10 (3), 37 |
| Hikari worker: máx. activas (p95), máx. en espera | 10 (2), 33 | 10 (4), 59 | 10 (10), 33 | 10 (10), **187** |
| Postgres: máx. `active` / `idle in transaction` | 9 / 6 | 10 / 12 | 11 / 9 | 10 / 9 |
| CPU pico: base / API / worker | 126 / 355 / 202 % | 99 / 371 / 161 % | 115 / 422 / 144 % | 273 / 1567 / 909 % |
| CPU ml-celo / ml-salud mientras corren | ~83 % / ~84 % (un núcleo) | — / ~74 % | ~82 % / — | ~85 % / — |
| Memoria: base / API / worker / k6 | 4,0 GB / 1,4 GB / 756 MB / 1,8 GB | 4,2 GB / 1,4 GB / 721 MB / 1,8 GB | — | — |
| Reinicios de API o worker | 0 | 0 | 0 | 0 |

- **Los 3 minutos fuera de la vara en las corridas largas:** el primero (worker recién reiniciado, en frío) y dos picos durante el ciclo de celo y salud (p99 2,1–2,2 s y máximo 2,9 s en la estable; p99 1,1–1,3 s con +1 ms). Ninguno llegó a 5 s.
- **Los errores del webhook en las corridas estables son el `UPDATE 0` de TimescaleDB:** un heartbeat y un job actualizan la misma fila al mismo tiempo, y la base aplica solo uno (ver "Hallazgos"). Fue 1 de 487.500 con +1 ms y 1 de 81.135 en la de RabbitMQ; en el checkpoint B, 2 de 54.134.
- **Los streams:** cada uno se reconecta a los 30 min, cuando el servidor cierra el `SseEmitter`. k6 cuenta ese cierre como un request fallido (~3.000 por corrida larga, ~0,5 % del total de requests) y la API lo loguea como ERROR. No es una falla: el navegador también reconecta.

### El ciclo de 6 h (1,875 millones de filas pendientes)

| | Estable | +1 ms |
|---|---|---|
| Salud | **52,6 min**, 25.000 predicciones (~8 animales/s) | **94,7 min**, 24.830 predicciones (~4,4/s) |
| Celo | **34,2 min**, 24.787 predicciones (~12 caravanas/s) | No llegó a empezar en los 90 min |
| Ciclo completo | **~87 min de 6 h (24 %)** | **Más de 95 min; estimado ~2,5–3 h** (si celo se estira como salud, ~60 min) |
| Geo | Esperó todo el ciclo: 87 min entre largadas, contra su intervalo de 15 | Igual: 100 min entre largadas |

El orden (salud primero o celo primero) lo decide qué tarea programada toma el lock antes al arrancar el worker.

### RabbitMQ pausado

- **Pausado de 15:31:15 a 15:32:15.** En el minuto de la pausa, el webhook llegó a p99 4,48 s y máximo **4,96 s**, al borde de los 5 s de TTN, sin errores. Al minuto siguiente estaba en p99 19 ms.
- **El publish a RabbitMQ bloquea el hilo del webhook mientras dura la pausa.** Desde la tarea 1 no retiene conexión a la base, así que el pool no se agota por eso.
- **Con una pausa de más de ~5 s,** los uplinks de TTN empezarían a vencer.

### Ráfaga (con los clientes andando)

- **Límite: ~318 uplinks/s,** el último minuto que cumple la vara. Es ~3,5 veces la carga normal. El minuto siguiente, a ~477/s, ya fue a p99 2,3 s.
- **Se cortó sola a los 174 s,** con una tasa objetivo de ~643/s (p99 6,9 s, 0,58 % de errores, todos timeouts de 10 s). k6 descartó 5.235 iteraciones por quedarse sin VUs.
- **El worker se quedó sin conexiones** (10 de 10 con hasta 187 en espera), y la navegación de los clientes también se resintió: la API llegó a 37 en espera, y el mapa a p99 1,3 s.
- **Esta vez no hubo `OutOfMemoryError`:** la tarea 8 llegó a ~850/s sin clientes. Si pasa, desde ahora el worker sale y Docker lo reinicia.

## Hallazgos durante la prueba

Detalles y estado de cada uno en `tasks/plan-carga.md`, "Noticed".

| Hallazgo | Estado |
|---|---|
| El job de anestro con 25.000 animales no terminaba (12 min y `OutOfMemoryError`) y frenaba a celo, salud y geo | Arreglado (tareas 4–6): ~2 s, a las 2 AM, en su propio hilo |
| En TimescaleDB, el segundo de dos `UPDATE` simultáneos sobre la misma fila no actualiza nada | Entre jobs, evitado (celo, salud y geo de a uno). Webhook contra job: 1–2 uplinks perdidos cada ~50.000–500.000 |
| Sin memoria, la JVM quedaba viva pero sin atender, hasta reiniciarla a mano | Arreglado en el Dockerfile (`ExitOnOutOfMemoryError`). Falta revisar Railway (ver `MEMORY.md`) |
| Requests simultáneos de usuarios distintos con la misma cookie de sesión se mezclaban las identidades | Arreglado (`JwtAuthFilter`); medido: 530 de 2.000 antes, 0 después |
| Cada 403 crea una sesión HTTP que dura 30 min; muchos 403 llenan la memoria de la API | Pendiente |
| Salud saltea a cualquier animal con un solo uplink perdido en 24 h (necesita 288 puntos) | Pendiente: decisión de producto |
| El cierre de cada stream SSE a los 30 min se loguea como ERROR | Pendiente: ruido en los logs |

## Conclusiones

**1. ¿Esta máquina aguanta 25.000 sensores? Sí.**
- **El webhook,** a ritmo real: 0 errores en 487.480 uplinks, p99 102 ms y 88 de 91 minutos dentro de la vara. Los minutos fuera no pasaron de 2,9 s.
- **Los 500 clientes,** con los streams abiertos, quedaron muy por debajo de su vara (p95 < 140 ms).
- **El ciclo de 6 h** entra holgado (87 min).
- **Margen:** ~3,5 veces la carga normal antes de salir de la vara.
- **Con +1 ms** el webhook sigue bien, pero el ciclo y la navegación se estiran (abajo).

**2. El primer cuello de botella: los modelos de ML, y cómo se los llama.**
- **Un núcleo cada uno:** ml-salud y ml-celo trabajan a ~83 % todo el ciclo, que es un núcleo, porque son un proceso uvicorn cada uno.
- **Una llamada por animal, en serie:** cada job hace una llamada por caravana, de a una, y los tres jobs corren de a uno.
- **El ciclo:** 87 min sin latencia; con 1 ms más por consulta, salud solo ya tarda 95 min.
- **Detrás vienen:**
  - **El pool del worker,** que se llena (10 de 10) durante el ciclo y en las ráfagas. Cada chunk retiene su conexión durante las llamadas al modelo.
  - **El pool de la API con latencia** (p95 8 de 10 con +1 ms), por el mapa: ~4 consultas por animal hacen +1 ms → +200 ms por request.

**3. Pools recomendados.**
- **Worker: 20.** Toca 10 con 33–59 en espera durante el ciclo, y 187 en la ráfaga.
- **API: 20.** Con +1 ms, su p95 ya es 8 de 10.
- **Postgres:** con `max_connections` en 100 alcanza (20 + 20 + mantenimiento).
- **Mejor que solo agrandar:**
  - **Worker:** que no retenga la conexión mientras espera al modelo; es la causa de que se llene.
  - **API:** que el mapa no haga consultas por animal; es lo que la latencia multiplica.
  - **Heap del worker:** subir `-Xmx128m`. En régimen anda (756 MB de RSS), pero es lo que se agotó en la ráfaga de la tarea 8.

**4. Los números de partida para el plan de ML a escala.**
- **Tasas, sin latencia:** celo ~12 caravanas/s y salud ~8 animales/s, con un proceso por modelo y llamadas de a una.
- **Con +1 ms:** salud baja a ~4,4/s, y celo debería bajar parecido.
- **Ciclo de 25.000 animales:** ~87 min sin latencia (24 % de las 6 h) y ~2,5–3 h estimado con 1 ms (~45 %).
- **Hasta dónde da:** crece lineal con los animales, así que con la latencia de Railway deja de entrar en 6 h a partir de ~50.000.
- **Las palancas, de la más barata a la más cara:**
  1. Varios workers de uvicorn por modelo, sin `--reload`.
  2. Predicciones en lote: varias caravanas por llamada.
  3. No retener la conexión a la base durante la llamada.
  4. Paralelizar por caravana sin que dos jobs escriban la misma fila a la vez.
