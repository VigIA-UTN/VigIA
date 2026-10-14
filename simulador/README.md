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

Las variables de entorno principales son:
- `API_URL`: URL del webhook del backend (por defecto apunta al contenedor del `backend-worker`).
- `NUM_ANIMALES`: Cantidad de caravanas a simular (default 200).
- `INTERVALO_SEGUNDOS`: Frecuencia de envío de datos. Por defecto **300 segundos** (5 min, igual que la caravana real / dataset de entrenamiento) en `simulador.py` y en `simulador_normal.py` — no bajarlo ahí sin necesidad, aunque en estos dos scripts la distancia por paquete queda fija a esa escala así que bajarlo no rompe la calibración del modelo, solo acelera el ritmo. En `simulador_celo.py` el default es **5 segundos**, pensado para activarse puntualmente en una demo.
- `FORZAR_CELO_DEVICE_ID`: device_id que siempre se simula en celo (default `VIGIA-ARG-001`).
- `FORZAR_SALUD_DEVICE_ID`: device_id que siempre se simula con un desvío sanitario (default `VIGIA-ARG-002`, solo usado por `simulador.py`).

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

## `simulador_normal.py` + `simulador_celo.py` — procesos separados para un backend ya desplegado

Estos dos scripts reemplazan a `simulador_demo.py` para el caso de un backend
desplegado (Railway u otro), con un objetivo puntual: poder dejar la manada
"de fondo" corriendo con cadencia real durante períodos largos (incluso
varios días), y activar el escenario de celo por separado, en el momento
exacto en que se lo quiere mostrar — sin tener que reiniciar ni reconfigurar
el proceso de fondo.

- **`simulador_normal.py`**: manda telemetría con comportamiento orgánico
  (sin nada forzado) para toda la manada, EXCEPTO `FORZAR_CELO_DEVICE_ID`
  (reservado para `simulador_celo.py`) y `VIGIA-ARG-003` (reservado para el
  indicador "sin datos recientes" del mapa, H0012 — sembrado por
  `DataInitializer` y nunca actualizado). Pensado para dejarse corriendo
  todo el tiempo, con cadencia real (`INTERVALO_SEGUNDOS=300` por defecto).
- **`simulador_celo.py`**: proceso aparte, simula ÚNICAMENTE la vaca
  forzada a celo. Se prende cuando se quiere mostrar la detección y se
  apaga (Ctrl+C) después — no hace falta dejarlo corriendo el resto del
  tiempo. Cadencia rápida por defecto (`INTERVALO_SEGUNDOS=5`) para que la
  alerta aparezca a los pocos minutos de activarlo. Requiere que
  `DataInitializer` ya haya sembrado el historial de celo de esa vaca (ver
  arriba) — no lo inyecta él mismo.

**Importante:** nunca corran `simulador_normal.py` y `simulador_celo.py` (o
`simulador.py`/`simulador_demo.py`) al mismo tiempo apuntando al mismo
device_id — dos procesos mandando telemetría para la misma vaca, cada uno
con su propio estado en memoria, hacen que se vea "teletransportándose"
entre dos trayectorias distintas.

```bash
# Terminal 1 — queda corriendo de fondo
py simulador_normal.py

# Terminal 2 — se prende solo cuando se va a mostrar la detección de celo
py simulador_celo.py
```

### Importante si el backend queda desplegado con anticipación

El historial de celo que siembra `DataInitializer` tiene timestamps fijados
en el momento del arranque del backend, y el job de celo siempre filtra
"últimas 24hs desde ahora". Si pasan más de ~24hs entre que se desplegó el
backend y se prende `simulador_celo.py`, ese historial ya salió de la
ventana y la alerta va a tardar mucho más en aparecer (el script tendría
que juntar de cero suficientes puntos en vivo). Para que la demo funcione
como se espera ("prendo el simulador y a los pocos minutos aparece la
alerta"), conviene reiniciar el backend (o re-disparar el
`DataInitializer`) dentro de esa ventana de 24hs antes de la demo.

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

## `simulador_demo.py` — variante anterior (un solo proceso, todavía funcional)

Antes de separar en `simulador_normal.py` + `simulador_celo.py`, existía
`simulador_demo.py`: un único proceso que hace las dos cosas a la vez
(manda a toda la manada, y fuerza el patrón de celo para
`FORZAR_CELO_DEVICE_ID` dentro del mismo loop). Sigue andando igual que
antes si se prefiere un solo proceso en vez de dos, pero no da control
sobre activar el escenario de celo en un momento distinto al de arrancar el
resto de la manada.

```bash
py simulador_demo.py
```
