import os
import requests
from datetime import datetime, timezone

import time

API_URL = os.getenv("API_URL", "http://localhost:8081/api/v1/ingesta/uplink")
LAT_BASE = -34.943317
LNG_BASE = -59.225728

# Las 5 vacas vuelven a estar juntas en el establecimiento (a menos de 10-15 metros entre sí)
ANIMALES_RESET = [
    ("VIGIA-ARG-001", LAT_BASE + 0.00003, LNG_BASE + 0.00003),
    ("VIGIA-ARG-002", LAT_BASE - 0.00003, LNG_BASE - 0.00002),
    ("VIGIA-ARG-003", LAT_BASE + 0.00001, LNG_BASE - 0.00004),
    ("VIGIA-ARG-004", LAT_BASE - 0.00002, LNG_BASE + 0.00004),
    ("VIGIA-ARG-005", LAT_BASE + 0.00004, LNG_BASE - 0.00002),
]

def reset_posiciones():
    session_id = f"reset_{int(time.time())}"
    print(f"Enviando telemetría de reseteo para reagrupar a los animales (nueva sesión OTAA: {session_id})...")
    for dev_id, lat, lng in ANIMALES_RESET:
        payload = {
            "end_device_ids": {"dev_eui": dev_id},
            "uplink_message": {
                "f_cnt": 1,
                "session_key_id": session_id,
                "received_at": datetime.now(timezone.utc).isoformat(),
                "decoded_payload": {
                    "type": "GNSSPosition",
                    "latitude": round(lat, 6),
                    "longitude": round(lng, 6),
                    "moveState": 4,
                    "battery": "100.0%",
                },
            },
        }
        headers = {"Authorization": "Bearer apiKey"}
        try:
            resp = requests.post(API_URL, json=payload, headers=headers, timeout=5)
            print(f"  {dev_id}: posicionado en ({lat:.6f}, {lng:.6f}) -> HTTP {resp.status_code}")
        except Exception as e:
            print(f"  {dev_id}: ERROR {e}")

    print("\n¡Listo! Todos los animales fueron devueltos a la posición inicial en el establecimiento.")

if __name__ == "__main__":
    reset_posiciones()
