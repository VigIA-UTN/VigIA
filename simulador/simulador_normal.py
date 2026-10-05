"""
simulador_normal.py — comportamiento "de fondo" de la manada, sin ningún
escenario forzado. Pensado para dejarse corriendo largos períodos (incluso
varios días) contra un backend ya desplegado, como base realista sobre la
que después se activan por separado los simuladores de escenarios puntuales
(ver simulador_celo.py; salud todavía no tiene su propia variante).

Por qué esto es un archivo aparte y no una rama más de simulador_demo.py:
antes, un único script mezclaba "el comportamiento normal de la manada" con
"el forzado de celo/salud" detrás de flags y env vars (FORZAR_CELO_DEVICE_ID,
INYECTAR_HISTORIAL_SALUD). Eso funciona para una demo puntual, pero no da
control fino sobre CUÁNDO se muestra cada escenario: había que reiniciar el
script (o preverlo con anticipación) para activar/desactivar un forzado.
Separando en procesos independientes, este script queda corriendo todo el
tiempo con la manada comportándose "como si fueran vacas reales" (sin nada
inyectado ni forzado), y el de celo se prende/apaga en el momento exacto en
que se lo quiere mostrar, sin tocar este proceso.

Reserva de identidades: este script NUNCA manda telemetría para el device_id
de FORZAR_CELO_DEVICE_ID (reservado para simulador_celo.py) ni para
VIGIA-ARG-003 (reservado para el escenario "sin datos recientes" del mapa,
H0012 — ver DataInitializer.java). Si dos procesos mandaran telemetría para
el mismo device_id, cada uno tiene su propio estado en memoria (heading,
posición), y el resultado se ve como una vaca "teletransportándose" entre
dos trayectorias distintas — por eso la exclusión es esencial, no cosmética.

Notas técnicas heredadas de simulador_demo.py (se mantienen sin cambios):

- La posición/batería inicial de cada animal se pide al backend
  (GET /api/v1/ingesta/ultima-posicion/{deviceId}) para continuar la última
  fila real, en vez de hardcodear coordenadas — ver el BUGFIX documentado en
  simulador.py sobre por qué un salto de posición desconectado dispara
  alertas de celo falsas.
- INTERVALO_SEGUNDOS (cadencia real de envío) y TICK_MODELADO_SEGUNDOS (la
  magnitud de movimiento que "ve" el modelo por paquete, fija en 300s) están
  desacoplados a propósito: acá INTERVALO_SEGUNDOS default es 300 (5 min,
  igual que una caravana real) porque este script SÍ está pensado para correr
  con cadencia real y no necesita ir rápido. Si en algún momento hace falta
  acelerarlo para pruebas, se puede bajar sin miedo a romper la calibración
  del modelo — la distancia por paquete no depende de este valor.
"""

import os
import time
import math
import random
import requests
from datetime import datetime, timezone

# pyrefly: ignore [missing-import]
from dotenv import load_dotenv

load_dotenv()

API_URL = os.getenv("API_URL", "http://localhost:8081/api/v1/ingesta/uplink")
NUM_ANIMALES = int(os.getenv("NUM_ANIMALES", 5))

# Cadencia real de envío. Este script está pensado para correr con el mismo
# ritmo que una caravana real (5 min) durante períodos largos; no participa del
# cálculo de distancia por paquete (ver TICK_MODELADO_SEGUNDOS).
INTERVALO_SEGUNDOS = int(os.getenv("INTERVALO_SEGUNDOS", 300))

# Escala de movimiento que "ve" el modelo por paquete: un tick de 5 minutos
# (300s), igual que ml-training/celo/generate_dataset.py. Fija, no se
# re-escala según INTERVALO_SEGUNDOS.
TICK_MODELADO_SEGUNDOS = 300

LAT_INICIAL = -34.943317
LON_INICIAL = -59.225728

SPEED_BASE_DEG_POR_TICK_5MIN = 0.000018
TURN_STD_BASE = 0.35  # rad por tick, pastoreo normal (igual que generate_dataset.py)

# moveState real de la caravana: 0-9
MOVESTATE_REPOSO = (0, 1)
MOVESTATE_NORMAL = (2, 6)
MOVESTATE_ALTA_ACTIVIDAD = (7, 9)

# Device_id reservado por simulador_celo.py: este script nunca le manda
# telemetría, aunque esté dentro del rango 1..NUM_ANIMALES.
FORZAR_CELO_DEVICE_ID = os.getenv("FORZAR_CELO_DEVICE_ID", "VIGIA-ARG-001")

# VIGIA-ARG-003 se deja fuera a propósito: DataInitializer le pone una única
# transmisión vieja (> 15 min) para mostrar el indicador "sin datos
# recientes" del mapa (H0012). Si este script le manda telemetría nueva, ese
# detalle de la demo se pierde.
EXCLUIDOS_SIEMPRE = {"VIGIA-ARG-003"}
EXCLUIR_DEL_LOOP_EN_VIVO = EXCLUIDOS_SIEMPRE | {FORZAR_CELO_DEVICE_ID}

TIMEOUT_ULTIMA_POSICION_SEGUNDOS = 3


def obtener_posicion_inicial(device_id):
    """Continúa la última posición/batería real que el backend ya tiene para
    este device_id, en vez de arrancar en un punto desconectado (ver el
    BUGFIX documentado en simulador.py)."""
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


animales = {}
for i in range(1, NUM_ANIMALES + 1):
    device_id = f"VIGIA-ARG-{i:03d}"
    if device_id in EXCLUIR_DEL_LOOP_EN_VIVO:
        continue
    lat_ini, lon_ini, bateria_ini = obtener_posicion_inicial(device_id)
    animales[device_id] = {
        "lat": lat_ini,
        "lon": lon_ini,
        "bateria": bateria_ini,
        "heading": random.uniform(0, 2 * math.pi),
    }


def generar_payload(device_id, estado):
    estado["bateria"] -= random.uniform(0.01, 0.05)
    if estado["bateria"] < 0:
        estado["bateria"] = 100.0

    r = random.random()
    if r < 0.35:
        move_state = random.randint(*MOVESTATE_REPOSO)
    elif r < 0.95:
        move_state = random.randint(*MOVESTATE_NORMAL)
    else:
        move_state = random.randint(*MOVESTATE_ALTA_ACTIVIDAD)  # falso positivo ocasional, esperable

    # Magnitud de paso fija a la escala de un tick de 5 min: no se re-escala
    # según INTERVALO_SEGUNDOS (ver docstring).
    estado["heading"] += random.gauss(0, 1) * TURN_STD_BASE
    speed = SPEED_BASE_DEG_POR_TICK_5MIN
    estado["lat"] += speed * math.cos(estado["heading"])
    estado["lon"] += speed * math.sin(estado["heading"])

    estado["f_cnt"] = estado.get("f_cnt", 0) + 1

    # Generamos el formato TTN v3 Webhook
    return {
        "end_device_ids": {
            "dev_eui": device_id
        },
        "uplink_message": {
            "received_at": datetime.now(timezone.utc).isoformat(),
            "f_cnt": estado["f_cnt"],
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
    print(f"Iniciando simulador NORMAL para {len(animales)} animales en el Establecimiento de Prueba.")
    print(f"Target API: {API_URL}")
    print(f"Cadencia de envío: {INTERVALO_SEGUNDOS} segundos (real).")
    print(f"Animales reservados para otros simuladores (no se les manda telemetría acá): "
          f"{sorted(EXCLUIR_DEL_LOOP_EN_VIVO)}")
    print(f"\n[{datetime.now().isoformat()}] Arrancando loop en vivo...\n")

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
