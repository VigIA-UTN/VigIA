"""
ML01 - Contrato de los microservicios ML: ml-geo.

Casos del bloque ML01 de la planilla de Casos de Prueba (hoja Ciclo 1). Cada
test lleva en el nombre el número de caso (test_ML01_NN_...). Los casos que
cubren los tres servicios (ML01-10 en adelante) tienen su parte en
test_contrato_ml01.py de ml-celo y de ml-salud.

ml-geo expone un solo endpoint, /analyze, que devuelve el mapa de calor y la
cohesión del rodeo juntos. El contrato es el que consumen el backend-worker y
el backend-api: GeoAnalyzeResponseDTO ({mapa_calor, alertas}), MatrizDensidadDTO,
AlertaAnimalDTO y el formato de error común {error, mensaje, trace_id}. ml-geo
no tiene modelo, así que no hay caso de health sin modelo (ML01-16).
"""
# pyrefly: ignore [missing-import]
import pytest
# pyrefly: ignore [missing-import]
from fastapi.testclient import TestClient
import logging
import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from app.main import app
from app import main as main_app

client = TestClient(app)

ENDPOINT = "/api/internal/ml/geo/analyze"
GRID_SIZE = 20

# Campos de los DTO de backend-core-lib (dto/ml/geo) y su tipo en el JSON.
CONTRATO_RESPUESTA = {"mapa_calor": list, "alertas": list}
CONTRATO_CELDA = {"lat": float, "lng": float, "intensidad": float}
CONTRATO_ALERTA = {"animal_id": str, "indice_cohesion": float, "distancia_centroide_m": float,
                   "alerta_aislamiento": bool, "tiempo_fuera_grupo_s": float, "tiempo_fuera_continuo_s": float}
CONTRATO_ERROR = {"error": str, "mensaje": str, "trace_id": str}

# ~0.009 grados de latitud son ~1 km: lejos del umbral de aislamiento por defecto (100 m).
LEJOS = 0.009


def assert_contrato(data: dict, contrato: dict, extra=()):
    """Mismas claves que el contrato (más las `extra` permitidas) y cada valor del tipo esperado."""
    assert set(data) - set(extra) == set(contrato)
    for campo, tipo in contrato.items():
        valor = data[campo]
        assert isinstance(valor, tipo), f"{campo}={valor!r} no es {tipo}"
        if tipo in (int, float):
            assert not isinstance(valor, bool), f"{campo} es bool, se esperaba {tipo}"


def punto(animal_id, lat=-34.0, lng=-59.0, timestamp="2024-01-01T00:00:00Z"):
    return {"animal_id": animal_id, "lat": lat, "lng": lng, "timestamp": timestamp}


def rodeo_con_un_alejado(timestamp="2024-01-01T00:00:00Z"):
    """A1 y A2 juntos; A3 a ~1 km."""
    return [punto("A1", timestamp=timestamp), punto("A2", -34.0001, -59.0001, timestamp),
            punto("A3", -34.0 + LEJOS, -59.0, timestamp)]


def por_animal(alertas):
    return {a["animal_id"]: a for a in alertas}


# ---------------------------------------------------------------------------
# Casos válidos: mapa de calor y cohesión
# ---------------------------------------------------------------------------

def test_ML01_06_mapa_de_calor_respeta_el_contrato():
    historial = [punto("A1", -34.0, -59.0), punto("A1", -34.0, -59.0), punto("A2", -34.01, -59.01)]

    response = client.post(ENDPOINT, json={"historial_7d": historial})

    assert response.status_code == 200
    data = response.json()
    assert_contrato(data, CONTRATO_RESPUESTA)
    assert len(data["mapa_calor"]) == GRID_SIZE * GRID_SIZE
    for celda in data["mapa_calor"]:
        assert_contrato(celda, CONTRATO_CELDA)
        assert 0.0 <= celda["intensidad"] <= 1.0
        assert -34.01 <= celda["lat"] <= -34.0 and -59.01 <= celda["lng"] <= -59.0
    intensidades = sorted(c["intensidad"] for c in data["mapa_calor"])
    # La celda con dos lecturas es el máximo (1.0); la de una lectura, la mitad.
    assert intensidades[-1] == pytest.approx(1.0)
    assert intensidades[-2] == pytest.approx(0.5)


def test_ML01_06_mapa_de_calor_filtrado_por_animal_ids():
    historial = [punto("A1", -34.0, -59.0), punto("A2", -34.01, -59.01)]

    data = client.post(ENDPOINT, json={"historial_7d": historial, "animal_ids": ["A1"]}).json()

    # La grilla se ajusta a los puntos de A1: todas las celdas quedan alrededor de su posición.
    assert len(data["mapa_calor"]) == GRID_SIZE * GRID_SIZE
    assert all(abs(c["lat"] - -34.0) < 0.001 for c in data["mapa_calor"])
    # Las alertas siguen usando el historial completo.
    assert set(por_animal(data["alertas"])) == {"A1", "A2"}


def test_ML01_07_cohesion_marca_al_animal_alejado_y_no_al_rodeo():
    data = client.post(ENDPOINT, json={"historial_7d": rodeo_con_un_alejado()}).json()

    alertas = por_animal(data["alertas"])
    assert set(alertas) == {"A1", "A2", "A3"}
    for alerta in alertas.values():
        assert_contrato(alerta, CONTRATO_ALERTA)
        assert 0.0 < alerta["indice_cohesion"] <= 1.0
    assert alertas["A3"]["alerta_aislamiento"] is True
    assert alertas["A3"]["indice_cohesion"] < 0.5
    assert alertas["A3"]["distancia_centroide_m"] > 900
    for a_id in ("A1", "A2"):
        assert alertas[a_id]["alerta_aislamiento"] is False
        assert alertas[a_id]["indice_cohesion"] > 0.5


def test_ML01_07_cohesion_usa_el_umbral_que_manda_el_backend():
    data = client.post(ENDPOINT, json={"historial_7d": rodeo_con_un_alejado(),
                                       "umbral_aislamiento_m": 2000}).json()

    assert por_animal(data["alertas"])["A3"]["alerta_aislamiento"] is False


def test_ML01_07_tiempo_fuera_del_grupo_por_intervalos_de_5_min():
    historial = []
    for minuto in ("00", "05", "10"):
        historial += rodeo_con_un_alejado(f"2024-01-01T00:{minuto}:00Z")

    alertas = por_animal(client.post(ENDPOINT, json={"historial_7d": historial}).json()["alertas"])

    assert alertas["A3"]["tiempo_fuera_grupo_s"] == 900.0
    assert alertas["A3"]["tiempo_fuera_continuo_s"] == 900.0
    assert alertas["A1"]["tiempo_fuera_grupo_s"] == 0.0


# ---------------------------------------------------------------------------
# Casos borde
# ---------------------------------------------------------------------------

def test_ML01_08_historial_vacio_devuelve_respuesta_vacia():
    response = client.post(ENDPOINT, json={"historial_7d": []})

    assert response.status_code == 200
    assert response.json() == {"mapa_calor": [], "alertas": []}


def test_ML01_08_un_solo_animal_no_tiene_rodeo_contra_el_que_medir():
    data = client.post(ENDPOINT, json={"historial_7d": [punto("A1")]}).json()

    assert len(data["mapa_calor"]) == GRID_SIZE * GRID_SIZE
    alerta = data["alertas"][0]
    assert alerta["alerta_aislamiento"] is False
    assert alerta["indice_cohesion"] == 1.0
    assert alerta["distancia_centroide_m"] == 0.0


def test_ML01_08_puntos_identicos_arman_una_grilla_valida():
    data = client.post(ENDPOINT, json={"historial_7d": [punto("A1"), punto("A2"), punto("A3")]}).json()

    intensidades = [c["intensidad"] for c in data["mapa_calor"]]
    assert len(intensidades) == GRID_SIZE * GRID_SIZE
    assert intensidades.count(1.0) == 1
    assert sum(intensidades) == 1.0
    assert not any(a["alerta_aislamiento"] for a in data["alertas"])


def test_ML01_09_caravana_sin_transmitir_queda_fuera_de_las_alertas():
    """A3 dejó de transmitir hace 2 h: no se compara con dónde está el rodeo ahora."""
    historial = [punto("A1", timestamp="2024-01-01T12:00:00Z"), punto("A2", timestamp="2024-01-01T12:00:00Z"),
                 punto("A3", -34.0 + LEJOS, -59.0, "2024-01-01T10:00:00Z")]

    data = client.post(ENDPOINT, json={"historial_7d": historial}).json()

    assert set(por_animal(data["alertas"])) == {"A1", "A2"}


def test_ML01_09_empate_entre_grupos_marca_a_los_dos():
    """Dos animales lejos entre sí: no hay grupo principal y los dos quedan alejados."""
    historial = [punto("A1"), punto("A2", -34.0 + LEJOS, -59.0)]

    alertas = por_animal(client.post(ENDPOINT, json={"historial_7d": historial}).json()["alertas"])

    assert alertas["A1"]["alerta_aislamiento"] is True
    assert alertas["A2"]["alerta_aislamiento"] is True


def test_ML01_09_timestamps_con_y_sin_zona_horaria():
    """El backend manda LocalDateTime sin offset; mezclarlo con timestamps con 'Z' no rompe."""
    historial = [punto("A1", timestamp="2024-01-01T00:00:00"), punto("A2", timestamp="2024-01-01T00:00:00Z")]

    response = client.post(ENDPOINT, json={"historial_7d": historial})

    assert response.status_code == 200
    assert len(response.json()["alertas"]) == 2


def test_ML01_09_animal_ids_que_no_estan_en_el_historial():
    data = client.post(ENDPOINT, json={"historial_7d": rodeo_con_un_alejado(), "animal_ids": ["X9"]}).json()

    assert data["mapa_calor"] == []
    assert len(data["alertas"]) == 3


# ---------------------------------------------------------------------------
# Payload inválido: 422 con mensaje claro
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("cuerpo, en_mensaje", [
    ({"historial_7d": [{"animal_id": "A1", "lng": -59.0, "timestamp": "2024-01-01T00:00:00Z"}]},
     "historial_7d[0].lat: campo obligatorio"),
    ({"historial_7d": [punto("A1", timestamp="ayer a la tarde")]},
     "historial_7d[0].timestamp: debe ser una fecha y hora ISO-8601"),
    ({"historial_7d": {"A1": []}}, "historial_7d: debe ser una lista"),
    ({}, "historial_7d: campo obligatorio"),
])
def test_ML01_10_payload_invalido_responde_422_con_mensaje_claro(cuerpo, en_mensaje):
    response = client.post(ENDPOINT, json=cuerpo, headers={"X-Correlation-ID": "ml01-10"})

    assert response.status_code == 422
    data = response.json()
    assert_contrato(data, CONTRATO_ERROR, extra=("detail",))
    assert data["error"] == "Payload Invalido"
    assert en_mensaje in data["mensaje"]
    assert data["trace_id"] == "ml01-10"


@pytest.mark.parametrize("cuerpo, en_mensaje", [
    ({"historial_7d": [punto("A1", lat=91.0)]}, "historial_7d[0].lat: debe ser menor o igual que 90"),
    ({"historial_7d": [punto("A1", lng=-200.0)]}, "historial_7d[0].lng: debe ser mayor o igual que -180"),
    ({"historial_7d": [punto("A1")], "umbral_aislamiento_m": 0}, "umbral_aislamiento_m: debe ser mayor que 0"),
    ({"historial_7d": [punto("A1")], "vigencia_posicion_s": -5}, "vigencia_posicion_s: debe ser mayor que 0"),
])
def test_ML01_11_valores_fuera_de_rango_responden_422(cuerpo, en_mensaje):
    response = client.post(ENDPOINT, json=cuerpo)

    assert response.status_code == 422
    assert en_mensaje in response.json()["mensaje"]


def test_ML01_11_coordenada_no_finita_responde_422():
    cuerpo = (b'{"historial_7d": [{"animal_id": "A1", "lat": NaN, "lng": -59, '
              b'"timestamp": "2024-01-01T00:00:00Z"}]}')
    response = client.post(ENDPOINT, content=cuerpo, headers={"Content-Type": "application/json"})

    assert response.status_code == 422
    assert "historial_7d[0].lat: debe ser un número finito" in response.json()["mensaje"]


# ---------------------------------------------------------------------------
# X-Correlation-ID
# ---------------------------------------------------------------------------

def test_ML01_13_devuelve_el_correlation_id_en_200_422_y_503(monkeypatch):
    def explotar(*args, **kwargs):
        raise RuntimeError("fallo de cálculo")

    headers = {"X-Correlation-ID": "ml01-13"}

    ok = client.post(ENDPOINT, json={"historial_7d": rodeo_con_un_alejado()}, headers=headers)
    invalido = client.post(ENDPOINT, json={}, headers=headers)
    monkeypatch.setattr(main_app, "_haversine", explotar)
    caido = client.post(ENDPOINT, json={"historial_7d": rodeo_con_un_alejado()}, headers=headers)

    assert [ok.status_code, invalido.status_code, caido.status_code] == [200, 422, 503]
    for r in (ok, invalido, caido):
        assert r.headers["X-Correlation-ID"] == "ml01-13"
    assert caido.json()["trace_id"] == "ml01-13"


def test_ML01_13_sin_correlation_id_responde_unknown():
    response = client.post(ENDPOINT, json={"historial_7d": [punto("A1")]})

    assert response.headers["X-Correlation-ID"] == "unknown"


def test_ML01_14_loguea_el_correlation_id_en_cada_linea(caplog):
    caplog.set_level(logging.INFO, logger="ml-geo")

    client.post(ENDPOINT, json={"historial_7d": rodeo_con_un_alejado()}, headers={"X-Correlation-ID": "ml01-14-ok"})
    client.post(ENDPOINT, json={}, headers={"X-Correlation-ID": "ml01-14-422"})

    ok = [r for r in caplog.records if r.correlation_id == "ml01-14-ok"]
    invalido = [r for r in caplog.records if r.correlation_id == "ml01-14-422"]
    assert any("Análisis exitoso" in r.getMessage() for r in ok)
    assert any(r.getMessage() == f"POST {ENDPOINT} -> 200" for r in ok)
    assert any(r.levelno == logging.WARNING and "Payload inválido" in r.getMessage() for r in invalido)
    assert any(r.getMessage() == f"POST {ENDPOINT} -> 422" for r in invalido)


# ---------------------------------------------------------------------------
# Health y fallas del servicio
# ---------------------------------------------------------------------------

def test_ML01_15_health_responde_ok():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ml-geo"}


def test_ML01_17_falla_del_servicio_responde_503_con_formato_comun(monkeypatch):
    def explotar(*args, **kwargs):
        raise RuntimeError("fallo de cálculo")

    monkeypatch.setattr(main_app, "_haversine", explotar)

    response = client.post(ENDPOINT, json={"historial_7d": rodeo_con_un_alejado()},
                           headers={"X-Correlation-ID": "ml01-17"})

    assert response.status_code == 503
    data = response.json()
    assert_contrato(data, CONTRATO_ERROR)
    assert data["error"] == "Internal Server Error"
    assert "fallo de cálculo" in data["mensaje"]
    assert data["trace_id"] == "ml01-17"
