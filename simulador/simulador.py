import os
import time
import random
import requests
from datetime import datetime, timezone, timedelta

# Configuración
API_URL = os.getenv("API_URL", "http://localhost:8080/api/v1/ingesta/uplink") # Ajustado a localhost para probar localmente
NUM_ANIMALES = int(os.getenv("NUM_ANIMALES", 5)) # 5 vacas como solicitaste
INTERVALO_SEGUNDOS = int(os.getenv("INTERVALO_SEGUNDOS", 5)) # 5 segundos para pruebas en tiempo real

# Coordenadas iniciales: Establecimiento de Prueba (del DataInitializer)
LAT_INICIAL = -34.943317
LON_INICIAL = -59.225728

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
    # Simular desplazamiento de pastoreo (caminata aleatoria) dentro del radio
    lat_diff = random.uniform(-0.0003, 0.0003)
    lon_diff = random.uniform(-0.0003, 0.0003)
    
    # Consumo de batería leve
    estado["bateria"] -= random.uniform(0.01, 0.05)
    if estado["bateria"] < 0:
        estado["bateria"] = 100.0 # Se cambió el collar/batería
        
    # Comportamiento normal por defecto
    move_state = random.choices([0, 1, 2], weights=[0.3, 0.6, 0.1])[0]
    nivel_rumia = random.uniform(0.0, 100.0)
    
    # FORZAR CELO PARA VIGIA-ARG-001
    if device_id == "VIGIA-ARG-001":
        # Mayor desplazamiento (alta actividad motora) pero no tanto para que no desaparezca del mapa
        lat_diff = random.uniform(0.0004, 0.0006) * random.choice([-1, 1])
        lon_diff = random.uniform(0.0004, 0.0006) * random.choice([-1, 1])
        # Forzar estado de movimiento a 2 (Alta actividad)
        move_state = 2
        # Menor tiempo de rumia (típico en celo)
        nivel_rumia = random.uniform(0.0, 20.0)
        
    estado["lat"] += lat_diff
    estado["lon"] += lon_diff
    
    return {
        "device_id": device_id,
        "timestamp": datetime.utcnow().isoformat(),
        "payload": {
            "latitud": round(estado["lat"], 5),
            "longitud": round(estado["lon"], 5),
            "moveState": move_state,
            "nivelRumia": round(nivel_rumia, 2),
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
        past_time = datetime.utcnow() - timedelta(seconds=i*INTERVALO_SEGUNDOS)
        estado_001["lat"] += random.uniform(0.0005, 0.001) * random.choice([-1, 1])
        estado_001["lon"] += random.uniform(0.0005, 0.001) * random.choice([-1, 1])
        payload = {
            "device_id": "VIGIA-ARG-001",
            "timestamp": past_time.isoformat(),
            "payload": {
                "latitud": round(estado_001["lat"], 5),
                "longitud": round(estado_001["lon"], 5),
                "moveState": 2,
                "nivelRumia": round(random.uniform(0.0, 20.0), 2),
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
            except requests.exceptions.RequestException as e:
                errores += 1
        
        tiempo_transcurrido = time.time() - inicio_ciclo
        print(f"Ciclo completado en {tiempo_transcurrido:.2f}s. Éxitos: {exitos}, Errores: {errores}")
        
        tiempo_espera = INTERVALO_SEGUNDOS - tiempo_transcurrido
        if tiempo_espera > 0:
            print(f"Esperando {tiempo_espera:.2f} segundos para el próximo ciclo...")
            time.sleep(tiempo_espera)

if __name__ == "__main__":
    main()
