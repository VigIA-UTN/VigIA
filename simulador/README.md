# VigIA - Simulador de IoT Ganadero

Script en Python que emula el comportamiento de cientos de caravanas IoT transmitiendo datos de telemetría en tiempo real hacia el backend. 

## Funcionalidades
- **Random Walk:** Simula el pastoreo normal de los animales en el campo.
- **Continuidad con la base:** al arrancar, cada animal consulta al backend (`GET /api/v1/ingesta/ultima-posicion/{deviceId}`) su última posición/batería real y arranca desde ahí, en vez de coordenadas hardcodeadas — evita el salto de posición que dispara alertas de celo falsas: el modelo de celo lee un salto entre dos puntos consecutivos como un desplazamiento anómalo.
  - **Arrancarlo después de `SEED DEMO completado`** (`docker logs vigia-backend-api`): `backend-api` da `healthy` y el worker atiende antes de que `DataInitializer` termine de sembrar la telemetría. Si una vaca todavía no tiene fila, `simulador.py` espera hasta `ESPERA_SEED_SEGUNDOS` (120 s) a que aparezca su posición sembrada. Pero si se reinicia `backend-api` con el simulador corriendo, o se lo prende antes de que el seed borre la telemetría vieja, continúa desde las posiciones viejas.
- **Anomalías forzadas**, para poder demostrar de punta a punta cada tipo de alerta sin depender del azar:
  - **Celo** (`VIGIA-ARG-001` por defecto): el historial de 24hs con patrón de alta actividad sostenida y errática ya lo persiste el backend al arrancar (`DataInitializer.java`, método `sembrarHistorialCelo`) — ningún script lo inyecta. Cada script que simula esta vaca solo manda, en su loop en vivo, los puntos nuevos con ese mismo patrón; basta que lleguen unos pocos para que el próximo ciclo del job de celo (cada 60s) evalúe la ventana completa y dispare la alerta.
  - **Salud y aislamiento** (`VIGIA-ARG-002` por defecto): igual que el celo, el historial vive en el backend. `DataInitializer` (método `sembrarHistorialMargarita`) siembra 8 días a 5 min: los primeros 7 son la calibración del perfil de referencia (ml-salud exige >= 2016 puntos), las últimas 24 hs traen la caída de ~40 % de actividad y el historial termina apartado del rodeo. `simulador_salud.py` (Acto 4) solo manda los puntos nuevos que despiertan a los jobs de salud y geo.
- **Consumo de Batería:** Emula el desgaste progresivo de la batería de las caravanas.

## Configuración

Las variables de entorno (ver `.env.example`) son:
- `API_URL`: URL del webhook del `backend-worker`.
- `RODEO_DEVICE_IDS`: el rodeo vivo de `simulador.py`, separado por comas (default `VIGIA-ARG-003..040`). Ver la sección de la demo.
- `EXTRA_DEVICE_IDS`: caravanas adicionales a simular, separadas por coma. En `simulador.py` el default son las caravanas libres `VIGIA-LIBRE-301..305` (Acto 1).
- `INTERVALO_SEGUNDOS`: segundos entre envíos. Si se define, pisa el default (5) de `simulador.py`, `simulador_celo.py` y `simulador_cohesion.py`. `simulador_fuga.py` y `simulador_salud.py` no lo leen (`--intervalo`). Para la demo **no** definirlo: con 300, el script de celo del Acto 2 tarda cinco minutos entre puntos. La distancia por paquete está fija a la escala de un tick de 5 min en todos los scripts, así que cambiarlo solo acelera o frena el ritmo, no rompe la calibración del modelo.
- `TTN_WEBHOOK_API_KEY`: tiene que coincidir con la de `infra/.env`.
- `FORZAR_CELO_DEVICE_ID`: vaca del escenario de celo (default `VIGIA-ARG-001`). `simulador.py` nunca le manda telemetría.
- `FORZAR_SALUD_DEVICE_ID`: vaca del Acto 4, desvío sanitario y aislamiento (default `VIGIA-ARG-002`). `simulador.py` nunca le manda telemetría.
- `FUGA_DEVICE_IDS`: vacas del Acto 3 (default `VIGIA-ARG-013,VIGIA-ARG-014`). `simulador.py` nunca les manda telemetría.

## Ejecución

Los scripts se ejecutan localmente usando un entorno virtual de Python:

```bash
# 1. Crear el entorno virtual
py -m venv .venv

# 2. Activar el entorno virtual (PowerShell)
.\.venv\Scripts\activate
# En Linux/Mac: source .venv/bin/activate

# 3. Instalar dependencias
pip install -r requirements.txt

# 4. Ejecutar el simulador que corresponda (ver más abajo cuál usar)
py simulador.py
```

## Demo E2E: `simulador.py` de fondo + un script por acto

El backend corre con el perfil `dev`: `DataInitializer` limpia la base y siembra
los datos de la demo en cada arranque. Antes de la demo, reiniciar `backend-api`
(`docker compose restart backend-api` desde `infra/`) y esperar en su log la línea
`[DataInitializer] SEED DEMO completado.` antes de prender `simulador.py`: el log
`Started BackendApiApplication` sale antes de que termine el seed.

Ensayo con ese cronograma (reseed, `simulador.py`, acto 2 al minuto 2, acto 3 a
continuación, acto 4 al minuto 10): cada alerta salió entre 25 y 26 s después de
prender su script, sin alertas de más ni ERROR en el worker. El acto 4 tiene que
caer dentro de los ~25 min posteriores al reseed (ver la sección del acto 4).

- **`simulador.py`**: la manada de fondo. Manda pastoreo normal, sin
  nada forzado, y queda corriendo toda la demo. Mueve el rodeo vivo
  (`RODEO_DEVICE_IDS`, por defecto todo el Lote Principal, `VIGIA-ARG-003..040`)
  y lo mantiene junto y en el lugar: la vaca que se aleja
  más de 35 m del centro donde arrancó el rodeo vuelve hacia él. Hace falta porque ml-geo arma el grupo
  con los animales a <= 100 m entre sí y solo mira los que transmitieron en los
  últimos 30 min: si el rodeo vivo se dispersara, marcaría aislados a todos.
  `DataInitializer` deja las últimas posiciones de todas las vacas con datos
  recientes en un círculo de ~40 m, así que al arrancar tampoco hay
  aislamientos. Nunca manda telemetría para `FORZAR_CELO_DEVICE_ID`,
  `FUGA_DEVICE_IDS` ni `FORZAR_SALUD_DEVICE_ID`, que mueven los scripts de cada
  acto. Para el Acto 1 (Paso 1.6) manda también las caravanas libres
  `VIGIA-LIBRE-301..305`, juntas en un corral ~275 m al oeste del Potrero
  Principal (afuera, para dibujar en vivo el perímetro del Lote Cuarentena
  alrededor de ellas): el worker las ignora ("Sin
  animal asociado") hasta que Pedro vincula una desde la UI, y desde el
  paquete siguiente se guarda su telemetría y el animal aparece en el mapa,
  sin reiniciar nada.
- **`simulador_celo.py`** (Acto 2): simula solo la vaca #104 en celo. Se
  prende en el Paso 2.1 y se apaga con Ctrl+C al terminar el acto. Cada
  corrida abre una sesión nueva, así que se puede repetir sin reiniciar el
  backend; si el worker ignora un paquete, lo avisa en la consola con el motivo.
  Ojo: si #104 ya tuvo una alerta de celo en las últimas 24 hs, el job no genera
  otra hasta reiniciar el backend.
- **`simulador_fuga.py`** (Acto 3) y **`simulador_salud.py`** (Acto 4): ver
  las secciones siguientes.

```bash
# Terminal 1 — queda corriendo toda la demo
py simulador.py

# Terminal 2 — un script por acto, en el momento en que se lo muestra
py simulador_celo.py
py simulador_fuga.py
py simulador_salud.py
```

**Importante:** nunca corran dos scripts que manden telemetría para el mismo
device_id (por ejemplo `simulador_cohesion.py` y `simulador.py` juntos): cada
proceso tiene su propio estado en memoria y la vaca se ve
"teletransportándose" entre dos trayectorias, lo que además puede disparar
alertas de celo falsas.

Para dejarlo corriendo varios días contra un backend desplegado, alcanza con
`INTERVALO_SEGUNDOS=300` (cadencia de una caravana real).

### Importante si el backend se levanta con anticipación

El historial de celo que siembra `DataInitializer` tiene timestamps fijados
en el momento del arranque del backend, y el job de celo siempre filtra
"últimas 24hs desde ahora". Si pasan más de ~24hs entre que se levantó el
backend y se prende `simulador_celo.py`, ese historial ya salió de la
ventana y la alerta va a tardar mucho más en aparecer. Conviene reiniciar el
backend dentro de esa ventana de 24hs antes de la demo.

## `simulador_fuga.py` — Acto 3: fuga por el alambrado norte

Simula que cede el alambrado norte del "Potrero Principal" y dos vacas
(`FUGA_DEVICE_IDS`, por defecto `VIGIA-ARG-013` Aurora y `VIGIA-ARG-014`
Canela) se escapan juntas. Arranca desde la última posición real de cada una,
caminan hasta el mismo hueco del alambrado y lo cruzan en el mismo paquete
(~15 paquetes en total). El worker verifica el perímetro en el mismo request
de ingesta, así que las dos alertas `FUERA_DE_PERIMETRO` salen con segundos de
diferencia y el panel de alertas las agrupa. Quedan pastando ~145 m afuera
(la tolerancia del perímetro es 50 m) hasta que el presentador presiona
Enter: ahí vuelven a entrar y el script termina solo.

```bash
py simulador_fuga.py              # cadencia de 3 s
py simulador_fuga.py --intervalo 2
```

- Usa su propia cadencia (3 s, o `--intervalo`): **no** lee
  `INTERVALO_SEGUNDOS`.
- moveState se mantiene en 0-2 a propósito. Validado offline contra el modelo
  de `ml-celo`: caminar cientos de metros con moveState 4-7 dispara un celo
  falso en las vacas que se escapan (P > 0.943); con 0-2 queda en ~0.55-0.66.
- `simulador.py` excluye `FUGA_DEVICE_IDS`, así que nunca hay dos
  procesos moviendo la misma vaca.
- El worker no vuelve a alertar la salida de una vaca con una alerta de
  perímetro ya atendida en las últimas 6 hs (`vigia.geofence.cooldown-hours`).
  Si se ensaya, reiniciar el backend antes de la demo para que
  `DataInitializer` resetee la base.
- Si las vacas mandan posiciones fuera del rodeo durante más de
  `VIGIA_GEO_MINUTOS_FUERA_MINIMO` (15 min) seguidos, además aparece una
  alerta de `AISLAMIENTO_SOCIAL` para las dos, y la #208 del Acto 4 se suma a
  esa alerta en vez de abrir una propia. Presionar Enter antes de eso.

## `simulador_salud.py` — Acto 4: desvío sanitario y aislamiento de la #208

El "trigger_salud_aislamiento" del guion. La vaca #208 Margarita
(`FORZAR_SALUD_DEVICE_ID`, `VIGIA-ARG-002`) viene incubando una infección: hoy
se movió ~40 % menos que su promedio y se quedó atrás, apartada del rodeo.
Con el backend reiniciado antes de la demo y `simulador.py` corriendo:

```bash
py simulador_salud.py
```

La caravana de la #208 vuelve a transmitir, quieta a ~180 m del rodeo, y en
el próximo ciclo de cada job (60 s con la config de demo) aparecen sus dos
alertas: `SALUD` (severidad MEDIA) y `AISLAMIENTO_SOCIAL`. El script sigue
mandando su posición hasta Ctrl+C.

Igual que el celo de la #104, la historia vive en el seed y el script solo
manda los puntos PENDIENTES que despiertan a cada job (los detalles, en el
docstring del script):

| | Salud (`SaludDetectorStrategyImpl`, ml-salud) | Aislamiento (`GeoDetectorStrategyImpl`, ml-geo) |
|---|---|---|
| Job | `VIGIA_BATCH_SALUD_RATE` (60 s) | `VIGIA_BATCH_GEO_RATE` (900000 en el compose; usar 60000 en `infra/.env` para la demo) |
| Ventana | Últimos 288 puntos de las últimas 24 hs, contra el perfil de 7 días de calibración (>= 2016 puntos a 5 min, desde el primer dato de la caravana) | `VIGIA_GEO_VENTANA_HORAS` (24 en el compose); solo animales con posición de los últimos 30 min |
| Umbral | Clasificador de ml-salud (severidad > 0) | Fuera del grupo (animales a <= 100 m entre sí, umbral del establecimiento) durante `VIGIA_GEO_MINUTOS_FUERA_MINIMO` (15) min seguidos, en intervalos de 5 min |
| Qué pone el seed | 8 días a 5 min; las últimas 24 hs con -40 % (moveState 1-3 en vez de 2-5) | La #208 termina apartada (~180 m al oeste del rodeo) y 35 min antes del arranque: ml-geo no la ve hasta el Acto 4, pero su última hora ya es "fuera del grupo" |
| Qué pone el script | Los puntos PENDIENTES, quieta (sin caminar: alejarse dentro de la ventana le suma desplazamiento y ml-salud deja de ver la caída) | Completa con su hora real los intervalos de 5 min hasta 5 min antes del arranque, donde el seed tiene al rodeo, y sigue en vivo |

- Con `VIGIA_GEO_VENTANA_HORAS=1` el aislamiento sale de entrada si el Acto 4
  cae dentro de ~25 min del reinicio del backend; con 24, siempre.
- El resto del rodeo (incluidas las vacas de la fuga) tiene 8 días a 5 min con
  el mismo pastoreo que `simulador.py` (`sembrarHistorialRodeo`), y el worker
  les arma el perfil de salud en el primer minuto después del seed: el
  dashboard las muestra monitoreadas. Validado offline contra ml-salud: 0
  alertas falsas en 1200 ventanas (con el pastoreo anterior del seed daba
  ~1 %), y en Docker, 1074 evaluaciones en 31 min con solo la alerta de la
  #208. La #104 tiene 6 días y queda en calibración: con perfil, sus 24 hs de
  celo darían alerta de salud en el Acto 2.
- moveState 0-2 y la vaca quieta. Validado offline contra `ml-celo`
  (umbral 0.943): P(celo) ~0.12. El rodeo vivo con la vuelta al centro queda
  en <= 0.23.
- El worker no vuelve a alertar salud de una vaca con una alerta de salud en
  las últimas 24 hs: si se ensaya, reiniciar el backend antes de la demo.
- Usa su propia cadencia (5 s, o `--intervalo`): **no** lee
  `INTERVALO_SEGUNDOS`.

## `simulador_cohesion.py` — escenarios de cohesión y aislamiento

Simula las 5 caravanas `VIGIA-ARG-001..005` en tres fases. Primero convergen desde posiciones fijas que corresponden a un `DataInitializer` anterior: con el seed actual las vacas "saltan" a esas posiciones en el primer paquete, así que no usarlo durante la demo (para el Acto 4 está `simulador_salud.py`). Después quedan todas juntas `--ciclos-juntos` ciclos. Al final, 001 a 003 se quedan en el rodeo (a menos de 25 m del centro) y 004 y 005 se alejan hasta `--distancia-aislado` (175 m del centro por defecto) y pastorean ahí. Si vuelven a entrar al radio, retoman hacia afuera.

Con `--modo aislados` (default), 004 y 005 se van en direcciones opuestas. Con `--modo subgrupo`, se van juntos, a ~10 m entre sí.

La distancia por defecto es mayor que el umbral (100 m) más el radio del rodeo (25 m), con margen. Así el aislamiento es claro y la alerta no va y viene entre ciclos. Si el establecimiento usa otro umbral, pasarlo con `--umbral` (solo afecta al log) y ajustar `--distancia-aislado` a más de `umbral + 50`.

Resultado esperado tras correr la fase 3 y que corra el job de geo del worker (`geoJob`, umbral de 100 m; cada `VIGIA_BATCH_GEO_RATE` ms, 15 min por defecto):

| Escenario | 001-003 | 004 y 005 |
|---|---|---|
| Fases 1-2 (rodeo cohesionado) | `alerta_aislamiento: false`, `tiempo_fuera_grupo_s: 0` | igual que 001-003 |
| `--modo aislados` (fase 3) | sin alerta, tiempo 0 | alerta `AISLAMIENTO_SOCIAL`, tiempo > 0 |
| `--modo subgrupo` (fase 3) | sin alerta, tiempo 0 | alerta `AISLAMIENTO_SOCIAL` para los dos, tiempo > 0, aunque estén juntos entre sí |

El tiempo y el índice se ven en `registros_cohesion`. Las alertas, en `alertas` con disparador `AISLAMIENTO_SOCIAL`.

```bash
py simulador_cohesion.py --modo aislados
py simulador_cohesion.py --modo subgrupo
```

Igual que con el resto, no correrlo al mismo tiempo que otro simulador que mande telemetría de los mismos device_id.
