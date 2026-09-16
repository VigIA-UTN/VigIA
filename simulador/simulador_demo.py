"""
simulador_demo.py — variante de simulador.py pensada para la DEMO del MVP
(Sprints 1-4: Acceso y Autenticación, Gestión del Rodeo, Detección
Reproductiva/Alertas de celo, Dashboard y Mapa).

Historial de este archivo (para quien lea esto en el futuro): hasta la
versión anterior, este script inyectaba al arrancar 288 puntos de historial
de celo (24hs) para FORZAR_CELO_DEVICE_ID antes de empezar el loop en vivo.
Eso funcionaba, pero tenía dos problemas para un despliegue de demo real
(no local): (1) esos ~288 requests son varios minutos de arranque tirados a
la basura, en un escenario donde el simulador se prende y apaga varias veces
antes/durante la demo; y (2) la posición inicial de cada vaca estaba
hardcodeada en SEED_POSICIONES_DB, copiada a mano de lo que insertaba
DataInitializer.java — si el simulador arrancaba en un momento distinto al
del último deploy del backend, esa posición podía estar desactualizada
(ver el BUGFIX que sigue documentado más abajo: un salto de posición
desconectado de la última fila real dispara una alerta de celo falsa).

Qué cambió (ver DataInitializer.java, método sembrarHistorialCelo):

1. El historial de celo (288 puntos / 24hs, patrón de alta actividad
   sostenida y errática) ya NO lo inyecta este script: lo persiste el
   backend al arrancar, vía DataInitializer. Este simulador arranca
   directo en el loop en vivo, sin ningún paso previo de inyección.

2. Como esa telemetría sembrada queda marcada COMPLETADA (no PENDIENTE), no
   dispara el job de celo por sí sola. El job (corre cada 60s, ver
   BatchScheduler) recién procesa la ventana completa —ya con forma de
   celo de punta a punta— cuando llega la primera telemetría PENDIENTE, es
   decir, apenas este simulador manda sus primeros puntos en vivo. Por eso
   la alerta debería aparecer en el dashboard a los pocos minutos de
   prender el simulador, sin depender de cuándo se desplegó el backend.

3. La posición/batería inicial de cada vaca ya NO está hardcodeada: se pide
   al backend (GET /api/v1/ingesta/ultima-posicion/{deviceId}) al arrancar,
   así el simulador siempre continúa la última fila real que exista en la
   base en ESE momento — sin importar si el backend lleva 5 minutos o 5
   horas arriba. Si el backend no tiene todavía ninguna fila para un
   device_id (o no se lo puede consultar), se cae a una posición random
   cerca del establecimiento, igual que antes.

4. NO inyecta historial sanitario por defecto (esto no cambió respecto a la
   versión anterior). La vigilancia sanitaria (H0017 en adelante) es
   Release 2 (Sprint 5+) y no forma parte de esta demo del MVP, así que ese
   flujo sigue detrás del flag INYECTAR_HISTORIAL_SALUD (default "false").
   Si no se inyecta, el animal FORZAR_SALUD_DEVICE_ID tampoco se fuerza a
   comportamiento "enfermo" en el loop en vivo (no tiene sentido sin un
   detector sanitario mirando esos datos en el alcance de esta demo).

5. Envío en vivo MÁS RÁPIDO que el sistema real, sin romper la calibración
   del modelo de celo. Esto es la parte más delicada del archivo, así que
   va con detalle:

   - El modelo de celo (ml-celo/app/main.py, función predict()) NO usa el
     timestamp como feature: solo usa moveState y la distancia geométrica
     entre puntos consecutivos, SIN normalizar por el tiempo real
     transcurrido. Es decir, el timestamp que se manda es puramente
     informativo (para el filtro "últimas 24hs" del job y para el
     indicador de frescura del mapa) — no afecta la predicción.
   - Lo que sí afecta la predicción es la MAGNITUD del desplazamiento por
     paquete. El modelo se entrenó asumiendo que cada punto consecutivo
     representa un tick de 5 minutos (300s) de movimiento real (ver
     generate_dataset.py). La versión vieja de este archivo calculaba esa
     magnitud como SPEED_BASE_DEG_POR_SEG * INTERVALO_SEGUNDOS, es decir,
     la escalaba según cada cuánto se mandaba en la vida real — por eso
     bajar INTERVALO_SEGUNDOS para "verlo más rápido" hundía la confianza
     del modelo (medido: ~0.02 con 5s vs ~0.8-0.97 con 300s).
   - Este archivo separa esas dos cosas:
       * TICK_MODELADO_SEGUNDOS = 300 (fijo, NO configurable): la escala de
         movimiento que "ve" el modelo por paquete. No tocar.
       * INTERVALO_SEGUNDOS (configurable, default 5s): cada cuánto tiempo
         REAL se manda un ciclo completo de paquetes. Sirve solo para el
         ritmo de la demo (que se vea la caravana moverse en el mapa, que
         la vaca de celo junte paquetes rápido) y no participa del cálculo
         de distancia.
   - El timestamp que se manda es siempre datetime.now() (hora real de
     envío) — no uno fabricado — justamente porque el modelo no lo usa
     para nada más que el filtro de ventana, y mandar la hora real evita
     que un animal quede con "última posición" en el futuro si la demo se
     deja corriendo un rato (algo que sí podía pasar fabricando timestamps
     espaciados 300s y enviándolos cada pocos segundos reales).

Lo que NO se toca porque está correctamente calibrado contra el modelo de
celo (ver los comentarios largos en simulador.py, que se mantienen acá sin
modificar la lógica): la escala de velocidad/turno del random walk, y el
random walk correlacionado con heading persistente.
"""

import os
import time
import math
import random
import requests
from datetime import datetime, timezone, timedelta
# pyrefly: ignore [missing-import]
from dotenv import load_dotenv

load_dotenv()

# Configuración
API_URL = os.getenv("API_URL", "http://localhost:8081/api/v1/ingesta/uplink")
NUM_ANIMALES = int(os.getenv("NUM_ANIMALES", 5))

# Cada cuánto tiempo REAL se manda un ciclo completo de paquetes durante la
# demo. Es puramente el ritmo de la demo: NO participa del cálculo de
# distancia por paquete (ver TICK_MODELADO_SEGUNDOS y el punto 5 del
# docstring). Bajarlo hace que la caravana se vea moverse más rápido en el
# mapa y que la vaca forzada a celo junte paquetes nuevos más rápido, sin
# ningún impacto en la confianza del modelo.
INTERVALO_SEGUNDOS = int(os.getenv("INTERVALO_SEGUNDOS", 5))

# Escala de movimiento que "ve" el modelo de celo por paquete: un tick de 5
# minutos (300s), igual que ml-training/celo/generate_dataset.py. FIJO, no
# confundir con INTERVALO_SEGUNDOS de arriba — ver punto 5 del docstring
# para el porqué. No bajar/subir este valor.
TICK_MODELADO_SEGUNDOS = 300

# Flag propio de esta variante "demo": por defecto NO se inyecta ni se fuerza
# comportamiento sanitario, porque la vigilancia sanitaria no está en el
# alcance de la demo del MVP (Release 2, Sprint 5+). Poner en "true" solo si
# en algún momento quieren mostrar también ese flujo.
INYECTAR_HISTORIAL_SALUD = os.getenv("INYECTAR_HISTORIAL_SALUD", "false").lower() == "true"

# Coordenadas iniciales: Establecimiento de Prueba (del DataInitializer),
# usadas solo como fallback si el backend todavía no tiene ninguna fila para
# un device_id (ver obtener_posicion_inicial()).
LAT_INICIAL = -34.943317
LON_INICIAL = -59.225728

# ---------------------------------------------------------------------------
# Escala física de movimiento, alineada con generate_dataset.py:
#   SPEED_BASE = 0.000018 grados POR TICK DE 5 MINUTOS (300s) en pastoreo normal.
# Esta magnitud es fija por paquete (TICK_MODELADO_SEGUNDOS), no se re-escala
# según INTERVALO_SEGUNDOS — ver punto 5 del docstring.
# ---------------------------------------------------------------------------
SPEED_BASE_DEG_POR_TICK_5MIN = 0.000018

# En celo, generate_dataset.py multiplica la velocidad por 1.0 + intensidad*(2 a 3).
SPEED_MULT_CELO_MIN = 2.5
SPEED_MULT_CELO_MAX = 4.0

TURN_STD_BASE = 0.35   # rad por tick, pastoreo normal (igual que generate_dataset.py)
TURN_STD_CELO = 1.1    # trayectoria errática durante celo

# Patrón "enferma" (severidad 3, tipo_alto=1 — inmovilidad), ver simulador.py.
# Se mantiene definido acá por si INYECTAR_HISTORIAL_SALUD=true.
SPEED_MULT_SALUD_ENFERMO = 0.1
TURN_STD_SALUD_ENFERMO = 0.15
PROB_REPOSO_ENFERMO = 0.95

# moveState real del collar: 0-9
MOVESTATE_REPOSO = (0, 1)
MOVESTATE_NORMAL = (2, 6)
MOVESTATE_ALTA_ACTIVIDAD = (7, 9)

FORZAR_CELO_DEVICE_ID = os.getenv("FORZAR_CELO_DEVICE_ID", "VIGIA-ARG-001")
FORZAR_SALUD_DEVICE_ID = os.getenv("FORZAR_SALUD_DEVICE_ID", "VIGIA-ARG-002")

# Igual que en simulador.py: este animal se deja fuera del loop en vivo a
# propósito. DataInitializer le pone una única transmisión vieja (> 15 min)
# para mostrar el indicador "sin datos recientes" del mapa (H0012). Si el
# simulador le manda telemetría nueva, ese detalle de la demo se pierde.
EXCLUIR_DEL_LOOP_EN_VIVO = {"VIGIA-ARG-003"}

# Timeout corto para la consulta de posición inicial: si el backend no
# responde rápido, mejor arrancar con el fallback random que colgar el
# arranque de la demo.
TIMEOUT_ULTIMA_POSICION_SEGUNDOS = 3


def obtener_posicion_inicial(device_id):
    """
    Continúa la última posición/batería real que el backend ya tiene para
    este device_id (sembrada por DataInitializer o dejada por una corrida
    anterior del simulador), en vez de arrancar en un punto desconectado.

    BUGFIX heredado de simulador.py: si el primer paquete en vivo "teletransporta"
    al animal respecto a la última fila real que ya tiene la base, el job de
    celo lee ese salto como un desplazamiento anómalo entre puntos consecutivos
    de la ventana de 24hs y puede disparar una alerta de celo falsa en
    cualquier animal — no necesariamente en el que está forzado. Por eso este
    simulador ya no hardcodea posiciones: las pide.
    """
    base_url = API_URL.rsplit("/api/v1/", 1)[0]
    url = f"{base_url}/api/v1/ingesta/ultima-posicion/{device_id}"
    try:
        resp = requests.get(url, timeout=TIMEOUT_ULTIMA_POSICION_SEGUNDOS)
        if resp.status_code == 200:
            data = resp.json()
            return data["latitud"], data["longitud"], data.get("bateria") or 100.0
    except Exception as e:
        print(f"AVISO: no se pudo consultar la última posición de {device_id} ({e}).")

    print(f"AVISO: {device_id} no tiene posición previa conocida en el backend; "
          f"arrancando en una posición random cerca del establecimiento.")
    lat_ini = LAT_INICIAL + random.uniform(-0.005, 0.005)
    lon_ini = LON_INICIAL + random.uniform(-0.005, 0.005)
    bateria_ini = random.uniform(80.0, 100.0)
    return lat_ini, lon_ini, bateria_ini


# Estado inicial de los animales: se resuelve contra el backend, no
# hardcodeado (ver obtener_posicion_inicial()).
animales = {}
for i in range(1, NUM_ANIMALES + 1):
    device_id = f"VIGIA-ARG-{i:03d}"
    lat_ini, lon_ini, bateria_ini = obtener_posicion_inicial(device_id)
    animales[device_id] = {
        "lat": lat_ini,
        "lon": lon_ini,
        "bateria": bateria_ini,
        "heading": random.uniform(0, 2 * math.pi),
    }


def generar_payload(device_id, estado):
    en_celo = device_id == FORZAR_CELO_DEVICE_ID
    # Solo se fuerza comportamiento "enfermo" en vivo si efectivamente se
    # inyectó el historial de calibración sanitaria; si no, no tiene sentido
    # (ver punto 4 del docstring) y el animal se comporta como cualquier otro.
    enferma = INYECTAR_HISTORIAL_SALUD and device_id == FORZAR_SALUD_DEVICE_ID

    estado["bateria"] -= random.uniform(0.01, 0.05)
    if estado["bateria"] < 0:
        estado["bateria"] = 100.0

    speed_mult = 1.0
    turn_std = TURN_STD_BASE

    if en_celo:
        move_state = random.randint(*MOVESTATE_ALTA_ACTIVIDAD)
        speed_mult = random.uniform(SPEED_MULT_CELO_MIN, SPEED_MULT_CELO_MAX)
        turn_std = TURN_STD_CELO
    elif enferma:
        if random.random() < PROB_REPOSO_ENFERMO:
            move_state = random.randint(*MOVESTATE_REPOSO)
        else:
            move_state = random.randint(*MOVESTATE_NORMAL)
        speed_mult = SPEED_MULT_SALUD_ENFERMO
        turn_std = TURN_STD_SALUD_ENFERMO
    else:
        r = random.random()
        if r < 0.35:
            move_state = random.randint(*MOVESTATE_REPOSO)
        elif r < 0.95:
            move_state = random.randint(*MOVESTATE_NORMAL)
        else:
            move_state = random.randint(*MOVESTATE_ALTA_ACTIVIDAD)

    # Magnitud de paso fija a la escala de un tick de 5 min (ver punto 5 del
    # docstring): no se re-escala según INTERVALO_SEGUNDOS.
    estado["heading"] += random.gauss(0, 1) * turn_std
    speed = SPEED_BASE_DEG_POR_TICK_5MIN * speed_mult
    estado["lat"] += speed * math.cos(estado["heading"])
    estado["lon"] += speed * math.sin(estado["heading"])

    # Generamos el formato TTN v3 Webhook
    return {
        "end_device_ids": {
            "dev_eui": device_id
        },
        "uplink_message": {
            "received_at": datetime.now(timezone.utc).isoformat(),
            "decoded_payload": {
                "type": "GNSSPosition",
                "latitude": round(estado["lat"], 6),
                "longitude": round(estado["lon"], 6),
                "moveState": move_state,
                "battery": f"{round(estado['bateria'], 1)}%"
            }
        }
    }


def main():
    print(f"Iniciando simulador DEMO para {NUM_ANIMALES} animales en el Establecimiento de Prueba.")
    print(f"Target API: {API_URL}")
    print(f"Frecuencia de envío (real, ritmo de demo): {INTERVALO_SEGUNDOS} segundos.")
    print(f"Escala de movimiento por paquete (fija, no confundir con la frecuencia de envío): "
          f"tick de {TICK_MODELADO_SEGUNDOS} segundos.")
    print(f"Escenario forzado de celo: {FORZAR_CELO_DEVICE_ID} "
          f"(historial de 24hs ya persistido por DataInitializer; este script solo manda "
          f"los puntos nuevos que hacen falta para que el job de celo evalúe la ventana).")
    if INYECTAR_HISTORIAL_SALUD:
        print(f"Escenario forzado de desvío sanitario: {FORZAR_SALUD_DEVICE_ID} (INYECTAR_HISTORIAL_SALUD=true)")
    else:
        print("Historial sanitario: OMITIDO (fuera de alcance del MVP para esta demo; "
              "poner INYECTAR_HISTORIAL_SALUD=true para incluirlo).")
    if EXCLUIR_DEL_LOOP_EN_VIVO:
        print(f"Excluidos del loop en vivo (quedan con su telemetría sembrada, sin actualizar): "
              f"{sorted(EXCLUIR_DEL_LOOP_EN_VIVO)}")

    print(f"\n[{datetime.now().isoformat()}] Sin inyección de historial al arrancar. "
          f"El job batch de celo corre cada 60s: apenas este loop mande los primeros puntos "
          f"de {FORZAR_CELO_DEVICE_ID}, la alerta debería aparecer en el dashboard dentro del "
          f"próximo minuto o dos. Arrancando loop en vivo...\n")

    while True:
        inicio_ciclo = time.time()
        exitos = 0
        errores = 0

        print(f"--- Iniciando ciclo de envío ({datetime.now().isoformat()}) ---")
        
        headers = {}
        api_key = os.getenv("TTN_WEBHOOK_API_KEY")
        if api_key:
            headers["Authorization"] = f"Bearer {api_key}"

        for device_id, estado in animales.items():
            if device_id in EXCLUIR_DEL_LOOP_EN_VIVO:
                continue  # ver comentario de EXCLUIR_DEL_LOOP_EN_VIVO más arriba
            payload = generar_payload(device_id, estado)
            try:
                response = requests.post(API_URL, json=payload, headers=headers, timeout=5)
                if response.status_code in [200, 201, 202]:
                    exitos += 1
                else:
                    errores += 1
                    print(f"Error {response.status_code}: {response.text}")
            except requests.exceptions.RequestException as e:
                errores += 1
                print(f"Excepción de conexión: {e}")

        tiempo_transcurrido = time.time() - inicio_ciclo
        print(f"Ciclo completado en {tiempo_transcurrido:.2f}s. Éxitos: {exitos}, Errores: {errores}")

        tiempo_espera = INTERVALO_SEGUNDOS - tiempo_transcurrido
        if tiempo_espera > 0:
            print(f"Esperando {tiempo_espera:.2f} segundos para el próximo ciclo...\n")
            time.sleep(tiempo_espera)

if __name__ == "__main__":
    main()
