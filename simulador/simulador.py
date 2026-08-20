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
API_URL = os.getenv("API_URL", "http://localhost:8081/api/v1/ingesta/uplink") # Apunta al backend-worker (8081)
NUM_ANIMALES = int(os.getenv("NUM_ANIMALES", 5)) # 5 vacas como solicitaste

# IMPORTANTE: el modelo de celo (ml-training/celo/generate_dataset.py) se entrenó
# con SAMPLES_PER_DAY=288, es decir UNA lectura cada 5 minutos (300s). La
# distancia entre lecturas consecutivas es una feature más (moveState, distancia),
# y el modelo la interpreta como "cuánto se desplazó el animal en 5 minutos".
# Si el simulador manda lecturas más seguido (ej. cada 5s) y solo reescala la
# distancia proporcionalmente al intervalo (como hacía antes esta constante),
# cada tick individual queda representando un desplazamiento de solo 5 segundos
# — 60 veces más chico que lo que el modelo aprendió a considerar "alta
# actividad" — y la probabilidad de celo nunca cruza el umbral, sin importar
# qué tan alto sea moveState. Se verificó empíricamente contra el modelo
# entrenado (ml-celo/app/models/): con INTERVALO_SEGUNDOS=5 la confianza queda
# ~0.02, con INTERVALO_SEGUNDOS=300 (5 min, igual que entrenamiento) sube a
# ~0.8-0.97. Por eso el default pasa a ser 300s.
INTERVALO_SEGUNDOS = int(os.getenv("INTERVALO_SEGUNDOS", 300))  # 5 minutos, igual que el collar real / dataset de entrenamiento

# Coordenadas iniciales: Establecimiento de Prueba (del DataInitializer)
LAT_INICIAL = -34.943317
LON_INICIAL = -59.225728

# ---------------------------------------------------------------------------
# Escala física de movimiento, alineada con generate_dataset.py:
#   SPEED_BASE = 0.000018 grados POR TICK DE 5 MINUTOS (300s) en pastoreo normal.
# Convertimos esa velocidad a grados/segundo y la aplicamos al intervalo real
# de envío (INTERVALO_SEGUNDOS). Con INTERVALO_SEGUNDOS=300 esto da
# directamente SPEED_BASE_DEG_POR_TICK_5MIN, tal cual la usa el dataset de
# entrenamiento. Si se baja INTERVALO_SEGUNDOS para hacer pruebas más rápidas
# de otras partes del sistema (mapa, dashboard, etc.), tené en cuenta que la
# detección de celo deja de ser confiable (ver comentario arriba).
# ---------------------------------------------------------------------------
SPEED_BASE_DEG_POR_TICK_5MIN = 0.000018
SPEED_BASE_DEG_POR_SEG = SPEED_BASE_DEG_POR_TICK_5MIN / 300.0
SPEED_BASE_DEG_POR_INTERVALO = SPEED_BASE_DEG_POR_SEG * INTERVALO_SEGUNDOS

# En celo, generate_dataset.py multiplica la velocidad por 1.0 + intensidad*(2 a 3),
# con intensidad≈1 en el pico → multiplicador de hasta ~4x.
SPEED_MULT_CELO_MIN = 2.5
SPEED_MULT_CELO_MAX = 4.0

# ---------------------------------------------------------------------------
# Random walk CORRELACIONADO (con rumbo/heading), igual que generate_dataset.py.
# Antes generábamos lat_diff/lon_diff como dos componentes independientes con
# random.uniform(-1,1) cada una. Eso hace que la magnitud del desplazamiento
# por tick (lo que el modelo ve como "distancia") sea muy ruidosa: a veces las
# componentes casi se cancelan, a veces se suman al máximo, así que la misma
# "intensidad de celo" produce probabilidades muy dispares corrida a corrida
# (medido: 0.27 a 0.93 con el generador viejo, promedio 0.74 — por debajo del
# CELO_THRESHOLD=0.892 configurado en .env la mayoría de las veces). Usando un
# rumbo que persiste entre ticks y un paso de magnitud fija (speed*cos/sin),
# igual que en entrenamiento, la probabilidad queda estable en ~0.996-0.998.
# ---------------------------------------------------------------------------
TURN_STD_BASE = 0.35   # rad por tick, pastoreo normal (igual que generate_dataset.py)
TURN_STD_CELO = 1.1    # trayectoria errática durante celo

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
        "bateria": random.uniform(80.0, 100.0),
        "heading": random.uniform(0, 2 * math.pi),  # rumbo inicial, para el random walk correlacionado
    }

def generar_payload(device_id, estado):
    en_celo = device_id == "VIGIA-ARG-001"  # FORZAR CELO PARA VIGIA-ARG-001

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

    speed_mult = 1.0
    turn_std = TURN_STD_BASE
    if en_celo:
        speed_mult = random.uniform(SPEED_MULT_CELO_MIN, SPEED_MULT_CELO_MAX)
        turn_std = TURN_STD_CELO
        move_state = random.randint(*MOVESTATE_ALTA_ACTIVIDAD)  # siempre alta actividad (7-9)

    # Random walk correlacionado (rumbo persistente + paso de magnitud fija),
    # igual que generate_dataset.py: evita que la distancia por tick quede
    # tan ruidosa como con componentes lat/lon independientes (ver comentario
    # de TURN_STD_BASE/TURN_STD_CELO más arriba).
    estado["heading"] += random.gauss(0, 1) * turn_std
    speed = SPEED_BASE_DEG_POR_INTERVALO * speed_mult
    estado["lat"] += speed * math.cos(estado["heading"])
    estado["lon"] += speed * math.sin(estado["heading"])

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

    # El microservicio ml-celo espera ventanas de 24hs (SAMPLES_PER_DIA=288 en
    # generate_dataset.py). El backend-worker (CeloBatchConfig) manda a
    # /predict TODA la telemetría pendiente de las últimas 24hs de la
    # caravana, así que para que la primera corrida del job de celo (corre
    # cada 60s, ver BatchScheduler) ya tenga una ventana representativa,
    # inyectamos 288 puntos históricos espaciados cada 5 minutos (no 10
    # puntos sueltos como antes), igual que una jornada completa de collar
    # real en celo.
    SAMPLES_PER_DIA = 288
    TICK_HISTORIAL_SEGUNDOS = 300  # 5 minutos, igual que generate_dataset.py

    print(f"Inyectando {SAMPLES_PER_DIA} puntos de historial (24hs, 1 cada 5 min) "
          f"para VIGIA-ARG-001 (simulando celo previo)...")
    estado_001 = animales["VIGIA-ARG-001"]
    for i in range(SAMPLES_PER_DIA, 0, -1):
        past_time = datetime.now(timezone.utc) - timedelta(seconds=i*TICK_HISTORIAL_SEGUNDOS)
        speed_mult = random.uniform(SPEED_MULT_CELO_MIN, SPEED_MULT_CELO_MAX)
        # Random walk correlacionado (rumbo persistente), a escala de un tick
        # de 5 minutos (no de INTERVALO_SEGUNDOS): esta ventana histórica
        # siempre representa "un día de collar real", independientemente de
        # cada cuánto mande el simulador en tiempo real. El "heading" queda
        # guardado en estado_001 y sigue evolucionando en el loop en vivo.
        estado_001["heading"] += random.gauss(0, 1) * TURN_STD_CELO
        speed = SPEED_BASE_DEG_POR_TICK_5MIN * speed_mult
        estado_001["lat"] += speed * math.cos(estado_001["heading"])
        estado_001["lon"] += speed * math.sin(estado_001["heading"])
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