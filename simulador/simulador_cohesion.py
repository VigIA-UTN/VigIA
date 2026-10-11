"""
simulador_cohesion.py — Simula escenarios de cohesión de rodeo enviando
telemetría al backend (igual que simulador_demo.py), de modo que las alertas
de aislamiento aparezcan en el dashboard/mapa.

NO llama a ml-geo directamente: manda coordenadas GPS al endpoint de ingesta
del backend, y el job batch de ml-geo las procesa normalmente.

Escenarios forzados (configurables por env var):
  - GRUPO (VIGIA-ARG-001 y 002):  random walk ajustado, se mantienen juntos.
  - AISLADO (VIGIA-ARG-003):      deriva en dirección fija lejos del grupo.
  - SUBGRUPO (VIGIA-ARG-004/005): ambos derivan juntos en otra dirección.

Uso:
  py simulador_cohesion.py                    # usa .env
  py simulador_cohesion.py --intervalo 5      # ciclo cada 5 s (demo rápida)
  py simulador_cohesion.py --escenario grupo  # solo el grupo (sin alertas)

Variables de entorno:
  API_URL                URL del backend worker (default: http://localhost:8081/api/v1/ingesta/uplink)
  TTN_WEBHOOK_API_KEY    API key del webhook TTN
  INTERVALO_SEGUNDOS     Segundos entre ciclos de envío (default: 5)
  FORZAR_AISLADO_ID      Device ID del animal aislado (default: VIGIA-ARG-003)
  FORZAR_SUBGRUPO_IDS    Device IDs del subgrupo, separados por coma (default: VIGIA-ARG-004,VIGIA-ARG-005)
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
# Configuración
# ---------------------------------------------------------------------------

API_URL = os.getenv("API_URL", "http://localhost:8081/api/v1/ingesta/uplink")
INTERVALO_SEGUNDOS = int(os.getenv("INTERVALO_SEGUNDOS", 5))
TIMEOUT_ULTIMA_POSICION = 3

# Coordenadas base: Establecimiento de Prueba (igual que el resto de simuladores)
LAT_BASE = -34.943317
LNG_BASE = -59.225728

# ---------------------------------------------------------------------------
# Device IDs de cada rol
# ---------------------------------------------------------------------------

# Animales que forman el GRUPO PRINCIPAL (3 animales juntos, se mantienen apiñados)
IDS_GRUPO = ["VIGIA-ARG-001", "VIGIA-ARG-002", "VIGIA-ARG-003"]

# Los 2 animales que se van / alejan del grupo
IDS_ALEJADOS = ["VIGIA-ARG-004", "VIGIA-ARG-005"]

TODOS_LOS_IDS = IDS_GRUPO + IDS_ALEJADOS

# ---------------------------------------------------------------------------
# Parámetros de movimiento
# ---------------------------------------------------------------------------

# Escala base: igual que generate_dataset.py / simulador_demo.py
# Un "tick" representa 5 min de movimiento real (~2 m por tick en pastoreo).
SPEED_BASE_DEG = 0.000018   # grados por tick de 5 min en pastoreo normal

# El grupo se mueve con velocidad reducida y heading persistente
# (pastoreo lento) para mantenerse apiñado (< 30 m entre ellos).
SPEED_GRUPO   = SPEED_BASE_DEG * 0.4
TURN_STD_GRUPO = 0.15

# Los animales que se alejan van a velocidad media hacia afuera.
# Superan los 100 m de umbral en pocos ciclos.
SPEED_ALEJADOS = SPEED_BASE_DEG * 10.0

# moveState normal
MOVESTATE_NORMAL = (2, 6)

# ---------------------------------------------------------------------------
# Estado de los animales (posición + heading)
# ---------------------------------------------------------------------------

def _init_animales():
    animales = {}
    # Grupo de 3 apiñados en el centro (a menos de 20m entre sí)
    offsets_grupo = [
        (0.00008, 0.00008),
        (-0.00008, -0.00005),
        (0.00002, -0.00008),
    ]
    for idx, dev_id in enumerate(IDS_GRUPO):
        dlat, dlng = offsets_grupo[idx]
        animales[dev_id] = {
            "lat": LAT_BASE + dlat,
            "lng": LNG_BASE + dlng,
            "bateria": 100.0,
            "heading": random.uniform(0, 2 * math.pi),
        }

    # Los 2 que se van: arrancan cerca y derivan en direcciones opuestas
    animales["VIGIA-ARG-004"] = {
        "lat": LAT_BASE + 0.00015,
        "lng": LNG_BASE + 0.00005,
        "bateria": 100.0,
        "heading": math.pi / 2,     # se va hacia el Norte
    }
    animales["VIGIA-ARG-005"] = {
        "lat": LAT_BASE - 0.00015,
        "lng": LNG_BASE - 0.00005,
        "bateria": 100.0,
        "heading": -math.pi / 2,    # se va hacia el Sur
    }
    return animales


# ---------------------------------------------------------------------------
# Generación de payload TTN (mismo formato que simulador_demo.py)
# ---------------------------------------------------------------------------

def _generar_payload(device_id, estado, escenario_activo, f_cnt):
    # Consumo de batería
    estado["bateria"] -= random.uniform(0.01, 0.05)
    if estado["bateria"] < 0:
        estado["bateria"] = 100.0

    if not escenario_activo or device_id in IDS_GRUPO:
        # ---- Grupo principal (3 animales): pastoreo apiñado ----
        estado["heading"] += random.gauss(0, 1) * TURN_STD_GRUPO
        speed = SPEED_GRUPO
        estado["lat"] += speed * math.cos(estado["heading"])
        estado["lng"] += speed * math.sin(estado["heading"])

    elif device_id == "VIGIA-ARG-004":
        # ---- Animal 4: se va hacia el Norte ----
        estado["heading"] = math.pi / 2 + random.gauss(0, 0.05)
        speed = SPEED_ALEJADOS
        estado["lat"] += speed * math.cos(estado["heading"])
        estado["lng"] += speed * math.sin(estado["heading"])

    elif device_id == "VIGIA-ARG-005":
        # ---- Animal 5: se va hacia el Sur ----
        estado["heading"] = -math.pi / 2 + random.gauss(0, 0.05)
        speed = SPEED_ALEJADOS
        estado["lat"] += speed * math.cos(estado["heading"])
        estado["lng"] += speed * math.sin(estado["heading"])

    move_state = random.randint(*MOVESTATE_NORMAL)

    return {
        "end_device_ids": {"dev_eui": device_id},
        "uplink_message": {
            "f_cnt": f_cnt,
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


# ---------------------------------------------------------------------------
# Envío al backend
# ---------------------------------------------------------------------------

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
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Simulador de cohesión: envía telemetría al backend forzando escenarios de aislamiento."
    )
    parser.add_argument(
        "--intervalo",
        type=int,
        default=INTERVALO_SEGUNDOS,
        help="Segundos entre ciclos de envío (default: {})".format(INTERVALO_SEGUNDOS),
    )
    parser.add_argument(
        "--escenario",
        choices=["completo", "grupo"],
        default="completo",
        help=(
            "completo (default): grupo normal + animal aislado + subgrupo alejado. "
            "grupo: solo el grupo cohesionado, sin alertas."
        ),
    )
    args = parser.parse_args()

    escenario_activo = args.escenario == "completo"

    print("")
    print("=" * 68)
    print("  VigIA - Simulador de Cohesion")
    print("=" * 68)
    print("  Backend   : {}".format(API_URL))
    print("  Intervalo : {} s".format(args.intervalo))
    print("  Escenario : {}".format(args.escenario))
    print("")
    if escenario_activo:
        print("  Roles:")
        print("    GRUPO PRINCIPAL : {} (pastoreo apiñado, se mantienen juntos)".format(IDS_GRUPO))
        print("    SE ALEJAN       : {} (004 al norte, 005 al sur)".format(IDS_ALEJADOS))
        print("")
        print("  Las alertas deberían aparecer en el dashboard en cuanto el")
        print("  job de ml-geo procese la ventana (~1-2 min después del arranque).")
    else:
        print("  Modo 'grupo': todos los animales se comportan normalmente.")
        print("  No se esperan alertas de aislamiento.")
    print("=" * 68)
    print("")

    animales = _init_animales()

    f_cnt = 1
    while True:
        inicio = time.time()
        exitos = errores = 0

        print("--- Ciclo {} ({}) ---".format(f_cnt, datetime.now().strftime("%H:%M:%S")))

        for device_id, estado in animales.items():
            payload = _generar_payload(device_id, estado, escenario_activo, f_cnt)
            ok, status = _enviar(payload)
            if ok:
                exitos += 1
                rol = (
                    "GRUPO"     if device_id in IDS_GRUPO else
                    "SE ALEJA"
                )
                print("  {:17s} [{:8s}] lat={:.6f} lng={:.6f}".format(
                    device_id, rol,
                    estado["lat"], estado["lng"],
                ))
            else:
                errores += 1
                print("  {:17s} ERROR {}".format(device_id, status))

        elapsed = time.time() - inicio
        print("  Ciclo completado en {:.2f}s. Exitos: {}, Errores: {}".format(
            elapsed, exitos, errores))

        espera = args.intervalo - elapsed
        if espera > 0:
            print("  Esperando {:.1f}s...\n".format(espera))
            try:
                time.sleep(espera)
            except KeyboardInterrupt:
                print("\nInterrumpido por el usuario.")
                sys.exit(0)
        else:
            print("")

        f_cnt += 1


if __name__ == "__main__":
    main()
