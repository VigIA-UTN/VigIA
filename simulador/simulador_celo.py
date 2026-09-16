"""
simulador_celo.py — proceso independiente que simula ÚNICAMENTE la vaca
forzada a celo (FORZAR_CELO_DEVICE_ID). Se prende por separado del
simulador de fondo (ver simulador_normal.py) justo en el momento en que se
quiere mostrar la detección de celo, y se apaga (Ctrl+C) cuando ya se
mostró — no necesita quedar corriendo el resto del tiempo.

Requisito: DataInitializer.java ya sembró, al arrancar el backend, las 288
filas (24hs) de historial con patrón de celo para este animal (ver
sembrarHistorialCelo()), marcadas como ya inferidas. Este script NO inyecta
ese historial — su único trabajo es mandar, en vivo, un puñado de puntos
nuevos con ese mismo patrón; alcanza con que lleguen unos pocos (quedan
PENDIENTE) para que el próximo ciclo del job de celo (corre cada 60s, ver
BatchScheduler) evalúe la ventana completa de 24hs —ya con forma de celo de
punta a punta— y dispare la alerta.

Reserva de identidad: mientras este script no está corriendo, este device_id
NO debe recibirle telemetría a simulador_normal.py (que lo excluye a
propósito, ver EXCLUIR_DEL_LOOP_EN_VIVO ahí) — si dos procesos mandaran para
el mismo device_id, cada uno con su propio estado en memoria, la vaca se ve
"teletransportándose" entre dos trayectorias distintas.

Notas técnicas heredadas de simulador_demo.py (se mantienen sin cambios):

- La posición/batería inicial se pide al backend
  (GET /api/v1/ingesta/ultima-posicion/{deviceId}) para continuar la última
  fila real que dejó el historial sembrado por DataInitializer (o una
  corrida anterior de este mismo script) — no coordenadas hardcodeadas.
- INTERVALO_SEGUNDOS (cadencia real de envío, default 5s acá porque este
  script SÍ está pensado para ir rápido y mostrar el efecto pronto) y
  TICK_MODELADO_SEGUNDOS (magnitud de movimiento por paquete, fija en 300s,
  la escala que espera el modelo entrenado) están desacoplados a propósito:
  el modelo de celo no usa el timestamp como feature (solo moveState y
  distancia geométrica cruda entre puntos, ver ml-celo/app/main.py), así
  que se puede mandar rápido sin perder confianza en la predicción. El
  timestamp que se manda es siempre la hora real de envío.
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
FORZAR_CELO_DEVICE_ID = os.getenv("FORZAR_CELO_DEVICE_ID", "VIGIA-ARG-001")

# Cadencia real de envío: puramente el ritmo al que se quiere ver aparecer
# el efecto en la demo. No participa del cálculo de distancia por paquete
# (ver TICK_MODELADO_SEGUNDOS).
INTERVALO_SEGUNDOS = int(os.getenv("INTERVALO_SEGUNDOS", 5))

# Escala de movimiento que "ve" el modelo por paquete: un tick de 5 minutos
# (300s), igual que ml-training/celo/generate_dataset.py. Fija.
TICK_MODELADO_SEGUNDOS = 300

LAT_INICIAL = -34.943317
LON_INICIAL = -59.225728

SPEED_BASE_DEG_POR_TICK_5MIN = 0.000018

# En celo, generate_dataset.py multiplica la velocidad por 1.0 + intensidad*(2 a 3).
SPEED_MULT_CELO_MIN = 2.5
SPEED_MULT_CELO_MAX = 4.0
TURN_STD_CELO = 1.1  # trayectoria errática durante celo

MOVESTATE_ALTA_ACTIVIDAD = (7, 9)

TIMEOUT_ULTIMA_POSICION_SEGUNDOS = 3


def obtener_posicion_inicial(device_id):
    """Continúa la última posición/batería real que el backend ya tiene para
    este device_id (sembrada por DataInitializer o dejada por una corrida
    anterior de este script), en vez de arrancar en un punto desconectado
    (ver el BUGFIX documentado en simulador.py)."""
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


def generar_payload(estado):
    estado["bateria"] -= random.uniform(0.01, 0.05)
    if estado["bateria"] < 0:
        estado["bateria"] = 100.0

    move_state = random.randint(*MOVESTATE_ALTA_ACTIVIDAD)
    speed_mult = random.uniform(SPEED_MULT_CELO_MIN, SPEED_MULT_CELO_MAX)

    # Magnitud de paso fija a la escala de un tick de 5 min: no se re-escala
    # según INTERVALO_SEGUNDOS (ver docstring).
    estado["heading"] += random.gauss(0, 1) * TURN_STD_CELO
    speed = SPEED_BASE_DEG_POR_TICK_5MIN * speed_mult
    estado["lat"] += speed * math.cos(estado["heading"])
    estado["lon"] += speed * math.sin(estado["heading"])

    # Generamos el formato TTN v3 Webhook
    return {
        "end_device_ids": {
            "dev_eui": FORZAR_CELO_DEVICE_ID
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
    lat_ini, lon_ini, bateria_ini = obtener_posicion_inicial(FORZAR_CELO_DEVICE_ID)
    estado = {
        "lat": lat_ini,
        "lon": lon_ini,
        "bateria": bateria_ini,
        "heading": random.uniform(0, 2 * math.pi),
    }

    print(f"Iniciando simulador de CELO para {FORZAR_CELO_DEVICE_ID}.")
    print(f"Target API: {API_URL}")
    print(f"Cadencia de envío (real, ritmo de demo): {INTERVALO_SEGUNDOS} segundos.")
    print("Historial de 24hs con patrón de celo ya persistido por DataInitializer al arrancar "
          "el backend — este script solo manda los puntos nuevos que hacen falta para que el "
          "job de celo (corre cada 60s) evalúe la ventana completa.")
    print(f"\n[{datetime.now().isoformat()}] Arrancando loop en vivo. La alerta debería aparecer "
          f"en el dashboard dentro del próximo minuto o dos...\n")

    while True:
        inicio_ciclo = time.time()
        payload = generar_payload(estado)
        
        headers = {}
        api_key = os.getenv("TTN_WEBHOOK_API_KEY")
        if api_key:
            headers["Authorization"] = f"Bearer {api_key}"
            
        try:
            response = requests.post(API_URL, json=payload, headers=headers, timeout=5)
            if response.status_code in [200, 201, 202]:
                decoded = payload['uplink_message']['decoded_payload']
                print(f"[{datetime.now().isoformat()}] OK — "
                      f"lat={decoded['latitude']} lon={decoded['longitude']} "
                      f"moveState={decoded['moveState']}")
            else:
                print(f"Error {response.status_code}: {response.text}")
        except requests.exceptions.RequestException as e:
            print(f"Excepción de conexión: {e}")

        tiempo_transcurrido = time.time() - inicio_ciclo
        tiempo_espera = INTERVALO_SEGUNDOS - tiempo_transcurrido
        if tiempo_espera > 0:
            time.sleep(tiempo_espera)


if __name__ == "__main__":
    main()
