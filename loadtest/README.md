# Prueba de carga local (25.000 sensores)

Herramientas para simular 500 clientes × 50 sensores contra el stack de Docker. El plan está en `tasks/plan-carga.md`.

Todos los comandos se corren desde la raíz del repo.

## Stack en modo carga

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.carga.yml up -d --build
```

`infra/docker-compose.carga.yml` cambia cuatro cosas respecto del stack normal:

- **API y worker con el perfil `prod`.** Con `dev` (el de `infra/.env`), `DataInitializer` borra la base y la vuelve a sembrar cada vez que arranca la API, y se perdería la semilla de carga.
- **Métricas de actuator expuestas** (`health,metrics`), para leer el pool de Hikari:
  ```bash
  curl localhost:8080/actuator/metrics/hikaricp.connections.active   # API
  curl localhost:8081/actuator/metrics/hikaricp.connections.active   # worker
  ```
- **Los horarios de los jobs de Railway:** celo y salud cada 6 h, geo cada 15 min y anestro a las 2 AM. El stack normal los corre cada minuto.
- **Un servicio `k6`** (perfil `carga`, no arranca con `up`). Monta esta carpeta en `/scripts` y recibe `API_URL`, `WORKER_URL` y `TTN_API_KEY`:
  ```bash
  docker compose -f infra/docker-compose.yml -f infra/docker-compose.carga.yml run --rm k6 version
  ```

## Datos: 500 clientes × 50 sensores

```bash
cat loadtest/trayectoria.sql loadtest/seed.sql \
  | docker exec -i vigia-db psql -U vigia_user -d vigia -q -v perfil="$(cat loadtest/perfil.json)"
```

Tarda ~1 minuto (63 s en esta máquina; 61 s son la telemetría). Deja el sistema como en producción justo antes de un ciclo de celo y salud:

| Qué | Cuántos |
|---|---|
| Usuarios `carga-001@vigia.dev` … `carga-500@vigia.dev`, rol PRODUCTOR, contraseña `productor123` | 500 |
| Establecimientos `Carga 001` … (empleado: su usuario), con un rodeo y un perímetro de ~1 km | 500 |
| Caravanas `LT-00001` … `LT-25000` (`dev_eui` = código), asignadas hace 30 días; animales activos | 25.000 |
| Perfiles de salud (`perfil.json`), calculados ayer: nadie está en calibración ni recalibra | 25.000 |
| Estado reproductivo `GESTANTE`: el job de anestro las recorre pero no alerta (sin esto alertaría a todas) | 25.000 |
| Telemetría: 24 h cada 5 min hasta el momento de la semilla. Geo ya la procesó toda; celo y salud tienen pendientes las últimas 6 h (lo que procesa cada ciclo de Railway) | 7.200.000 (1.800.000 pendientes) |

Otro tamaño: `-v clientes=10 -v sensores=5`. Otras horas pendientes: `-v pendientes_h=0` deja todo procesado.

Para medir salud con uplinks perdidos (`tasks/todo-salud.md`):
- `-v dias=8`: 8 días de historial en vez de 24 h.
- `-v perdida=0.02`: pierde al azar ese 2 % de las filas de telemetría.
- `-v sin_perfil=0.2`: ese 20 % de los animales queda sin perfil de salud; con `dias=8`, el próximo ciclo de salud los calibra.

Sin estas variables la semilla queda igual.

**Movimiento.** `trayectoria.sql` define la posición de cada sensor en función del tiempo (dos círculos superpuestos alrededor de su lugar en el rodeo) y un `moveState` como el pastoreo normal del simulador. Historial, perfil y k6 usan la misma fórmula, así que empalman. Con ventanas de 24 h generadas así, ml-salud no detectó desvío en 40 de 40 y ml-celo no detectó celo en 40 de 40 (confianza máxima 0,39): la carga no genera alertas falsas.

**`perfil.json`.** Es el perfil de ml-salud para 7 días (2016 puntos) de ese movimiento. Solo hay que regenerarlo si cambian `trayectoria.sql` o las features de ml-salud:

```bash
cat loadtest/trayectoria.sql loadtest/calibracion.sql \
  | docker exec -i vigia-db psql -U vigia_user -d vigia -qtA \
  | curl -s -H 'Content-Type: application/json' -d @- localhost:8002/api/internal/ml/salud/perfil \
  | python -c "import json,sys; json.dump(json.load(sys.stdin)['perfil'], sys.stdout)" > loadtest/perfil.json
```

**Limpiar.** Borra todo lo sembrado y lo que la carga generó sobre esos animales (alertas, estados, cohesión, telemetría). Lo demás queda como estaba. Tarda ~30 s:

```bash
docker stop vigia-backend-worker
docker exec -i vigia-db psql -U vigia_user -d vigia < loadtest/limpiar.sql
docker start vigia-backend-worker
```

Con el worker parado: si no, sus jobs pueden estar actualizando las mismas filas y el borrado termina en `deadlock detected` (se deshace entero; alcanza con repetirlo). También marca como `ABANDONED` las corridas de jobs que quedaron `STARTED` porque el worker se paró a mitad; si no, `observar.py` las mostraría en curso.

## Tráfico: `carga.js`

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.carga.yml run --rm k6 run -e DURACION=30m carga.js
```

Se configura con `-e VARIABLE=valor`:

| Variable | Por defecto | Qué es |
|---|---|---|
| `ESCENARIOS` | `sensores` | Separados por coma: `sensores` o `rafaga` (alternativos), `clientes_streams`, `clientes_navegacion` |
| `DURACION` | `30m` | Cuánto dura `sensores` |
| `RATE` | `1` | Multiplica la tasa de `sensores` (`RATE=2`: cada sensor manda el doble) |
| `CLIENTES`, `SENSORES_POR_CLIENTE` | `500`, `50` | Tienen que coincidir con la semilla |
| `INTERVALO_S` | `300` | Cada cuánto manda un GNSS cada sensor |
| `RAFAGA_DESDE`, `MAX`, `RAMPA`, `MAX_VUS` | `90`, `2000`, `10m`, `2000` | La rampa de `rafaga`: de `RAFAGA_DESDE`/s a `MAX`/s en `RAMPA` |
| `PENSAR_S` | `60` | Cada cuánto recarga cada productor en `clientes_navegacion` (con jitter de ±50 %) |

**`sensores`.** Los 25.000 sensores a su ritmo real, con tasa constante: 25.000 GNSS cada 5 minutos (83,3/s) y un heartbeat por hora por sensor, repartidos entre las rondas (~6,9/s). Es un modelo abierto: los uplinks salen a su hora aunque el servidor se atrase, como los sensores de verdad.
- **Payload:** el de TTN v3, como `simulador/simulador.py`.
- **Posición:** la de `trayectoria.sql`, así que continúa el historial de la semilla.
- **`f_cnt`:** el segundo epoch del arranque más dos por ronda (más uno para el heartbeat). Crece entre corridas, así que el filtro de frames duplicados del worker no descarta ninguno.
- **Vara de aprobación** (el timeout por defecto de TTN es 5 s): errores < 0,1 %, p99 < 1 s, ninguno de 5 s o más, y el 99,9 % con respuesta `OK`. El webhook responde 200 también cuando ignora un uplink; el cuerpo dice si lo procesó.

**`rafaga`.** Los mismos uplinks, con la tasa subiendo de forma lineal. Se corta solo cuando los errores pasan el 1 % o el p99 los 5 s, y al final informa la tasa a la que llegó. El límite es la tasa más alta cuyo minuto todavía cumple la vara de `sensores`; para calcularlo, guardar los datos crudos con `--out csv=resultados/<corrida>/k6.csv.gz` (comprimido: sin comprimir son ~1 KB por request, 120 MB en 4 minutos de ráfaga) y pasárselos al observador (`--resumen`). k6 sale con código 99 cuando la corta un umbral.

**Medido en esta máquina** (semilla sin pendientes, sin clientes):

| Corrida | Requests | p50 | p95 | p99 | Máximo | Errores |
|---|---|---|---|---|---|---|
| `sensores`, 5 min a 1× | 27.085 (90/s), 100 % `OK` | 6 ms | 10 ms | 65 ms | 531 ms | 0 |
| `sensores`, 2 min a 2× | 21.528 (179/s), 100 % `OK` | 6 ms | 19 ms | 933 ms | 2,55 s | 0 |
| `rafaga` hasta 2000/s en 10 min | 109.337 (459/s de promedio) | 9 ms | 1,24 s | 9,99 s | 10 s (timeout) | 1,16 %; cortó a los 238 s, en ~848/s |

**Ojo con la ráfaga: tumba el worker.** En esta prueba el worker se quedó sin heap (`OutOfMemoryError: Java heap space`; el worker tiene 128 MB de heap, `-Xmx128m` en `backend/Dockerfile`) hacia el final de la rampa. Murieron también los hilos que aceptan conexiones de Tomcat, y quedaron tomadas las 10 conexiones del pool, sin dueño. No se recupera solo, ni siquiera responde `/actuator/health`: después de una ráfaga, `docker restart vigia-backend-worker`.

**Clientes.** Los 500 productores de la semilla. En `setup()` k6 inicia sesión con cada uno y busca su establecimiento.
- **`clientes_streams`:** 3 streams SSE por productor, como la página del mapa (2 de alertas y 1 de telemetría): 1.500 conexiones abiertas. Cada uno es un GET largo, que del lado del servidor ocupa lo mismo que un `EventSource`. El servidor lo cierra a los 30 min y k6 reconecta.
- **`clientes_navegacion`:** cada productor recarga `/animales`, `/animales/mapa` y `/alertas` de su establecimiento cada `PENSAR_S`. Vara: p95 < 2 s por endpoint (el resumen trae uno por cada uno) y < 1 % de errores.
- **Contar los streams abiertos** (conexiones establecidas al 8080 de la API):
  ```bash
  docker exec vigia-backend-api sh -c "cat /proc/net/tcp /proc/net/tcp6 | awk '\$2 ~ /:1F90\$/ && \$4 == \"01\"' | wc -l"
  ```
- **Cookies:** cada VU conserva la suya, como un navegador (`noCookiesReset`). La API responde con `JSESSIONID`, y si k6 borrara las cookies, cada reconexión crearía una sesión HTTP nueva.

## Observador: `observar.py`

Solo biblioteca estándar de Python; usa el CLI de docker y las métricas que expone el stack en modo carga.

```bash
python loadtest/observar.py <corrida> [--minutos N]    # hasta Ctrl-C o N minutos
python loadtest/observar.py --resumen <corrida>
```

Escribe `loadtest/resultados/<corrida>/muestras.csv`:

| Cada | Columnas |
|---|---|
| 15 s | Hikari de la API y del worker (`activas`, `pendientes`, `timeouts`); conexiones a Postgres por estado; CPU (%) y memoria (MB) de cada contenedor, k6 incluido; para celo, salud y geo: duración de la última corrida terminada, segundos de la que está en curso y segundos entre las dos últimas largadas |
| 60 s | Filas de las últimas 7 h pendientes para celo y para salud, y cuánto tardó esa consulta (`backlog_ms`, con el `docker exec`) |

`--resumen` imprime el máximo y el p95 de cada columna. Si en la carpeta de la corrida está `k6.csv.gz` (k6 con `--out csv=resultados/<corrida>/k6.csv.gz`), agrega el webhook minuto a minuto: tasa, p99, máximo, errores y si cumple la vara. En una ráfaga, el último minuto que cumple es el límite.

## Orden de una corrida

1. **Semilla** (arriba), justo antes: el historial termina cuando se siembra, y un hueco largo entre el último punto y el primero de k6 se ve como un salto de posición.
2. **Reiniciar el worker:** `docker restart vigia-backend-worker`. Celo y salud corren cada 6 h y, como son `fixedRate`, la primera vez al arrancar. Así el ciclo que procesa las 6 h pendientes cae dentro de la corrida; si no, no correría nunca durante una prueba.
3. **Observador y k6**, con el mismo nombre de corrida:
   ```bash
   python loadtest/observar.py <corrida> &
   docker compose -f infra/docker-compose.yml -f infra/docker-compose.carga.yml run --rm k6 run \
     -e ESCENARIOS=sensores,clientes_streams,clientes_navegacion --out csv=resultados/<corrida>/k6.csv.gz carga.js
   ```

Celo, salud y geo corren de a uno (ver `BatchScheduler`), así que geo espera mientras dura el ciclo de celo y salud.

## Volver al stack normal

Antes, `limpiar.sql`. El `DataInitializer` de `dev` también borra todo al arrancar, pero salvo la telemetría lo hace de a una entidad, y con la semilla son decenas de miles.


```bash
docker compose -f infra/docker-compose.yml up -d
```

Vuelve el perfil `dev`: la API borra todo y siembra los datos de desarrollo al arrancar, y las métricas dejan de estar expuestas (404).
