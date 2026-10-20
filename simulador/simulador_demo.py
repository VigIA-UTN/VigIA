"""
simulador_demo.py — la manada "de fondo" durante la demo E2E (Sprint 10).

Manda telemetría con comportamiento normal de pastoreo para el rodeo, sin
ningún escenario forzado. Queda corriendo toda la demo; cada acto tiene su
propio script que se prende en el momento justo:

    Acto 2 (celo de la vaca #104)   -> simulador_celo.py
    Acto 3 (fuga por el alambrado)  -> simulador_fuga.py

Reserva de identidades: este script NUNCA manda telemetría para las vacas que
mueven esos scripts (FORZAR_CELO_DEVICE_ID y FUGA_DEVICE_IDS), ni para
VIGIA-ARG-003 (ver EXCLUIR_DEL_LOOP_EN_VIVO). Si dos procesos mandaran para el
mismo device_id, cada uno con su propio estado en memoria, la vaca se vería
"teletransportándose" entre dos trayectorias — y el salto de posición puede
disparar alertas de celo falsas.

Acto 1 (Paso 1.6) — caravanas libres vinculadas en vivo:

Las caravanas de EXTRA_DEVICE_IDS (por defecto VIGIA-LIBRE-301..305) se
mandan desde el arranque. Mientras no están vinculadas a ninguna vaca, el
worker contesta "Ignorado: Sin animal asociado" y no guarda nada. Apenas Pedro
vincula una a la vaquillona #301 desde la UI, el siguiente paquete ya se
guarda y el animal aparece en el mapa en el próximo ciclo, sin reiniciar este
proceso ni tocar el .env. (Antes había un "descubrimiento" que consultaba
GET /api/v1/caravanas contra el worker, pero ese endpoint vive en backend-api,
se llama /api/v1/caravana y requiere sesión: nunca encontraba nada.)

Notas técnicas:

- La posición/batería inicial de cada animal se pide al backend
  (GET /api/v1/ingesta/ultima-posicion/{deviceId}) para continuar la última
  fila real, en vez de hardcodear coordenadas: un salto de posición
  desconectado de la última fila real dispara alertas de celo falsas (ver el
  BUGFIX documentado en simulador.py).
- INTERVALO_SEGUNDOS (cadencia real de envío, default 5 s) y
  TICK_MODELADO_SEGUNDOS (la magnitud de movimiento que "ve" el modelo por
  paquete, fija en 300 s) están desacoplados a propósito. El modelo de celo
  (ml-celo) no usa el timestamp como feature, solo moveState y la distancia
  entre puntos consecutivos; por eso la distancia por paquete queda fija a la
  escala de un tick de 5 min y bajar INTERVALO_SEGUNDOS solo acelera el ritmo,
  sin romper la calibración del modelo. El timestamp que se manda es siempre
  la hora real de envío.
- Si se lo quiere dejar corriendo varios días contra un backend desplegado,
  alcanza con INTERVALO_SEGUNDOS=300 (cadencia de una caravana real).
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

# Cadencia real de envío (ritmo de la demo). No participa del cálculo de
# distancia por paquete (ver TICK_MODELADO_SEGUNDOS).
INTERVALO_SEGUNDOS = int(os.getenv("INTERVALO_SEGUNDOS", 5))

# Escala de movimiento que "ve" el modelo de celo por paquete: un tick de 5
# minutos (300s), igual que ml-training/celo/generate_dataset.py. FIJO, no
# confundir con INTERVALO_SEGUNDOS.
TICK_MODELADO_SEGUNDOS = 300

# Fallback si el backend todavía no tiene ninguna fila para un device_id
# (centro del establecimiento del seed demo).
LAT_INICIAL = -34.943317
LON_INICIAL = -59.225728

# Escala física de pastoreo normal, alineada con generate_dataset.py: grados
# POR TICK DE 5 MINUTOS, fija por paquete.
SPEED_BASE_DEG_POR_TICK_5MIN = 0.000018
TURN_STD_BASE = 0.35  # rad por tick

# moveState real de la caravana: 0-9
MOVESTATE_REPOSO = (0, 1)
MOVESTATE_NORMAL = (2, 6)
MOVESTATE_ALTA_ACTIVIDAD = (7, 9)

# Reservadas para los scripts de cada acto: este proceso nunca les manda telemetría.
FORZAR_CELO_DEVICE_ID = os.getenv("FORZAR_CELO_DEVICE_ID", "VIGIA-ARG-001")
FUGA_DEVICE_IDS = {d.strip() for d in os.getenv("FUGA_DEVICE_IDS", "VIGIA-ARG-013,VIGIA-ARG-014").split(",")
                   if d.strip()}

# VIGIA-ARG-003 se deja fuera a propósito: el seed le pone una única
# transmisión vieja (> 15 min) para mostrar el indicador "sin datos recientes"
# del mapa (H0012). Si recibe telemetría nueva, ese detalle de la demo se pierde.
EXCLUIR_DEL_LOOP_EN_VIVO = {"VIGIA-ARG-003", FORZAR_CELO_DEVICE_ID} | FUGA_DEVICE_IDS

# Acto 1 (Paso 1.6): caravanas libres que Pedro puede vincular en vivo.
EXTRA_DEVICE_IDS = [d.strip() for d in os.getenv(
    "EXTRA_DEVICE_IDS",
    "VIGIA-LIBRE-301,VIGIA-LIBRE-302,VIGIA-LIBRE-303,VIGIA-LIBRE-304,VIGIA-LIBRE-305",
).split(",") if d.strip()]

# Respuesta del worker para una caravana que existe pero no está vinculada.
RESPUESTA_SIN_ANIMAL = "Ignorado: Sin animal asociado"

# Timeout corto para la consulta de posición inicial: si el backend no
# responde rápido, mejor arrancar con el fallback random que colgar el
# arranque de la demo.
TIMEOUT_ULTIMA_POSICION_SEGUNDOS = 3


def obtener_posicion_inicial(device_id):
    """Continúa la última posición/batería real que el backend ya tiene para
    este device_id, en vez de arrancar en un punto desconectado."""
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


device_ids = [f"VIGIA-ARG-{i:03d}" for i in range(1, NUM_ANIMALES + 1)]
for d in EXTRA_DEVICE_IDS:
    if d not in device_ids:
        device_ids.append(d)

animales = {}
for device_id in device_ids:
    if device_id in EXCLUIR_DEL_LOOP_EN_VIVO:
        continue
    lat_ini, lon_ini, bateria_ini = obtener_posicion_inicial(device_id)
    animales[device_id] = {
        "lat": lat_ini,
        "lon": lon_ini,
        "bateria": bateria_ini,
        "heading": random.uniform(0, 2 * math.pi),
        "vinculada": None,  # None = todavía no se sabe; se aprende de la respuesta del worker
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
        move_state = random.randint(*MOVESTATE_ALTA_ACTIVIDAD)  # pico ocasional, esperable

    # Magnitud de paso fija a la escala de un tick de 5 min: no se re-escala
    # según INTERVALO_SEGUNDOS (ver docstring).
    estado["heading"] += random.gauss(0, 1) * TURN_STD_BASE
    estado["lat"] += SPEED_BASE_DEG_POR_TICK_5MIN * math.cos(estado["heading"])
    estado["lon"] += SPEED_BASE_DEG_POR_TICK_5MIN * math.sin(estado["heading"])

    # f_cnt incremental: el backend tiene idempotencia por frame counter.
    # Sin él, el worker responde 200 pero descarta todos los paquetes
    # silenciosamente (fCnt=0 <= ultimoFrameCounter ya guardado).
    estado["f_cnt"] = estado.get("f_cnt", 0) + 1

    # Formato TTN v3 Webhook
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


def registrar_vinculacion(device_id, estado, respuesta):
    """Acto 1: avisa cuando una caravana libre pasa de ignorada a aceptada."""
    vinculada = respuesta != RESPUESTA_SIN_ANIMAL
    if vinculada and estado["vinculada"] is False:
        print(f"[ACTO 1] {device_id} fue vinculada a una vaca: el worker empezó a guardar "
              f"su telemetría. Aparece en el mapa en este ciclo.")
    estado["vinculada"] = vinculada
    return vinculada


def main():
    print(f"Iniciando simulador DEMO (manada de fondo) para {len(animales)} caravanas.")
    print(f"Target API: {API_URL}")
    print(f"Frecuencia de envío (real, ritmo de demo): {INTERVALO_SEGUNDOS} segundos.")
    print(f"Escala de movimiento por paquete (fija): tick de {TICK_MODELADO_SEGUNDOS} segundos.")
    print(f"Reservados para otros scripts o el seed (no se les manda telemetría acá): "
          f"{sorted(EXCLUIR_DEL_LOOP_EN_VIVO)}")
    print(f"[ACTO 1] Caravanas libres en el loop (el worker las ignora hasta que se vinculan): "
          f"{[d for d in EXTRA_DEVICE_IDS if d in animales]}")
    print(f"\n[{datetime.now().isoformat()}] Arrancando loop en vivo...\n")

    headers = {}
    api_key = os.getenv("TTN_WEBHOOK_API_KEY")
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"

    while True:
        inicio_ciclo = time.time()
        exitos = 0
        sin_vincular = 0
        errores = 0

        print(f"--- Iniciando ciclo de envío ({datetime.now().isoformat()}) ---")

        for device_id, estado in animales.items():
            payload = generar_payload(device_id, estado)
            try:
                response = requests.post(API_URL, json=payload, headers=headers, timeout=5)
                if response.status_code in [200, 201, 202]:
                    if registrar_vinculacion(device_id, estado, response.text.strip()):
                        exitos += 1
                    else:
                        sin_vincular += 1
                else:
                    errores += 1
                    print(f"Error {response.status_code}: {response.text}")
            except requests.exceptions.RequestException as e:
                errores += 1
                print(f"Excepción de conexión: {e}")

        tiempo_transcurrido = time.time() - inicio_ciclo
        print(f"Ciclo completado en {tiempo_transcurrido:.2f}s. Éxitos: {exitos}, "
              f"Caravanas sin vincular: {sin_vincular}, Errores: {errores}")

        tiempo_espera = INTERVALO_SEGUNDOS - tiempo_transcurrido
        if tiempo_espera > 0:
            print(f"Esperando {tiempo_espera:.2f} segundos para el próximo ciclo...\n")
            time.sleep(tiempo_espera)


if __name__ == "__main__":
    main()
