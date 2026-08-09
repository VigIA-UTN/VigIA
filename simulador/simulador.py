import os
import time
import random
import requests
from datetime import datetime, timezone, timedelta

# Configuración
API_URL = os.getenv("API_URL", "http://localhost:8081/api/v1/ingesta/uplink") # Apunta al backend-worker (8081)
NUM_ANIMALES = int(os.getenv("NUM_ANIMALES", 5)) # 5 vacas como solicitaste
INTERVALO_SEGUNDOS = int(os.getenv("INTERVALO_SEGUNDOS", 5)) # 5 segundos para pruebas en tiempo real

# Coordenadas iniciales: Establecimiento de Prueba (del DataInitializer)
LAT_INICIAL = -34.943317
LON_INICIAL = -59.225728

# ---------------------------------------------------------------------------
# Escala física de movimiento, alineada con generate_dataset.py:
#   SPEED_BASE = 0.000018 grados POR TICK DE 5 MINUTOS (300s) en pastoreo normal.
# Acá convertimos esa velocidad a grados/segundo y la aplicamos al intervalo
# real de envío (INTERVALO_SEGUNDOS), para que la distancia recorrida por
# tick que ve el modelo (una vez multiplicada por *100000 en ml-celo) quede
# en la misma escala que la usada en entrenamiento, sin importar cada cuánto
# mandemos el uplink. Si esto no coincide, TODAS las vacas (no solo la
# forzada) terminan pareciendo en "alta actividad" para el modelo.
# ---------------------------------------------------------------------------
SPEED_BASE_DEG_POR_TICK_5MIN = 0.000018
SPEED_BASE_DEG_POR_SEG = SPEED_BASE_DEG_POR_TICK_5MIN / 300.0
SPEED_BASE_DEG_POR_INTERVALO = SPEED_BASE_DEG_POR_SEG * INTERVALO_SEGUNDOS

# En celo, generate_dataset.py multiplica la velocidad por 1.0 + intensidad*(2 a 3),
# con intensidad≈1 en el pico → multiplicador de hasta ~4x.
SPEED_MULT_CELO_MIN = 2.5
SPEED_MULT_CELO_MAX = 4.0

# moveState real del collar: 0-9 (ver generate_dataset.py)
#   0-1 = reposo/rumia, 2-6 = pastoreo/caminata normal, 7-9 = alta actividad
MOVESTATE_REPOSO = (0, 1)
MOVESTATE_NORMAL = (2, 6)
MOVESTATE_ALTA_ACTIVIDAD = (7, 9)

# Estado inicial de los animales
animales = {}
for i in range(1, NUM_ANIMALES + 1):
    device_id = f"VIGIA-ARG-{i:03d}"
    animales[device_id] = {
        # Mantener las vacas en un radio cercano al establecimiento
        "lat": LAT_INICIAL + random.uniform(-0.005, 0.005),
        "lon": LON_INICIAL + random.uniform(-0.005, 0.005),
        "bateria": random.uniform(80.0, 100.0)
    }

def generar_payload(device_id, estado):
    # Desplazamiento de pastoreo normal, a escala física real (ver constantes arriba)
    lat_diff = random.uniform(-1.0, 1.0) * SPEED_BASE_DEG_POR_INTERVALO
    lon_diff = random.uniform(-1.0, 1.0) * SPEED_BASE_DEG_POR_INTERVALO

    # Consumo de batería leve
    estado["bateria"] -= random.uniform(0.01, 0.05)
    if estado["bateria"] < 0:
        estado["bateria"] = 100.0 # Se cambió el collar/batería

    # Comportamiento normal por defecto: mayormente reposo/pastoreo, rara vez alta actividad
    r = random.random()
    if r < 0.35:
        move_state = random.randint(*MOVESTATE_REPOSO)
    elif r < 0.95:
        move_state = random.randint(*MOVESTATE_NORMAL)
    else:
        move_state = random.randint(*MOVESTATE_ALTA_ACTIVIDAD)  # falso positivo ocasional

    # FORZAR CELO PARA VIGIA-ARG-001
    if device_id == "VIGIA-ARG-001":
        speed_mult = random.uniform(SPEED_MULT_CELO_MIN, SPEED_MULT_CELO_MAX)
        lat_diff = random.uniform(-1.0, 1.0) * SPEED_BASE_DEG_POR_INTERVALO * speed_mult
        lon_diff = random.uniform(-1.0, 1.0) * SPEED_BASE_DEG_POR_INTERVALO * speed_mult
        move_state = random.randint(*MOVESTATE_ALTA_ACTIVIDAD)  # siempre alta actividad (7-9)

    estado["lat"] += lat_diff
    estado["lon"] += lon_diff

    return {
        "device_id": device_id,
        "timestamp": datetime.now(timezone.utc).replace(tzinfo=None).isoformat(),
        "payload": {
            "latitud": round(estado["lat"], 6),
            "longitud": round(estado["lon"], 6),
            "moveState": move_state,
            "bateria": round(estado["bateria"], 1)
        }
    }

def main():
    print(f"Iniciando simulador para {NUM_ANIMALES} animales en el Establecimiento de Prueba.")
    print(f"Target API: {API_URL}")
    print(f"Intervalo de envío: {INTERVALO_SEGUNDOS} segundos.")

    print("Inyectando historial anómalo para VIGIA-ARG-001 (simulando celo previo)...")
    estado_001 = animales["VIGIA-ARG-001"]
    for i in range(10, 0, -1): # Reducido a 10 para que arranque más rápido en esta prueba local
        past_time = datetime.now(timezone.utc) - timedelta(seconds=i*INTERVALO_SEGUNDOS)
        speed_mult = random.uniform(SPEED_MULT_CELO_MIN, SPEED_MULT_CELO_MAX)
        estado_001["lat"] += random.uniform(-1.0, 1.0) * SPEED_BASE_DEG_POR_INTERVALO * speed_mult
        estado_001["lon"] += random.uniform(-1.0, 1.0) * SPEED_BASE_DEG_POR_INTERVALO * speed_mult
        payload = {
            "device_id": "VIGIA-ARG-001",
            "timestamp": past_time.replace(tzinfo=None).isoformat(),
            "payload": {
                "latitud": round(estado_001["lat"], 6),
                "longitud": round(estado_001["lon"], 6),
                "moveState": random.randint(*MOVESTATE_ALTA_ACTIVIDAD),
                "bateria": 100.0
            }
        }
        try:
            requests.post(API_URL, json=payload, timeout=2)
        except:
            pass
    print("Historial inyectado exitosamente.")

    while True:
        inicio_ciclo = time.time()
        exitos = 0
        errores = 0

        print(f"\n--- Iniciando ciclo de envío ({datetime.now().isoformat()}) ---")
        for device_id, estado in animales.items():
            payload = generar_payload(device_id, estado)
            try:
                response = requests.post(API_URL, json=payload, timeout=5)
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
            print(f"Esperando {tiempo_espera:.2f} segundos para el próximo ciclo...")
            time.sleep(tiempo_espera)

if __name__ == "__main__":
    main()