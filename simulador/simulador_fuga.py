"""
simulador_fuga.py — script de un solo uso para el Acto 3 de la demo E2E
(Sprint 10). Se corre desde la Consola Técnica (Pantalla 3) para simular que
cede el alambrado norte y dos vacas del Lote Principal se escapan juntas
(abigeato / rotura de alambrado).

═══════════════════════════════════════════════════════════════════════════════
INSTRUCCIONES PARA EL PRESENTADOR (Acto 3, Pasos 3.2 a 3.4)
═══════════════════════════════════════════════════════════════════════════════

1. Asegurarse de que el backend (perfil "dev") y simulador.py ya estén
   corriendo, y que Juan ya mostró el Mapa de Calor (Paso 3.1).

2. Paso 3.2 — en la Consola Técnica, ejecutar:

       python simulador_fuga.py

   Las dos vacas caminan juntas hasta el alambrado norte y lo cruzan en
   ~15 paquetes (~45 s con la cadencia por defecto de 3 s).

3. Paso 3.3 — apenas cada vaca supera la tolerancia del perímetro (50 m), el
   worker genera la alerta FUERA_DE_PERIMETRO en el mismo request (no espera
   a ningún job). Como las dos salen con segundos de diferencia, el panel de
   alertas las muestra agrupadas. Al abrir la alerta se ven la hora de salida
   y el contador del tiempo fuera del perímetro en vivo. Mientras están
   afuera, el script sigue mandando su posición y la alerta acompaña al
   marcador externo.

4. Paso 3.4 — cuando Pedro "va a buscarlas", presionar Enter en esta consola:
   las dos vacas vuelven a entrar por el mismo hueco del alambrado. El script
   termina solo unos paquetes después. Pedro confirma la alerta desde el
   celular con la nota "Alambrado norte reparado, animales dentro del lote".

═══════════════════════════════════════════════════════════════════════════════
DETALLE TÉCNICO
═══════════════════════════════════════════════════════════════════════════════

- Vacas por defecto: VIGIA-ARG-013 (#581 Aurora) y VIGIA-ARG-014 (#618
  Canela), dos gestantes del Lote Principal que ningún otro simulador mueve
  (simulador.py las excluye vía FUGA_DEVICE_IDS).
  Se pueden cambiar con la variable de entorno FUGA_DEVICE_IDS, pero tienen
  que ser vacas de un lote con perímetro activo.
- La geometría del perímetro está copiada de DataInitializer
  (crearPerimetroPrincipal): cuadrado de ±0.0045° alrededor del centro del
  establecimiento, tolerancia de 50 m.
- moveState se mantiene en 0-2 (baja actividad) durante toda la fuga, a
  propósito: cada paquete nuevo deja PENDIENTE la inferencia de celo y el job
  evalúa la ventana de 24 hs de la vaca. Validado offline contra
  ml-celo/app/models: caminar cientos de metros con moveState 4-7 hace que el
  modelo dispare un celo falso (P > 0.943 en esa validación), mientras que con moveState 0-2 la
  probabilidad queda en ~0.55-0.66 aun con pasos de 80 m.
- Cada corrida abre una sesión OTAA nueva (session_key_id distinto) y
  arranca f_cnt en 1, así no choca con el frame counter que dejó una corrida
  anterior (ver la idempotencia por fCnt de IngestaController).
- Cadencia propia de 3 s, ajustable con --intervalo. NO lee
  INTERVALO_SEGUNDOS: si alguien lo pone en 300 para el simulador de fondo,
  la fuga tardaría más de una hora.

Si se ensaya antes de la demo: el worker no vuelve a alertar la salida de una
vaca que ya tuvo una alerta de perímetro confirmada en las últimas 6 hs
(vigia.geofence.cooldown-hours). Reiniciar el backend (DataInitializer
resetea la base) antes de la demo real.
"""

import argparse
import math
import os
import random
import threading
import time
from datetime import datetime, timezone

import requests
# pyrefly: ignore [missing-import]
from dotenv import load_dotenv

load_dotenv()

API_URL = os.getenv("API_URL", "http://localhost:8081/api/v1/ingesta/uplink")

DEVICE_IDS = [d.strip() for d in os.getenv("FUGA_DEVICE_IDS", "VIGIA-ARG-013,VIGIA-ARG-014").split(",")
              if d.strip()]
NOMBRES = {
    "VIGIA-ARG-013": "#581 — Aurora (Holando)",
    "VIGIA-ARG-014": "#618 — Canela (Holando)",
}

# ─── Perímetro "Potrero Principal" (DataInitializer) ────────────────
LAT_CENTRO = -34.943317
LON_CENTRO = -59.225728
DELTA_PERIMETRO = 0.0045
TOLERANCIA_M = 50
ALAMBRADO_NORTE_LAT = LAT_CENTRO + DELTA_PERIMETRO

# Dónde quedan las vacas en cada fase, relativo al alambrado norte (en grados de latitud).
ANTES_DEL_ALAMBRADO = 0.0002      # ~20 m adentro: se juntan frente al hueco
DESTINO_AFUERA = 0.0013           # ~145 m afuera: bien pasada la tolerancia de 50 m
DESTINO_REGRESO = 0.0008          # ~90 m adentro al volver
SEPARACION_ENTRE_VACAS = 0.00006  # ~6 m, para que los dos marcadores no se tapen

# Primero cada vaca camina hasta el hueco (la que llega antes espera a la otra)
# y después cruzan el alambrado juntas, con la misma cantidad de paquetes.
PAQUETES_ACERCAMIENTO = 10
PAQUETES_CRUCE = 5
PAQUETES_DENTRO_AL_FINAL = 4
# Paso máximo por paquete validado contra el modelo de celo (ver docstring).
PASO_MAXIMO_GRADOS = 0.0008
MOVESTATE_FUGA = (0, 2)
JITTER_AFUERA = 0.00002

TIMEOUT_SEGUNDOS = 5
# ─────────────────────────────────────────────────────────────────────────────


def metros_fuera_del_alambrado(lat):
    return (lat - ALAMBRADO_NORTE_LAT) * 111_320


def obtener_posicion_inicial(device_id):
    """Continúa la última posición real que el backend tiene para la vaca: un
    salto de posición desconectado de la última fila real dispara celos falsos
    (ver obtener_posicion_inicial en simulador.py)."""
    base_url = API_URL.rsplit("/api/v1/", 1)[0]
    url = f"{base_url}/api/v1/ingesta/ultima-posicion/{device_id}"
    try:
        resp = requests.get(url, timeout=TIMEOUT_SEGUNDOS)
        if resp.status_code == 200:
            data = resp.json()
            return data["latitud"], data["longitud"], data.get("bateria") or 100.0
    except Exception as e:
        print(f"AVISO: no se pudo consultar la última posición de {device_id} ({e}).")

    print(f"AVISO: {device_id} no tiene posición previa en el backend; "
          f"arrancando en una posición random dentro del potrero.")
    return (
        LAT_CENTRO + random.uniform(-0.002, 0.002),
        LON_CENTRO + random.uniform(-0.002, 0.002),
        random.uniform(80.0, 100.0),
    )


def recorrido(desde, waypoints, paquetes_minimos):
    """Puntos equiespaciados a lo largo de la poligonal desde -> waypoints.
    Si con `paquetes_minimos` el paso supera PASO_MAXIMO_GRADOS, agrega
    paquetes en vez de agrandar el paso."""
    tramos = [desde] + waypoints
    largos = [math.dist(tramos[i], tramos[i + 1]) for i in range(len(tramos) - 1)]
    total = sum(largos)
    n = max(paquetes_minimos, math.ceil(total / PASO_MAXIMO_GRADOS))
    puntos = []
    for k in range(1, n + 1):
        objetivo = total * k / n
        acumulado = 0.0
        for i, largo in enumerate(largos):
            if acumulado + largo >= objetivo or i == len(largos) - 1:
                t = (objetivo - acumulado) / largo if largo else 1.0
                a, b = tramos[i], tramos[i + 1]
                puntos.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
                break
            acumulado += largo
    return puntos


def rutas_juntas(estados, encuentro, destino, paquetes_acercamiento):
    """Arma en e["ruta"] el acercamiento de cada vaca a su punto de encuentro
    y el cruce hasta su destino, sincronizados entre todas las vacas."""
    for e in estados.values():
        e["ruta"] = recorrido((e["lat"], e["lon"]), [encuentro(e)], paquetes_acercamiento)
    largo_max = max(len(e["ruta"]) for e in estados.values())
    for e in estados.values():
        e["ruta"] = ([e["ruta"][0]] * (largo_max - len(e["ruta"])) + e["ruta"]
                     + recorrido(encuentro(e), [destino(e)], PAQUETES_CRUCE))


def armar_payload(device_id, estado, sesion):
    estado["f_cnt"] += 1
    estado["bateria"] = max(5.0, estado["bateria"] - random.uniform(0.01, 0.05))
    return {
        "end_device_ids": {"dev_eui": device_id},
        "uplink_message": {
            "received_at": datetime.now(timezone.utc).isoformat(),
            "f_cnt": estado["f_cnt"],
            "session_key_id": sesion,
            "decoded_payload": {
                "type": "GNSSPosition",
                "latitude": round(estado["lat"], 6),
                "longitude": round(estado["lon"], 6),
                "moveState": random.randint(*MOVESTATE_FUGA),
                "battery": f"{round(estado['bateria'], 1)}%",
            },
        },
    }


def esperar_enter(evento):
    try:
        input()
    except EOFError:
        return
    evento.set()


def main():
    parser = argparse.ArgumentParser(description="Acto 3 — fuga de dos vacas por el alambrado norte.")
    parser.add_argument("--intervalo", type=float, default=3.0,
                        help="Segundos entre paquetes (default 3).")
    args = parser.parse_args()

    print("=" * 70)
    print("  SIMULADOR DE FUGA / ABIGEATO — DEMO E2E (Acto 3, Pasos 3.2 a 3.4)")
    print("=" * 70)
    for d in DEVICE_IDS:
        print(f"  Vaca          : {NOMBRES.get(d, d)}  [{d}]")
    print(f"  Perímetro     : Potrero Principal (tolerancia {TOLERANCIA_M} m)")
    print(f"  Target API    : {API_URL}")
    print(f"  Cadencia      : {args.intervalo:g} s")
    print("=" * 70)

    estados = {}
    for d in DEVICE_IDS:
        lat, lon, bateria = obtener_posicion_inicial(d)
        estados[d] = {"lat": lat, "lon": lon, "bateria": bateria, "f_cnt": 0, "afuera": False}
        print(f"  {d}: arranca en lat={lat:.6f} lon={lon:.6f} "
              f"({-metros_fuera_del_alambrado(lat):.0f} m al sur del alambrado norte)")

    # Hueco del alambrado: a la altura media de las vacas, sin pegarse a las esquinas.
    margen = 0.0005
    lon_hueco = sum(e["lon"] for e in estados.values()) / len(estados)
    lon_hueco = min(max(lon_hueco, LON_CENTRO - DELTA_PERIMETRO + margen), LON_CENTRO + DELTA_PERIMETRO - margen)

    for i, e in enumerate(estados.values()):
        e["lon_propia"] = lon_hueco + (i - (len(estados) - 1) / 2) * SEPARACION_ENTRE_VACAS
    rutas_juntas(
        estados,
        encuentro=lambda e: (ALAMBRADO_NORTE_LAT - ANTES_DEL_ALAMBRADO, e["lon_propia"]),
        destino=lambda e: (ALAMBRADO_NORTE_LAT + DESTINO_AFUERA, e["lon_propia"]),
        paquetes_acercamiento=PAQUETES_ACERCAMIENTO,
    )

    headers = {}
    api_key = os.getenv("TTN_WEBHOOK_API_KEY")
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    sesion = f"fuga_{int(time.time())}"

    regresar = threading.Event()
    fase = "escape"
    dentro_restantes = PAQUETES_DENTRO_AL_FINAL

    print(f"\n[{datetime.now():%H:%M:%S}] Paso 3.2 — las vacas se dirigen al alambrado norte...\n")

    while True:
        inicio = time.time()

        if fase == "afuera" and regresar.is_set():
            fase = "regreso"
            print(f"\n[{datetime.now():%H:%M:%S}] Paso 3.4 — Pedro las arrea de vuelta al lote...\n")
            rutas_juntas(
                estados,
                encuentro=lambda e: (ALAMBRADO_NORTE_LAT + ANTES_DEL_ALAMBRADO, e["lon_propia"]),
                destino=lambda e: (ALAMBRADO_NORTE_LAT - DESTINO_REGRESO, e["lon_propia"]),
                paquetes_acercamiento=PAQUETES_CRUCE,
            )

        for d, e in estados.items():
            if fase in ("escape", "regreso") and e["ruta"]:
                e["lat"], e["lon"] = e["ruta"].pop(0)
            else:
                # Pastoreo quieto en el lugar (afuera o ya de vuelta adentro).
                e["lat"] += random.gauss(0, JITTER_AFUERA)
                e["lon"] += random.gauss(0, JITTER_AFUERA)

            payload = armar_payload(d, e, sesion)
            fuera_m = metros_fuera_del_alambrado(e["lat"])
            try:
                resp = requests.post(API_URL, json=payload, headers=headers, timeout=TIMEOUT_SEGUNDOS)
                respuesta = resp.text.strip()
                ok = resp.status_code in (200, 201, 202) and not respuesta.startswith("Ignorado")
                print(f"[{datetime.now():%H:%M:%S}] {'OK ' if ok else 'ERR'} {d} "
                      f"lat={e['lat']:.6f} lon={e['lon']:.6f} "
                      f"{'afuera' if fuera_m > 0 else 'adentro'} {abs(fuera_m):5.0f} m"
                      + ("" if ok else f"  -> HTTP {resp.status_code}: {respuesta}"))
            except requests.exceptions.RequestException as ex:
                print(f"[{datetime.now():%H:%M:%S}] ERR {d}: {ex}")

            superada = fuera_m > TOLERANCIA_M
            if superada and not e["afuera"]:
                print(f"  -> {NOMBRES.get(d, d)} superó la tolerancia de {TOLERANCIA_M} m: "
                      f"alerta FUERA_DE_PERIMETRO generada (Paso 3.3).")
            elif not superada and e["afuera"] and fase == "regreso":
                print(f"  -> {NOMBRES.get(d, d)} volvió dentro del perímetro.")
            e["afuera"] = superada

        if fase == "escape" and all(not e["ruta"] for e in estados.values()):
            fase = "afuera"
            print(f"\n[{datetime.now():%H:%M:%S}] Las dos vacas están afuera. "
                  f"El script sigue mandando su posición.")
            print(">>> Presionar Enter cuando Pedro vaya a buscarlas (Paso 3.4). Ctrl+C para cortar. <<<\n")
            threading.Thread(target=esperar_enter, args=(regresar,), daemon=True).start()
        elif fase == "regreso" and all(not e["ruta"] for e in estados.values()):
            dentro_restantes -= 1
            if dentro_restantes <= 0:
                print(f"\n[{datetime.now():%H:%M:%S}] Las dos vacas están de nuevo en el Lote Principal.")
                print("  -> Pedro ya puede confirmar la alerta con la nota: "
                      "'Alambrado norte reparado, animales dentro del lote'.")
                break

        espera = args.intervalo - (time.time() - inicio)
        if espera > 0:
            time.sleep(espera)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nCortado por el presentador.")
