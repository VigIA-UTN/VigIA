# VigIA - Simulador de IoT Ganadero

Script en Python que emula el comportamiento de cientos de caravanas IoT transmitiendo datos de telemetría en tiempo real hacia el backend. 

## Funcionalidades
- **Random Walk:** Simula el pastoreo normal de los animales en el campo.
- **Continuidad con la base:** al arrancar, cada animal consulta al backend (`GET /api/v1/ingesta/ultima-posicion/{deviceId}`) su última posición/batería real y arranca desde ahí, en vez de coordenadas hardcodeadas — evita el salto de posición que dispara alertas de celo falsas (ver el comentario de BUGFIX en `simulador.py`).
- **Anomalías forzadas**, para poder demostrar de punta a punta cada tipo de alerta sin depender del azar:
  - **Celo** (`VIGIA-ARG-001` por defecto): el historial de 24hs con patrón de alta actividad sostenida y errática ya lo persiste el backend al arrancar (`DataInitializer.java`, método `sembrarHistorialCelo`) — ningún script lo inyecta. Cada script que simula esta vaca solo manda, en su loop en vivo, los puntos nuevos con ese mismo patrón; basta que lleguen unos pocos para que el próximo ciclo del job de celo (cada 60s) evalúe la ventana completa y dispare la alerta.
  - **Salud** (`VIGIA-ARG-002` por defecto): `simulador.py` sigue inyectando 8 días de historial (7+ de calibración "sana" + 1 día de cuadro de enfermedad con inmovilidad) al arrancar, y sigue mandando ese patrón de inmovilidad en el loop en vivo — esto todavía no se movió al backend. Ver el comentario grande en `simulador.py` sobre por qué esto **requiere** que `fecha_alta` del animal ya tenga >= `vigia.salud.calibracion-dias` (7 por defecto) de antigüedad real — si no, el backend bloquea toda predicción de salud por RF03 sin importar la telemetría enviada. `VIGIA-ARG-002` viene backdateada en `DataInitializer.java` justamente para poder demostrar esto el mismo día. Todavía no hay un `simulador_salud.py` separado (queda pendiente para cuando se muestre ese flujo).
- **Consumo de Batería:** Emula el desgaste progresivo de la batería de las caravanas.

## Configuración

Las variables de entorno (ver `.env.example`) son:
- `API_URL`: URL del webhook del `backend-worker`.
- `NUM_ANIMALES`: cantidad de caravanas correlativas (`VIGIA-ARG-001..NNN`) que simulan `simulador.py` y `simulador_demo.py` (default 5).
- `EXTRA_DEVICE_IDS`: caravanas adicionales a simular, separadas por coma. En `simulador_demo.py` el default son las caravanas libres `VIGIA-LIBRE-301..305` (Acto 1).
- `INTERVALO_SEGUNDOS`: segundos entre envíos. Si se define, pisa el default de `simulador.py` (300), `simulador_demo.py`, `simulador_celo.py` y `simulador_cohesion.py` (5). Para la demo **no** definirlo: con 300, el script de celo del Acto 2 tarda cinco minutos entre puntos. La distancia por paquete está fija a la escala de un tick de 5 min en todos los scripts, así que cambiarlo solo acelera o frena el ritmo, no rompe la calibración del modelo.
- `TTN_WEBHOOK_API_KEY`: tiene que coincidir con la de `infra/.env`.
- `FORZAR_CELO_DEVICE_ID`: vaca del escenario de celo (default `VIGIA-ARG-001`). `simulador_demo.py` nunca le manda telemetría.
- `FORZAR_SALUD_DEVICE_ID`: vaca con desvío sanitario forzado en `simulador.py` (default `VIGIA-ARG-002`).
- `FUGA_DEVICE_IDS`: vacas del Acto 3 (default `VIGIA-ARG-013,VIGIA-ARG-014`). `simulador_demo.py` nunca les manda telemetría.

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

## Demo E2E: `simulador_demo.py` de fondo + un script por acto

El backend corre con el perfil `dev`: `DataInitializer` limpia la base y siembra
los datos de la demo en cada arranque.

- **`simulador_demo.py`**: la manada de fondo. Manda pastoreo normal, sin
  nada forzado, y queda corriendo toda la demo. Nunca manda telemetría para
  `FORZAR_CELO_DEVICE_ID`, `FUGA_DEVICE_IDS` ni `VIGIA-ARG-003` (el seed le deja
  una única transmisión vieja para el indicador "sin datos recientes" del
  mapa, H0012). Para el Acto 1 (Paso 1.6) manda también las caravanas libres
  `VIGIA-LIBRE-301..305`: el worker las ignora ("Sin animal asociado") hasta
  que Pedro vincula una desde la UI, y desde el paquete siguiente se guarda su
  telemetría y el animal aparece en el mapa, sin reiniciar nada.
- **`simulador_celo.py`** (Acto 2): simula solo la vaca #104 en celo. Se
  prende en el Paso 2.1 y se apaga con Ctrl+C al terminar el acto. Cada
  corrida abre una sesión nueva, así que se puede repetir sin reiniciar el
  backend; si el worker ignora un paquete, lo avisa en la consola con el motivo.
  Ojo: si #104 ya tuvo una alerta de celo en las últimas 24 hs, el job no genera
  otra hasta reiniciar el backend.
- **`simulador_fuga.py`** (Acto 3): ver la sección siguiente.

```bash
# Terminal 1 — queda corriendo toda la demo
py simulador_demo.py

# Terminal 2 — un script por acto, en el momento en que se lo muestra
py simulador_celo.py
py simulador_fuga.py
```

**Importante:** nunca corran dos scripts que manden telemetría para el mismo
device_id (por ejemplo `simulador.py` y `simulador_demo.py` juntos): cada
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

Simula que cede el alambrado norte del "Potrero Principal Demo" y dos vacas
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
- `simulador_demo.py` excluye `FUGA_DEVICE_IDS`, así que se puede subir
  `NUM_ANIMALES` sin que dos procesos muevan la misma vaca.
- El worker no vuelve a alertar la salida de una vaca con una alerta de
  perímetro ya atendida en las últimas 6 hs (`vigia.geofence.cooldown-hours`).
  Si se ensaya, reiniciar el backend antes de la demo para que
  `DataInitializer` resetee la base.
- Si las vacas quedan afuera más que el período del job de geo
  (`VIGIA_BATCH_GEO_RATE`, 15 min por defecto), además puede aparecer una
  alerta de `AISLAMIENTO_SOCIAL` para las dos.

## `simulador_cohesion.py` — escenarios de cohesión y aislamiento

Simula las 5 caravanas `VIGIA-ARG-001..005` en tres fases. Primero convergen desde las posiciones de `DataInitializer`. Después quedan todas juntas `--ciclos-juntos` ciclos. Al final, 001 a 003 se quedan en el rodeo (a menos de 25 m del centro) y 004 y 005 se alejan hasta `--distancia-aislado` (175 m del centro por defecto) y pastorean ahí. Si vuelven a entrar al radio, retoman hacia afuera.

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
