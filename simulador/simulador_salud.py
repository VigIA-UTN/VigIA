"""
simulador_salud.py — script de un solo uso para el Acto 4 de la demo E2E
(Sprint 10), el "trigger_salud_aislamiento" del guion. Se corre desde la
Consola Técnica (Pantalla 3): la vaca #208 "Margarita" (VIGIA-ARG-002), que
viene incubando una infección, hoy se movió ~40 % menos y se quedó atrás,
apartada del rodeo del Lote Principal.

═══════════════════════════════════════════════════════════════════════════════
INSTRUCCIONES PARA EL PRESENTADOR (Acto 4, Paso 4.1)
═══════════════════════════════════════════════════════════════════════════════

1. Asegurarse de que el backend (perfil "dev", reiniciado antes de la demo)
   y simulador.py ya estén corriendo.

2. Paso 4.1 — en la Consola Técnica, ejecutar:

       python simulador_salud.py

   La caravana de la #208 vuelve a transmitir: pastorea despacio, sola, a
   ~180 m del rodeo.

3. En el próximo ciclo de cada job (60 s con la config de demo) aparecen las
   dos alertas de la #208:
   - SALUD (desvío sanitario, severidad MEDIA): hoy se movió ~40 % menos que
     su perfil de referencia.
   - AISLAMIENTO_SOCIAL: está a más de 100 m del rodeo hace más de 15 min.
   Laura (veterinaria) las atiende desde el celular (Pasos 4.2 a 4.4).

4. Dejar el script corriendo hasta el final del Acto 4 y cortarlo con Ctrl+C.

═══════════════════════════════════════════════════════════════════════════════
DETALLE TÉCNICO
═══════════════════════════════════════════════════════════════════════════════

Igual que el celo de la #104, la historia vive en el seed (DataInitializer,
sembrarHistorialMargarita) y este script solo manda los puntos PENDIENTES que
despiertan a los jobs:
- 8 días a 5 min: los primeros 7 son la calibración del perfil de salud
  (>= 2016 puntos) y las últimas 24 hs traen la caída de actividad
  (moveState 1-3 en vez de 2-5). Validado contra ml-salud con las filas del
  seed: 24 hs normales no alertan y estas, más los puntos de este script,
  alertan con severidad MEDIA.
- El historial termina apartada del rodeo (~180 m al oeste) y
  MINUTOS_SIN_DATOS_SEED (35) minutos antes del arranque del backend. ml-geo
  solo mira animales con posición de los últimos 30 min, así que no la ve
  hasta que este script vuelve a mandar; y como su última hora sembrada ya
  es "fuera del grupo", el aislamiento supera de entrada los
  VIGIA_GEO_MINUTOS_FUERA_MINIMO (15) minutos que exige el worker.
- Para que eso no dependa de cuánto tarda la demo en llegar al Acto 4, el
  script primero completa, con su hora real, los intervalos de 5 min entre su
  último dato sembrado y el final del seed (hasta 5 min antes del arranque),
  donde el seed sí tiene al rodeo: ml-geo mide el tiempo fuera del grupo en
  intervalos de 5 min y un intervalo con la #208 sola corta el tramo. Los
  intervalos sin datos de la #208 no suman ni cortan. Con
  VIGIA_GEO_VENTANA_HORAS=1 esto alcanza si el Acto 4 cae dentro de ~25 min
  del reinicio del backend; con 24 (default del compose), siempre.
- El mismo relleno repone los puntos sembrados que ya salieron de la ventana
  de 24 hs: el job de salud exige 288 puntos y, con menos, espera al próximo
  ciclo (verificado en Docker: a los 11 min del reinicio faltaban 2 y la
  alerta tardó un ciclo más).
- La #208 no se aleja dentro de la ventana de salud: caminar ~150 m dentro de
  las 24 hs evaluadas le suma tanto desplazamiento que ml-salud deja de ver
  la caída (verificado en Docker: severidad 0 durante 10 ciclos). Pastorea
  como en su día de enferma del seed (moveState 1-3, pasos de ~2 m): con
  hasta ~24 puntos del script en la ventana, la severidad es MEDIA.
- Si el rodeo se le acerca a menos de DISTANCIA_MINIMA_M, se aleja despacio
  (~3 m por paquete) sin salir del potrero.
- El worker no vuelve a alertar salud de una vaca que ya tuvo una alerta de
  salud en las últimas 24 hs: reiniciar el backend antes de la demo real.
- Si el rodeo ya tiene una alerta de AISLAMIENTO_SOCIAL pendiente (p. ej.
  las vacas de la fuga del Acto 3 quedaron afuera más de 15 min), el worker
  suma la #208 a esa alerta en vez de abrir una nueva.

Celo falso: moveState 1-3 y pasos de ~2 m; validado offline contra ml-celo
(umbral 0.947) y en Docker, sin alertas de celo.

- simulador.py excluye a la #208 vía FORZAR_SALUD_DEVICE_ID: nunca hay
  dos procesos mandando para la misma vaca.
- Cada corrida abre una sesión OTAA nueva (session_key_id distinto) y arranca
  f_cnt en 1. Si el worker ignora un paquete, se avisa en la consola.
- Cadencia propia de 5 s, ajustable con --intervalo. NO lee
  INTERVALO_SEGUNDOS.
"""

import argparse
import math
import os
import random
import time
from datetime import datetime, timedelta, timezone

import requests
# pyrefly: ignore [missing-import]
from dotenv import load_dotenv

load_dotenv()

API_URL = os.getenv("API_URL", "http://localhost:8081/api/v1/ingesta/uplink")

DEVICE_ID = os.getenv("FORZAR_SALUD_DEVICE_ID", "VIGIA-ARG-002")
NOMBRE_VACA = "#208 — Margarita (Jersey)" if DEVICE_ID == "VIGIA-ARG-002" else DEVICE_ID

# El rodeo vivo es el de simulador.py (misma variable y mismo default), sin la #208.
RODEO_DEVICE_IDS = [d.strip() for d in os.getenv(
    "RODEO_DEVICE_IDS",
    ",".join(f"VIGIA-ARG-{i:03d}" for i in range(3, 41)),
).split(",") if d.strip() and d.strip() != DEVICE_ID]

# ─── Seed (DataInitializer) ──────────────────────────────────────────────
# Minutos entre el último dato sembrado de la #208 y el arranque del backend
# (MINUTOS_SIN_DATOS_MARGARITA). El rodeo sembrado tiene datos hasta ~3 min
# antes del arranque: el relleno llega hasta 5 min antes.
MINUTOS_SIN_DATOS_SEED = 35
MINUTOS_RELLENO_HASTA_ARRANQUE = 5
TICK_SEED_MINUTOS = 5

# ─── Perímetro "Potrero Principal" (DataInitializer) ────────────────
LAT_CENTRO = -34.943317
LON_CENTRO = -59.225728
DELTA_PERIMETRO = 0.0045
# Margen contra el alambrado: la #208 no tiene que disparar FUERA_DE_PERIMETRO.
MARGEN_ALAMBRADO = 0.0008

# ─── Aislamiento (ml-geo / GeoDetectorStrategyImpl) ──────────────────────
UMBRAL_AISLAMIENTO_M = 100
DISTANCIA_MINIMA_M = 130       # a la vaca más cercana del rodeo; si baja, se aleja
VIGENCIA_POSICION_S = 1800     # ml-geo descarta animales sin posición en 30 min

# ─── Movimiento (validado contra ml-celo y ml-salud, ver docstring) ──────
# Igual que su día de enferma en el seed: moveState 1-3 y pasos de ~2 m (el
# paso del seed a 5 min), dando vueltas a menos de ~10 m de donde quedó.
MOVESTATE_ENFERMA = (1, 3)
PASO_PASTOREO = 0.000018
TURN_STD = 0.9
RADIO_PASTOREO = 0.0001
PASO_ALEJARSE = 0.00003        # ~3 m por paquete si el rodeo se le acerca
REVISAR_RODEO_CADA = 12        # paquetes

# ml-salud evalúa los últimos 288 puntos de las últimas 24 hs y, con menos,
# el job espera al próximo ciclo. Validado: hasta ~24 puntos del script dentro
# de esa ventana la severidad es MEDIA; con muchos más puede pasar a ALTA.
PUNTOS_VENTANA_SALUD = 288
MARGEN_VENTANA_SALUD = 2

TIMEOUT_SEGUNDOS = 5
# ─────────────────────────────────────────────────────────────────────────────


def ultima_posicion(device_id):
    """Última fila que el backend tiene para la caravana, o None."""
    base_url = API_URL.rsplit("/api/v1/", 1)[0]
    try:
        resp = requests.get(f"{base_url}/api/v1/ingesta/ultima-posicion/{device_id}", timeout=TIMEOUT_SEGUNDOS)
        if resp.status_code == 200:
            return resp.json()
    except requests.exceptions.RequestException as e:
        print(f"AVISO: no se pudo consultar la última posición de {device_id} ({e}).")
    return None


def fecha(fila):
    # LocalDateTime del worker, sin zona; se descartan las fracciones de segundo
    # (Jackson recorta los ceros y fromisoformat de Python < 3.11 no lo acepta).
    return datetime.fromisoformat(fila["fechaHora"].split(".")[0])


def metros(lat1, lon1, lat2, lon2):
    return math.hypot((lat2 - lat1) * 111_320, (lon2 - lon1) * 111_320 * math.cos(math.radians(lat1)))


def rodeo_vivo():
    """Posiciones (lat, lon) del rodeo vivo: las que ml-geo tendría en cuenta,
    con la misma vigencia de 30 min medida contra la más reciente."""
    filas = [f for f in (ultima_posicion(d) for d in RODEO_DEVICE_IDS) if f and f.get("fechaHora")]
    if not filas:
        return []
    ultima = max(fecha(f) for f in filas)
    return [(f["latitud"], f["longitud"]) for f in filas
            if (ultima - fecha(f)).total_seconds() <= VIGENCIA_POSICION_S]


def centro(posiciones):
    return (sum(p[0] for p in posiciones) / len(posiciones), sum(p[1] for p in posiciones) / len(posiciones))


def mas_cercana(estado, rodeo):
    return min(metros(estado["lat"], estado["lon"], *p) for p in rodeo)


def dentro_del_potrero(lat, lon):
    limite = DELTA_PERIMETRO - MARGEN_ALAMBRADO
    return abs(lat - LAT_CENTRO) <= limite and abs(lon - LON_CENTRO) <= limite


def mover(estado, rodeo):
    """Pastorea despacio cerca de su lugar; si el rodeo se le acercó, se aleja."""
    if mas_cercana(estado, rodeo) < DISTANCIA_MINIMA_M:
        c = centro(rodeo)
        rumbo = math.atan2(estado["lon"] - c[1], estado["lat"] - c[0])
        lat = estado["lat"] + PASO_ALEJARSE * math.cos(rumbo)
        lon = estado["lon"] + PASO_ALEJARSE * math.sin(rumbo)
        if dentro_del_potrero(lat, lon):
            estado["lat"], estado["lon"] = lat, lon
            estado["lugar"] = (lat, lon)
            return
    lugar = estado["lugar"]
    if math.hypot(estado["lat"] - lugar[0], estado["lon"] - lugar[1]) > RADIO_PASTOREO:
        estado["rumbo"] = math.atan2(lugar[1] - estado["lon"], lugar[0] - estado["lat"])
    else:
        estado["rumbo"] += random.gauss(0, TURN_STD)
    estado["lat"] += PASO_PASTOREO * math.cos(estado["rumbo"])
    estado["lon"] += PASO_PASTOREO * math.sin(estado["rumbo"])


def enviar(estado, sesion, headers, cuando, rodeo):
    estado["f_cnt"] += 1
    estado["bateria"] = max(5.0, estado["bateria"] - random.uniform(0.005, 0.02))
    move_state = random.randint(*MOVESTATE_ENFERMA)
    payload = {
        "end_device_ids": {"dev_eui": DEVICE_ID},
        "uplink_message": {
            "received_at": cuando.astimezone(timezone.utc).isoformat(),
            "f_cnt": estado["f_cnt"],
            "session_key_id": sesion,
            "decoded_payload": {
                "type": "GNSSPosition",
                "latitude": round(estado["lat"], 6),
                "longitude": round(estado["lon"], 6),
                "moveState": move_state,
                "battery": f"{round(estado['bateria'], 1)}%",
            },
        },
    }
    dist = mas_cercana(estado, rodeo)
    try:
        resp = requests.post(API_URL, json=payload, headers=headers, timeout=TIMEOUT_SEGUNDOS)
        respuesta = resp.text.strip()
        if resp.status_code in (200, 201, 202) and respuesta.startswith("Ignorado"):
            # El worker contesta 200 aunque descarte el paquete.
            print(f"[{datetime.now():%H:%M:%S}] AVISO el worker NO guardó el paquete: {respuesta}")
        elif resp.status_code in (200, 201, 202):
            print(f"[{datetime.now():%H:%M:%S}] OK  {cuando:%H:%M} lat={estado['lat']:.6f} "
                  f"lon={estado['lon']:.6f} moveState={move_state} a {dist:4.0f} m de la vaca más cercana"
                  f"{' (fuera del grupo)' if dist > UMBRAL_AISLAMIENTO_M else ''}")
        else:
            print(f"[{datetime.now():%H:%M:%S}] ERR HTTP {resp.status_code}: {respuesta}")
    except requests.exceptions.RequestException as e:
        print(f"[{datetime.now():%H:%M:%S}] ERR {e}")


def main():
    parser = argparse.ArgumentParser(description="Acto 4 — desvío sanitario y aislamiento de la #208.")
    parser.add_argument("--intervalo", type=float, default=5.0,
                        help="Segundos entre paquetes (default 5).")
    args = parser.parse_args()

    print("=" * 70)
    print("  SIMULADOR DE SALUD Y AISLAMIENTO — DEMO E2E (Acto 4, Paso 4.1)")
    print("=" * 70)
    print(f"  Vaca          : {NOMBRE_VACA}  [{DEVICE_ID}]")
    print(f"  Target API    : {API_URL}")
    print(f"  Cadencia      : {args.intervalo:g} s")
    print("  Alertas       : SALUD (-40 % de actividad) y AISLAMIENTO_SOCIAL (> 100 m del rodeo)")
    print("=" * 70)

    actual = ultima_posicion(DEVICE_ID)
    if not actual:
        raise SystemExit(f"{DEVICE_ID} no tiene telemetría en el backend: ¿arrancó con el perfil dev?")
    rodeo = rodeo_vivo()
    if len(rodeo) < 3:
        raise SystemExit(f"El rodeo vivo tiene {len(rodeo)} vacas con posición reciente: "
                         f"¿está corriendo simulador.py?")

    estado = {"lat": actual["latitud"], "lon": actual["longitud"], "lugar": (actual["latitud"], actual["longitud"]),
              "rumbo": random.uniform(0, 2 * math.pi), "bateria": actual.get("bateria") or 80.0, "f_cnt": 0}
    print(f"\n  Rodeo vivo    : {len(rodeo)} vacas")
    print(f"  #208          : a {metros(estado['lat'], estado['lon'], *centro(rodeo)):.0f} m del centro del rodeo, "
          f"{mas_cercana(estado, rodeo):.0f} m de la vaca más cercana\n")

    headers = {}
    api_key = os.getenv("TTN_WEBHOOK_API_KEY")
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    sesion = f"salud_{int(time.time())}"

    # Relleno: solo si la #208 sigue con el último dato del seed (primera corrida).
    # Cubre cada intervalo de 5 min hasta el final del seed (geo) y repone los
    # puntos sembrados que ya salieron de la ventana de 24 hs (salud).
    ultimo = fecha(actual)
    ahora_local = datetime.now()
    relleno = []
    if (ahora_local - ultimo).total_seconds() > (MINUTOS_SIN_DATOS_SEED - 1) * 60:
        desde = ultimo + timedelta(minutes=TICK_SEED_MINUTOS)
        hasta = ultimo + timedelta(minutes=MINUTOS_SIN_DATOS_SEED - MINUTOS_RELLENO_HASTA_ARRANQUE)
        sembrados_en_ventana = min(PUNTOS_VENTANA_SALUD, int(
            (ultimo - (ahora_local - timedelta(hours=24))).total_seconds() // (TICK_SEED_MINUTOS * 60)) + 1)
        n = max(int((hasta - desde) / timedelta(minutes=TICK_SEED_MINUTOS)) + 1,
                PUNTOS_VENTANA_SALUD - sembrados_en_ventana + MARGEN_VENTANA_SALUD)
        relleno = [desde + (hasta - desde) * k / (n - 1) for k in range(n)]
    if relleno:
        print(f"[{datetime.now():%H:%M:%S}] Completando la historia de la #208 entre "
              f"{relleno[0]:%H:%M} y {relleno[-1]:%H:%M} ({len(relleno)} puntos)...")
        for cuando in relleno:
            mover(estado, rodeo)
            enviar(estado, sesion, headers, cuando.astimezone(), rodeo)

    print(f"\n[{datetime.now():%H:%M:%S}] En vivo. Las alertas de SALUD y AISLAMIENTO_SOCIAL de la #208 "
          f"aparecen en el próximo ciclo de cada job (60 s con la config de demo).")
    print(">>> Ctrl+C cuando termine el Acto 4. <<<\n")

    paquetes = 0
    while True:
        inicio_ciclo = time.time()
        if paquetes and paquetes % REVISAR_RODEO_CADA == 0:
            rodeo = rodeo_vivo() or rodeo
        mover(estado, rodeo)
        enviar(estado, sesion, headers, datetime.now().astimezone(), rodeo)
        paquetes += 1

        espera = args.intervalo - (time.time() - inicio_ciclo)
        if espera > 0:
            time.sleep(espera)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nCortado por el presentador.")
