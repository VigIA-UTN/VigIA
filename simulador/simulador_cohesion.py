"""
simulador_cohesion.py — Simula el escenario realista de cohesión de rodeo:
  1. Fase 1: Los 5 animales arrancan en las posiciones dispersas del DataInitializer
     y caminan progresivamente hacia el centro del potrero para juntarse.
  2. Fase 2: Todos se agrupan en las posiciones de reset_posiciones (pastoreo apiñado, <15m).
  3. Fase 3: 3 animales se quedan juntos en el rodeo y 2 animales se van fuera del radio
     permitido (umbral de aislamiento ~100m), frenando su deriva en ~125m para quedarse
     pastoreando ahí sin irse a miles de kilómetros.

Uso:
  py simulador_cohesion.py                        # usa valores por defecto
  py simulador_cohesion.py --intervalo 5          # ciclo cada 5 s
  py simulador_cohesion.py --ciclos-juntos 4      # se quedan 4 ciclos juntos antes de separar
  py simulador_cohesion.py --distancia-aislado 125# radio límite de alejamiento en metros
"""

import os
import sys
import time
import math
import random
import argparse
import requests
from datetime import datetime, timezone
# pyrefly: ignore [missing-import]
from dotenv import load_dotenv

load_dotenv()

# ---------------------------------------------------------------------------
# Configuración y Parámetros
# ---------------------------------------------------------------------------

API_URL = os.getenv("API_URL", "http://localhost:8081/api/v1/ingesta/uplink")
INTERVALO_SEGUNDOS = int(os.getenv("INTERVALO_SEGUNDOS", 5))

# Coordenadas base: Establecimiento de Prueba
LAT_BASE = -34.943317
LNG_BASE = -59.225728

# Grupos
IDS_GRUPO = ["VIGIA-ARG-001", "VIGIA-ARG-002", "VIGIA-ARG-003"]
IDS_ALEJADOS = ["VIGIA-ARG-004", "VIGIA-ARG-005"]
TODOS_LOS_IDS = IDS_GRUPO + IDS_ALEJADOS

# ---------------------------------------------------------------------------
# Posiciones Geográficas
# ---------------------------------------------------------------------------

# 1. Posiciones en las que arrancan según el DataInitializer del backend:
POSICIONES_INITIALIZER = {
    "VIGIA-ARG-001": (-34.943000, -59.225500),
    "VIGIA-ARG-002": (-34.944800, -59.224100),
    "VIGIA-ARG-003": (-34.943317, -59.225728),
    "VIGIA-ARG-004": (-34.942100, -59.227900),
    "VIGIA-ARG-005": (-34.945500, -59.226000),
}

# 2. Posiciones de reagrupamiento exactas (las de reset_posiciones.py):
#    Todas a menos de 10-15 metros entre sí en el centroide del potrero.
POSICIONES_RESET = {
    "VIGIA-ARG-001": (LAT_BASE + 0.00003, LNG_BASE + 0.00003),
    "VIGIA-ARG-002": (LAT_BASE - 0.00003, LNG_BASE - 0.00002),
    "VIGIA-ARG-003": (LAT_BASE + 0.00001, LNG_BASE - 0.00004),
    "VIGIA-ARG-004": (LAT_BASE - 0.00002, LNG_BASE + 0.00004),
    "VIGIA-ARG-005": (LAT_BASE + 0.00004, LNG_BASE - 0.00002),
}

# Velocidades y umbrales
VELOCIDAD_CONVERGENCIA_M = 35.0   # metros por ciclo al juntarse al inicio
SPEED_ALEJAMIENTO_M = 18.0        # metros por ciclo cuando 004 y 005 se van
SPEED_PASTOREO_M = 1.8            # metros por ciclo en pastoreo normal

TURN_STD_GRUPO = 0.15
MOVESTATE_NORMAL = (2, 6)

# ---------------------------------------------------------------------------
# Helpers Geodésicos
# ---------------------------------------------------------------------------

def _distancia_m(lat1, lng1, lat2, lng2):
    """Distancia en metros entre dos coordenadas (fórmula plana ajustada a latitud)."""
    dlat_m = (lat2 - lat1) * 111320.0
    dlng_m = (lng2 - lng1) * (111320.0 * math.cos(math.radians((lat1 + lat2) / 2.0)))
    return math.hypot(dlat_m, dlng_m)

def _distancia_base_m(lat, lng):
    return _distancia_m(lat, lng, LAT_BASE, LNG_BASE)

def _mover_hacia(lat, lng, target_lat, target_lng, step_m):
    """Mueve la posición actual hacia la posición objetivo por un máximo de step_m metros."""
    dist = _distancia_m(lat, lng, target_lat, target_lng)
    if dist <= step_m:
        return target_lat, target_lng, 0.0
    frac = step_m / dist
    nueva_lat = lat + (target_lat - lat) * frac
    nueva_lng = lng + (target_lng - lng) * frac
    return nueva_lat, nueva_lng, dist - step_m

# ---------------------------------------------------------------------------
# Inicialización del estado de los animales
# ---------------------------------------------------------------------------

def _init_animales():
    """Inicializa a todos los animales en las posiciones originales del DataInitializer."""
    animales = {}
    for dev_id in TODOS_LOS_IDS:
        lat_init, lng_init = POSICIONES_INITIALIZER[dev_id]
        animales[dev_id] = {
            "lat": lat_init,
            "lng": lng_init,
            "bateria": 100.0,
            "heading": (
                math.pi / 2 if dev_id == "VIGIA-ARG-004" else
                -math.pi / 2 if dev_id == "VIGIA-ARG-005" else
                random.uniform(0, 2 * math.pi)
            ),
            "en_rodeo": False,
        }
    return animales

# ---------------------------------------------------------------------------
# Generación y Envío de Telemetría
# ---------------------------------------------------------------------------

def _generar_payload(device_id, estado, f_cnt, session_id):
    estado["bateria"] -= random.uniform(0.01, 0.04)
    if estado["bateria"] < 0:
        estado["bateria"] = 100.0

    move_state = random.randint(*MOVESTATE_NORMAL)

    return {
        "end_device_ids": {"dev_eui": device_id},
        "uplink_message": {
            "f_cnt": f_cnt,
            "session_key_id": session_id,
            "received_at": datetime.now(timezone.utc).isoformat(),
            "decoded_payload": {
                "type": "GNSSPosition",
                "latitude":  round(estado["lat"], 6),
                "longitude": round(estado["lng"], 6),
                "moveState": move_state,
                "battery":   "{:.1f}%".format(estado["bateria"]),
            },
        },
    }

def _enviar(payload):
    headers = {}
    api_key = os.getenv("TTN_WEBHOOK_API_KEY", "apiKey")
    if api_key:
        headers["Authorization"] = "Bearer {}".format(api_key)
    try:
        resp = requests.post(API_URL, json=payload, headers=headers, timeout=5)
        return resp.status_code in (200, 201, 202), resp.status_code
    except requests.exceptions.RequestException as e:
        return False, str(e)

# ---------------------------------------------------------------------------
# Main Simulator Loop
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Simulador de Cohesión: convergencia desde DataInitializer, agrupación y aislamiento controlado."
    )
    parser.add_argument(
        "--intervalo",
        type=int,
        default=INTERVALO_SEGUNDOS,
        help="Segundos entre ciclos de envío (default: {})".format(INTERVALO_SEGUNDOS),
    )
    parser.add_argument(
        "--ciclos-juntos",
        type=int,
        default=4,
        help="Ciclos que permanecen todas juntas en el rodeo antes de iniciar la separación (default: 4)",
    )
    parser.add_argument(
        "--distancia-aislado",
        type=float,
        default=125.0,
        help="Distancia objetivo en metros del animal aislado (fuera del umbral de 100m, sin irse lejos) (default: 125.0)",
    )
    args = parser.parse_args()

    session_id = f"cohesion_{int(time.time())}"

    print("")
    print("=" * 76)
    print("  VigIA - Simulador de Cohesion (Convergencia -> Rodeo -> Aislamiento)")
    print("=" * 76)
    print("  Backend             : {}".format(API_URL))
    print("  Sesion OTAA         : {}".format(session_id))
    print("  Intervalo           : {} s".format(args.intervalo))
    print("  Ciclos agrupados    : {} ciclos todas juntas".format(args.ciclos_juntos))
    print("  Distancia aislado   : {} m (fuera del radio permitido de 100m)".format(args.distancia_aislado))
    print("")
    print("  Fases de la simulación:")
    print("    1. CONVERGENCIA : Arrancan dispersas (DataInitializer) y caminan al rodeo.")
    print("    2. AGRUPADAS    : Permanecen las 5 juntas en el potrero (reset_posiciones).")
    print("    3. AISLAMIENTO  : 3 se quedan juntas; 004 y 005 van al radio de {}m y frenan.".format(args.distancia_aislado))
    print("=" * 76)
    print("", flush=True)

    animales = _init_animales()
    f_cnt = 1
    ciclos_agrupadas_restantes = args.ciclos_juntos
    todas_en_rodeo = False

    while True:
        inicio = time.time()
        exitos = errores = 0

        # Determinación de la fase actual
        if not todas_en_rodeo:
            fase_nombre = "FASE 1: CONVERGENCIA (JUNTÁNDOSE AL RODEO)"
        elif ciclos_agrupadas_restantes > 0:
            fase_nombre = f"FASE 2: RODEO AGRUPADO ({ciclos_agrupadas_restantes} ciclos restantes)"
        else:
            fase_nombre = "FASE 3: AISLAMIENTO CONTROLADO (3 EN RODEO, 2 AFUERA)"

        print("--- Ciclo {} ({}) [{}] ---".format(f_cnt, datetime.now().strftime("%H:%M:%S"), fase_nombre), flush=True)

        # 1. Enviar telemetría de cada animal
        for device_id, estado in animales.items():
            payload = _generar_payload(device_id, estado, f_cnt, session_id)
            ok, status = _enviar(payload)
            dist_centro = _distancia_base_m(estado["lat"], estado["lng"])

            if ok:
                exitos += 1
                if not todas_en_rodeo:
                    rol = "CONVERGE"
                    desc = f"a {dist_centro:4.1f}m del centro"
                elif ciclos_agrupadas_restantes > 0 or device_id in IDS_GRUPO:
                    rol = "GRUPO"
                    desc = f"en rodeo apiñado ({dist_centro:4.1f}m)"
                else:
                    rol = "SE ALEJA"
                    if dist_centro >= 100.0:
                        desc = f"AISLADO FUERA DE RADIO ({dist_centro:4.1f}m > 100m)"
                    else:
                        desc = f"alejándose ({dist_centro:4.1f}m)"

                print("  {:17s} [{:8s}] lat={:.6f} lng={:.6f} -> {}".format(
                    device_id, rol, estado["lat"], estado["lng"], desc
                ), flush=True)
            else:
                errores += 1
                print("  {:17s} ERROR {}".format(device_id, status), flush=True)

        # 2. Actualizar posiciones para el próximo ciclo
        llegaron_todas_este_ciclo = True

        for device_id, estado in animales.items():
            target_lat, target_lng = POSICIONES_RESET[device_id]

            if not todas_en_rodeo:
                # FASE 1: Caminar hacia el rodeo (POSICIONES_RESET)
                nueva_lat, nueva_lng, dist_restante = _mover_hacia(
                    estado["lat"], estado["lng"], target_lat, target_lng, VELOCIDAD_CONVERGENCIA_M
                )
                estado["lat"] = nueva_lat
                estado["lng"] = nueva_lng
                if dist_restante <= 5.0:
                    estado["en_rodeo"] = True
                else:
                    llegaron_todas_este_ciclo = False

            elif ciclos_agrupadas_restantes > 0 or device_id in IDS_GRUPO:
                # FASE 2 (todas) o FASE 3 (grupo 001..003): Pastoreo apiñado en el rodeo
                estado["heading"] += random.gauss(0, 1) * TURN_STD_GRUPO
                dlat_deg = (SPEED_PASTOREO_M * math.cos(estado["heading"])) / 111320.0
                dlng_deg = (SPEED_PASTOREO_M * math.sin(estado["heading"])) / (111320.0 * math.cos(math.radians(LAT_BASE)))
                nueva_lat = estado["lat"] + dlat_deg
                nueva_lng = estado["lng"] + dlng_deg

                # Mantener al grupo confinado en el centro (<25m de su reset)
                if _distancia_m(nueva_lat, nueva_lng, target_lat, target_lng) < 25.0:
                    estado["lat"] = nueva_lat
                    estado["lng"] = nueva_lng
                else:
                    estado["heading"] += math.pi

            else:
                # FASE 3: Animales 004 y 005 se alejan hastaargs.distancia_aislado
                dist_actual = _distancia_base_m(estado["lat"], estado["lng"])

                if dist_actual < args.distancia_aislado:
                    # Siguen avanzando en su dirección hacia el exterior
                    heading = estado["heading"] + random.gauss(0, 0.05)
                    dlat_deg = (SPEED_ALEJAMIENTO_M * math.cos(heading)) / 111320.0
                    dlng_deg = (SPEED_ALEJAMIENTO_M * math.sin(heading)) / (111320.0 * math.cos(math.radians(LAT_BASE)))
                    estado["lat"] += dlat_deg
                    estado["lng"] += dlng_deg
                else:
                    # Ya superaron el umbral y alcanzaron la distancia límite:
                    # FRENAN la deriva y pastorean suavemente en esa zona
                    estado["heading"] += random.gauss(0, 0.3)
                    dlat_deg = (SPEED_PASTOREO_M * math.cos(estado["heading"])) / 111320.0
                    dlng_deg = (SPEED_PASTOREO_M * math.sin(estado["heading"])) / (111320.0 * math.cos(math.radians(LAT_BASE)))
                    cand_lat = estado["lat"] + dlat_deg
                    cand_lng = estado["lng"] + dlng_deg

                    # Si se aleja a más de límite + 15m, regresa suavemente
                    if _distancia_base_m(cand_lat, cand_lng) <= args.distancia_aislado + 15.0:
                        estado["lat"] = cand_lat
                        estado["lng"] = cand_lng
                    else:
                        estado["heading"] += math.pi

        # Manejo de transición de fases
        if not todas_en_rodeo and llegaron_todas_este_ciclo:
            todas_en_rodeo = True
            print("\n  >>> ¡TODOS LOS ANIMALES HAN LLEGADO AL RODEO! Comenzando período de agrupación. <<<\n", flush=True)
        elif todas_en_rodeo and ciclos_agrupadas_restantes > 0:
            ciclos_agrupadas_restantes -= 1
            if ciclos_agrupadas_restantes == 0:
                print("\n  >>> FIN DEL PERÍODO DE AGRUPACIÓN: VIGIA-ARG-004 y 005 inician alejamiento controlado. <<<\n", flush=True)

        elapsed = time.time() - inicio
        espera = args.intervalo - elapsed
        if espera > 0:
            print("  Ciclo en {:.2f}s (Exitos: {}, Errores: {}). Esperando {:.1f}s...\n".format(
                elapsed, exitos, errores, espera), flush=True)
            try:
                time.sleep(espera)
            except KeyboardInterrupt:
                print("\nSimulación interrumpida por el usuario.", flush=True)
                sys.exit(0)
        else:
            print("  Ciclo en {:.2f}s (Exitos: {}, Errores: {})\n".format(elapsed, exitos, errores), flush=True)

        f_cnt += 1

if __name__ == "__main__":
    main()
