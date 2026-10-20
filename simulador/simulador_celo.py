"""
simulador_celo.py — script de un solo uso para el Acto 2 de la demo E2E
(Sprint 10). Se corre desde la Consola Técnica (Pantalla 3) para desencadenar
el evento de celo de la vaca #104 "La Vaca Lola" (VIGIA-ARG-001) en vivo.

═══════════════════════════════════════════════════════════════════════════════
INSTRUCCIONES PARA EL PRESENTADOR (Acto 2, Paso 2.1)
═══════════════════════════════════════════════════════════════════════════════

1. Asegurarse de que el backend (perfil "dev") y el simulador_demo.py ya
   estén corriendo (Acto 1 completado).

2. En la Consola Técnica, ejecutar:

       python simulador_celo.py

3. Observar en la consola los logs de confianza de celo. El job batch corre
   cada 60 s; la alerta de celo de la vaca #104 debería aparecer en el
   dashboard dentro del próximo minuto o dos.

4. En el mapa (sin recargar la página) el marcador de la vaca #104 cambia a
   Alerta Crítica (Rojo) y el resumen numérico superior suma +1 en alertas
   reproductivas (Paso 2.2).

5. Dejar corriendo este script hasta que la demo del Acto 2 termine (Paso 2.4
   — Pedro confirma el celo e insemina). Después, Ctrl+C para apagarlo.

═══════════════════════════════════════════════════════════════════════════════
DETALLE TÉCNICO
═══════════════════════════════════════════════════════════════════════════════

- La vaca #104 es "La Vaca Lola", device_id VIGIA-ARG-001.
- DataInitializer ya sembró al arrancar el backend las 288 filas (24 hs)
  de historial con patrón de celo para VIGIA-ARG-001, marcadas como ya
  inferidas (EstadoInferencia.COMPLETADA). Este script NO inyecta ese
  historial — solo manda, en vivo, unos pocos puntos nuevos con ese mismo
  patrón (moveState 7-9, velocidad 2.5-4× la base).
- Alcanza con que lleguen unos pocos puntos PENDIENTES para que el próximo
  ciclo del job de celo (BatchScheduler, cada 60 s) evalúe la ventana
  completa de 24 hs —ya con forma de celo de punta a punta— y dispare la
  alerta (confianza por encima del umbral CELO_THRESHOLD, 0.943 en infra).
- La vaca sale de FORZAR_CELO_DEVICE_ID (default VIGIA-ARG-001), la misma
  variable con la que simulador_demo.py la excluye de su loop: nunca hay dos
  procesos mandando para el mismo device_id.
- Cada corrida abre una sesión OTAA nueva (session_key_id distinto) y arranca
  f_cnt en 1. Antes arrancaba siempre en 90001: en una segunda corrida sin
  reiniciar el backend, el worker descartaba todos los paquetes por
  idempotencia ("Ignorado: frame duplicado") mientras el script decía OK.
  Ahora, si el worker ignora un paquete, se avisa en la consola con el motivo.
- Si #104 ya tuvo una alerta de celo en las últimas 24 hs (por ejemplo, de un
  ensayo), el job no genera otra. Reiniciar el backend antes de la demo para
  que DataInitializer resetee la base.
- TICK_MODELADO_SEGUNDOS (300 s) e INTERVALO_SEGUNDOS (5 s) están
  desacoplados: el modelo de celo no usa el timestamp como feature (solo
  moveState y distancia geométrica), así que se puede mandar rápido sin
  perder confianza. El timestamp que se manda es siempre la hora real.
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

# ─── Parámetros de la demo ────────────────────────────────────────────────────
# Los parámetros de movimiento están calibrados contra ml-celo y el historial
# que siembra DataInitializer: NO deben cambiarse.

API_URL = os.getenv("API_URL", "http://localhost:8081/api/v1/ingesta/uplink")

# Vaca #104, "La Vaca Lola". Misma variable que usa simulador_demo.py para
# excluirla de su loop, así los dos procesos nunca mandan para la misma vaca.
DEVICE_ID = os.getenv("FORZAR_CELO_DEVICE_ID", "VIGIA-ARG-001")
NOMBRE_VACA = "#104 — La Vaca Lola (Holando)" if DEVICE_ID == "VIGIA-ARG-001" else DEVICE_ID

# Cadencia real de envío. 5 s está bien para una demo en vivo: la vaca se ve
# moverse en el mapa y el job de celo recibe puntos PENDIENTES pronto.
INTERVALO_SEGUNDOS = int(os.getenv("INTERVALO_SEGUNDOS", 5))

# Escala de movimiento que "ve" el modelo por paquete: un tick de 5 minutos
# (300 s), igual que ml-training/celo/generate_dataset.py. Fija.
TICK_MODELADO_SEGUNDOS = 300

# Coordenadas de fallback (establecimiento "Tambo La Esperanza" del seed demo).
LAT_INICIAL = -34.943317
LON_INICIAL = -59.225728

# Parámetros de celo calibrados contra generate_dataset.py (no modificar):
SPEED_BASE_DEG_POR_TICK_5MIN = 0.000018
SPEED_MULT_CELO_MIN = 2.5
SPEED_MULT_CELO_MAX = 4.0
TURN_STD_CELO = 1.1        # trayectoria errática durante celo
MOVESTATE_ALTA_ACTIVIDAD = (7, 9)

TIMEOUT_POSICION_SEGUNDOS = 3
# ─────────────────────────────────────────────────────────────────────────────


def obtener_posicion_inicial():
    """Continúa la última posición/batería real que el backend ya tiene para
    VIGIA-ARG-001 (sembrada por DataInitializer o dejada por una corrida
    anterior de este script), en vez de arrancar en un punto desconectado.

    BUGFIX documentado en simulador.py: un salto de posición desconectado de
    la última fila real dispara una alerta de celo falsa porque el job lee ese
    salto como un desplazamiento anómalo entre puntos consecutivos de la
    ventana de 24 hs.
    """
    base_url = API_URL.rsplit("/api/v1/", 1)[0]
    url = f"{base_url}/api/v1/ingesta/ultima-posicion/{DEVICE_ID}"
    try:
        resp = requests.get(url, timeout=TIMEOUT_POSICION_SEGUNDOS)
        if resp.status_code == 200:
            data = resp.json()
            return data["latitud"], data["longitud"], data.get("bateria") or 100.0
    except Exception as e:
        print(f"AVISO: no se pudo consultar la última posición de {DEVICE_ID} ({e}).")

    print(f"AVISO: {DEVICE_ID} no tiene posición previa en el backend; "
          f"arrancando en una posición random cerca del establecimiento.")
    return (
        LAT_INICIAL + random.uniform(-0.003, 0.003),
        LON_INICIAL + random.uniform(-0.003, 0.003),
        random.uniform(80.0, 100.0),
    )


def generar_payload(estado, sesion):
    """Genera un paquete TTN v3 con patrón de CELO para DEVICE_ID.

    El backend tiene idempotencia por frame counter (fCnt): dentro de una misma
    sesión, un fCnt menor o igual al último guardado se ignora aunque la
    respuesta HTTP sea 200. Cada corrida usa un session_key_id nuevo, que el
    worker trata como un rejoin OTAA, así que f_cnt puede arrancar en 1.
    """
    estado["bateria"] -= random.uniform(0.01, 0.05)
    if estado["bateria"] < 0:
        estado["bateria"] = 100.0

    move_state = random.randint(*MOVESTATE_ALTA_ACTIVIDAD)
    speed_mult = random.uniform(SPEED_MULT_CELO_MIN, SPEED_MULT_CELO_MAX)

    # Magnitud de paso fija a la escala de un tick de 5 min: no se re-escala
    # según INTERVALO_SEGUNDOS (ver docstring del módulo).
    estado["heading"] += random.gauss(0, 1) * TURN_STD_CELO
    speed = SPEED_BASE_DEG_POR_TICK_5MIN * speed_mult
    estado["lat"] += speed * math.cos(estado["heading"])
    estado["lon"] += speed * math.sin(estado["heading"])
    estado["paquetes"] = estado.get("paquetes", 0) + 1
    estado["f_cnt"] = estado.get("f_cnt", 0) + 1

    return {
        "end_device_ids": {
            "dev_eui": DEVICE_ID
        },
        "uplink_message": {
            "received_at": datetime.now(timezone.utc).isoformat(),
            "f_cnt": estado["f_cnt"],
            "session_key_id": sesion,
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
    # ─── Banner de la consola técnica ──────────────────────────────────────
    print("=" * 70)
    print("  SIMULADOR DE CELO — DEMO E2E (Acto 2, Paso 2.1)")
    print("=" * 70)
    print(f"  Vaca objetivo : {NOMBRE_VACA}")
    print(f"  Device ID     : {DEVICE_ID}")
    print(f"  Target API    : {API_URL}")
    print(f"  Cadencia      : {INTERVALO_SEGUNDOS} s (ritmo de demo)")
    print(f"  Detección     : job de celo cada 60 s (umbral de confianza 0.943)")
    print()
    print("  El historial de 24 hs con patrón de celo ya está persistido por")
    print("  DataInitializer. Este script solo manda los puntos nuevos")
    print("  (PENDIENTES) que activan al job de celo.")
    print()
    print("  → La alerta aparecerá en el dashboard dentro del próximo minuto.")
    print("  → El marcador de la vaca #104 en el mapa cambiará a Rojo.")
    print("  → Ctrl+C para apagar cuando el Acto 2 finalice.")
    print("=" * 70)

    lat_ini, lon_ini, bateria_ini = obtener_posicion_inicial()
    estado = {
        "lat": lat_ini,
        "lon": lon_ini,
        "bateria": bateria_ini,
        "heading": random.uniform(0, 2 * math.pi),
        "paquetes": 0,
    }
    print(f"\n[{datetime.now().isoformat()}] Posición inicial: "
          f"lat={lat_ini:.6f}, lon={lon_ini:.6f}, batería={bateria_ini:.1f}%")
    print(f"[{datetime.now().isoformat()}] Arrancando loop en vivo...\n")

    headers = {}
    api_key = os.getenv("TTN_WEBHOOK_API_KEY")
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    sesion = f"celo_{int(time.time())}"

    while True:
        inicio_ciclo = time.time()
        payload = generar_payload(estado, sesion)

        try:
            response = requests.post(API_URL, json=payload, headers=headers, timeout=5)
            decoded = payload["uplink_message"]["decoded_payload"]
            paquetes = estado["paquetes"]

            respuesta = response.text.strip()
            if response.status_code in [200, 201, 202] and respuesta.startswith("Ignorado"):
                # El worker contesta 200 aunque descarte el paquete: sin este aviso
                # el script parece andar y la alerta nunca aparece.
                print(f"[{datetime.now().isoformat()}] AVISO PKT #{paquetes:03d} — "
                      f"el worker NO guardó el paquete: {respuesta}")
            elif response.status_code in [200, 201, 202]:
                # Log detallado para el presentador
                print(
                    f"[{datetime.now().isoformat()}] OK PKT #{paquetes:03d} — "
                    f"lat={decoded['latitude']} lon={decoded['longitude']} "
                    f"moveState={decoded['moveState']} "
                    f"bat={decoded['battery']}"
                )
                if paquetes == 1:
                    print("  -> Primer paquete PENDIENTE enviado. El job de celo lo evaluara "
                          "junto con las 24 hs de historial en el proximo ciclo (<=60 s).")
                elif paquetes == 3:
                    print("  -> La alerta deberia haber aparecido ya. "
                          "Si no, esperar el proximo ciclo del job.")
            else:
                print(f"[{datetime.now().isoformat()}] ERROR {response.status_code}: "
                      f"{response.text}")
        except requests.exceptions.RequestException as e:
            print(f"[{datetime.now().isoformat()}] Excepcion de conexion: {e}")

        tiempo_transcurrido = time.time() - inicio_ciclo
        tiempo_espera = INTERVALO_SEGUNDOS - tiempo_transcurrido
        if tiempo_espera > 0:
            time.sleep(tiempo_espera)


if __name__ == "__main__":
    main()
