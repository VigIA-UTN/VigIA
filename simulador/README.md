# VigIA - Simulador de IoT Ganadero

Script en Python que emula el comportamiento de cientos de collares IoT transmitiendo datos de telemetría en tiempo real hacia el backend. 

## Funcionalidades
- **Random Walk:** Simula el pastoreo normal de los animales en el campo.
- **Continuidad con la base:** al arrancar, cada animal consulta al backend (`GET /api/v1/ingesta/ultima-posicion/{deviceId}`) su última posición/batería real y arranca desde ahí, en vez de coordenadas hardcodeadas — evita el salto de posición que dispara alertas de celo falsas (ver el comentario de BUGFIX en `simulador.py`).
- **Anomalías forzadas**, para poder demostrar de punta a punta cada tipo de alerta sin depender del azar:
  - **Celo** (`VIGIA-ARG-001` por defecto): el historial de 24hs con patrón de alta actividad sostenida y errática ya lo persiste el backend al arrancar (`DataInitializer.java`, método `sembrarHistorialCelo`) — ni `simulador.py` ni `simulador_demo.py` lo inyectan. Ambos scripts solo mandan, en su loop en vivo, los puntos nuevos con ese mismo patrón para ese device_id; basta que lleguen unos pocos para que el próximo ciclo del job de celo (cada 60s) evalúe la ventana completa y dispare la alerta.
  - **Salud** (`VIGIA-ARG-002` por defecto): `simulador.py` sigue inyectando 8 días de historial (7+ de calibración "sana" + 1 día de cuadro de enfermedad con inmovilidad) al arrancar, y sigue mandando ese patrón de inmovilidad en el loop en vivo — esto no se movió al backend. Ver el comentario grande en `simulador.py` sobre por qué esto **requiere** que `fecha_alta` del animal ya tenga >= `vigia.salud.calibracion-dias` (7 por defecto) de antigüedad real — si no, el backend bloquea toda predicción de salud por RF03 sin importar la telemetría enviada. `VIGIA-ARG-002` viene backdateada en `DataInitializer.java` justamente para poder demostrar esto el mismo día. En `simulador_demo.py` esta inyección queda detrás del flag `INYECTAR_HISTORIAL_SALUD` (default `false`), por estar fuera del alcance de la demo del MVP.
- **Consumo de Batería:** Emula el desgaste progresivo de la batería de los collares.

## Configuración

Las variables de entorno principales son:
- `API_URL`: URL del webhook del backend (por defecto apunta al contenedor del `backend-worker`).
- `NUM_ANIMALES`: Cantidad de collares a simular (default 200).
- `INTERVALO_SEGUNDOS`: Frecuencia de envío de datos. Por defecto **300 segundos** (5 min, igual que el collar real / dataset de entrenamiento) en `simulador.py` — no bajarlo ahí, ver el comentario en el código sobre por qué eso hunde la confianza del modelo. Para una demo que sí necesita ir rápido, usar `simulador_demo.py` (ver más abajo), donde este valor pasa a ser puramente el ritmo real de envío, desacoplado de la escala de movimiento por paquete.
- `FORZAR_CELO_DEVICE_ID`: device_id que siempre se simula en celo (default `VIGIA-ARG-001`).
- `FORZAR_SALUD_DEVICE_ID`: device_id que siempre se simula con un desvío sanitario (default `VIGIA-ARG-002`).

## Ejecución

El simulador se ejecuta localmente usando un entorno virtual de Python:

```bash
# 1. Crear el entorno virtual
py -m venv .venv

# 2. Activar el entorno virtual (PowerShell)
.\.venv\Scripts\activate
# En Linux/Mac: source .venv/bin/activate

# 3. Instalar dependencias
pip install -r requirements.txt

# 4. Ejecutar el simulador
py simulador.py
```

## `simulador_demo.py` — variante para la demo del MVP (desplegada)

`simulador_demo.py` es la variante pensada para prender contra un backend ya
desplegado (no local) durante la demo del MVP. A diferencia de `simulador.py`:

- **`INTERVALO_SEGUNDOS` ya no afecta la calibración del modelo.** En esta
  variante es puramente el ritmo real de la demo (default **5 segundos**),
  desacoplado de la magnitud de movimiento por paquete, que queda fija a
  la escala de un tick de 5 minutos (`TICK_MODELADO_SEGUNDOS = 300`, no
  configurable) tal como espera el modelo entrenado. Se puede bajar sin
  miedo a hundir la confianza del modelo — ver el docstring del archivo
  para el detalle de por qué esto es seguro (el modelo no usa el timestamp
  como feature).
- Historial sanitario detrás de `INYECTAR_HISTORIAL_SALUD` (default
  `false`) — fuera de alcance de esta demo del MVP.

Se ejecuta igual que `simulador.py` (mismo entorno virtual y dependencias):

```bash
py simulador_demo.py
```

### Importante si el backend queda desplegado con anticipación

El historial de celo que siembra `DataInitializer` tiene timestamps fijados
en el momento del arranque del backend, y el job de celo siempre filtra
"últimas 24hs desde ahora". Si pasan más de ~24hs entre que se desplegó el
backend y se prende el simulador, ese historial ya salió de la ventana y la
alerta va a tardar mucho más en aparecer (el simulador tendría que juntar
de cero suficientes puntos en vivo). Para que la demo funcione como se
espera ("prendo el simulador y a los pocos minutos aparece la alerta"),
conviene reiniciar el backend (o re-disparar el `DataInitializer`) dentro de
esa ventana de 24hs antes de la demo.
