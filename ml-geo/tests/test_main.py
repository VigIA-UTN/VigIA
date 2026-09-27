# pyrefly: ignore [missing-import]
import pytest
# pyrefly: ignore [missing-import]
from fastapi.testclient import TestClient
import sys
import os
import math

# Añadir el directorio raíz de ml-geo al PYTHONPATH
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from app.main import app
from app import main as main_app

client = TestClient(app)

# Helpers

ENDPOINT = "/api/internal/ml/geo/analyze"
GRID_SIZE = 20  # Debe coincidir con la constante en main.py
UMBRAL_AISLAMIENTO_METROS = 100.0


def make_punto(animal_id: str, lat: float, lng: float, timestamp: str = "2024-01-01T00:00:00Z"):
    return {"animal_id": animal_id, "lat": lat, "lng": lng, "timestamp": timestamp}


def haversine(lat1, lon1, lat2, lon2):
    """Replica local de la fórmula haversine usada en main.py."""
    R = 6_371_000
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def cohesion_esperada(distancia_m, umbral=UMBRAL_AISLAMIENTO_METROS):
    """Replica local del índice de cohesión de main.py."""
    return umbral / (umbral + distancia_m)


# ===========================================================================
# HEALTH
# ===========================================================================

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "ml-geo"}


# ===========================================================================
# DENSIDAD - Mapa de calor
# ===========================================================================

class TestDensidad:
    """Tests enfocados en la generacion del mapa de calor (densidad)."""

    def test_historial_vacio_devuelve_mapa_calor_vacio(self):
        """Caso borde: historial vacio -> mapa de calor vacio, sin alertas."""
        payload = {"historial_7d": []}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        data = response.json()
        assert data["mapa_calor"] == []
        assert data["alertas"] == []

    def test_mapa_calor_tiene_grid_size_al_cuadrado_celdas(self):
        """Con puntos validos el mapa de calor siempre tiene GRID_SIZE^2 celdas."""
        puntos = [
            make_punto("A1", -34.0, -59.0, "2024-01-01T00:00:00Z"),
            make_punto("A1", -34.5, -59.5, "2024-01-01T01:00:00Z"),
        ]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        data = response.json()
        assert len(data["mapa_calor"]) == GRID_SIZE * GRID_SIZE

    def test_celda_del_mapa_solo_expone_posicion_e_intensidad(self):
        """El mapa de calor solo lleva posicion e intensidad por celda."""
        puntos = [make_punto("A1", -34.0, -59.0), make_punto("A2", -35.0, -60.0)]
        response = client.post(ENDPOINT, json={"historial_7d": puntos})

        assert response.status_code == 200
        for celda in response.json()["mapa_calor"]:
            assert set(celda.keys()) == {"lat", "lng", "intensidad"}

    def test_todos_en_el_mismo_punto_un_bin_con_intensidad_maxima(self):
        """
        Caso borde: todos los puntos en la misma coordenada.
        El codigo expande el bounding-box en +/-0.0001 para evitar division
        por cero, por lo que todos los puntos caeran en el mismo bin central.
        Ese bin debe tener intensidad == 1.0 y los demas == 0.0.
        """
        puntos = [make_punto("A1", -34.0, -59.0, f"2024-01-01T{h:02d}:00:00Z") for h in range(5)]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        data = response.json()
        intensidades = [c["intensidad"] for c in data["mapa_calor"]]

        assert max(intensidades) == pytest.approx(1.0)
        # Solo una celda tiene intensidad == 1.0
        assert intensidades.count(pytest.approx(1.0)) == 1
        # Todas las demas tienen intensidad 0
        celdas_activas = [c for c in data["mapa_calor"] if c["intensidad"] > 0]
        assert len(celdas_activas) == 1

    def test_puntos_dispersos_multiples_bins_activos(self):
        """
        Caso: puntos en extremos opuestos del bounding-box.
        Deben caer en bins distintos y ambos tendran intensidad == 1.0.
        """
        puntos = [
            make_punto("A1", -34.0, -59.0, "2024-01-01T00:00:00Z"),
            make_punto("A2", -35.0, -60.0, "2024-01-01T01:00:00Z"),
        ]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        data = response.json()
        celdas_activas = [c for c in data["mapa_calor"] if c["intensidad"] > 0]
        # Cada punto cae en un bin diferente
        assert len(celdas_activas) == 2
        for celda in celdas_activas:
            assert celda["intensidad"] == pytest.approx(1.0)

    def test_intensidad_normalizada_entre_0_y_1(self):
        """La intensidad de cada celda siempre cae en [0, 1]."""
        puntos = [
            make_punto("A1", -34.0 + i * 0.05, -59.0 + i * 0.05, f"2024-01-0{(i % 9) + 1}T00:00:00Z")
            for i in range(10)
        ]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        for celda in response.json()["mapa_calor"]:
            assert 0.0 <= celda["intensidad"] <= 1.0

    def test_filtro_por_animal_id_solo_cuenta_ese_animal(self):
        """
        Si se especifica animal_id en el request, el mapa de calor solo
        toma en cuenta los puntos de ese animal.
        """
        puntos = [
            make_punto("A1", -34.0, -59.0, "2024-01-01T00:00:00Z"),
            make_punto("A1", -34.0, -59.0, "2024-01-01T01:00:00Z"),
            make_punto("A2", -35.0, -60.0, "2024-01-01T00:00:00Z"),
        ]
        # Sin filtro: ambos animales contribuyen al mapa
        payload_sin_filtro = {"historial_7d": puntos}
        resp_sin = client.post(ENDPOINT, json=payload_sin_filtro)

        # Con filtro solo A1
        payload_con_filtro = {"animal_id": "A1", "historial_7d": puntos}
        resp_con = client.post(ENDPOINT, json=payload_con_filtro)

        assert resp_sin.status_code == 200
        assert resp_con.status_code == 200

        activas_sin = [c for c in resp_sin.json()["mapa_calor"] if c["intensidad"] > 0]
        activas_con = [c for c in resp_con.json()["mapa_calor"] if c["intensidad"] > 0]

        # Sin filtro hay puntos de A1 y A2 (dos ubicaciones distintas -> 2 bins)
        assert len(activas_sin) == 2
        # Con filtro solo A1, todos en el mismo punto -> 1 bin
        assert len(activas_con) == 1


# ===========================================================================
# COHESION / AISLAMIENTO - Alertas
# ===========================================================================

class TestCohesionAislamiento:
    """Tests enfocados en el calculo de indice de cohesion y alertas de aislamiento."""

    def test_historial_vacio_no_genera_alertas(self):
        """Caso borde: sin historial -> sin alertas."""
        payload = {"historial_7d": []}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        assert response.json()["alertas"] == []

    def test_un_solo_animal_cohesion_maxima_sin_alerta(self):
        """
        Caso borde: un unico animal en el historial.
        No puede medirse aislamiento respecto a un rodeo, asi que se reporta
        la cohesion maxima (1.0), distancia 0 y sin alerta.
        """
        puntos = [make_punto("A1", -34.0, -59.0)]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        alertas = response.json()["alertas"]
        assert len(alertas) == 1
        assert alertas[0]["animal_id"] == "A1"
        assert alertas[0]["indice_cohesion"] == pytest.approx(1.0)
        assert alertas[0]["distancia_centroide_m"] == pytest.approx(0.0)
        assert alertas[0]["alerta_aislamiento"] is False

    def test_todos_en_el_mismo_punto_cohesion_maxima(self):
        """
        Caso borde: todos los animales en la misma coordenada exacta.
        La distancia de cada animal al centroide de los demas debe ser ~0,
        y por lo tanto el indice de cohesion ~1.
        """
        puntos = [
            make_punto("A1", -34.0, -59.0),
            make_punto("A2", -34.0, -59.0),
            make_punto("A3", -34.0, -59.0),
        ]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        for alerta in response.json()["alertas"]:
            assert alerta["distancia_centroide_m"] == pytest.approx(0.0, abs=1e-3)
            assert alerta["indice_cohesion"] == pytest.approx(1.0, abs=1e-5)
            assert alerta["alerta_aislamiento"] is False

    def test_puntos_dispersos_distancia_correcta(self):
        """
        Con animales en ubicaciones distintas, distancia_centroide_m debe
        coincidir con el haversine manual y el indice derivarse de ella.
        """
        # A1 en un extremo, A2 en el otro (~111 km de diferencia en lat)
        puntos = [
            make_punto("A1", -34.0, -59.0),
            make_punto("A2", -35.0, -59.0),
        ]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        alertas = response.json()["alertas"]
        assert len(alertas) == 2

        # Distancia esperada A1->centroide (= posicion de A2) y viceversa
        dist_esperada = haversine(-34.0, -59.0, -35.0, -59.0)

        for alerta in alertas:
            assert alerta["distancia_centroide_m"] == pytest.approx(dist_esperada, rel=1e-4)
            assert alerta["indice_cohesion"] == pytest.approx(cohesion_esperada(dist_esperada), rel=1e-4)
            assert alerta["alerta_aislamiento"] is True  # >> 100 m

    def test_indice_cohesion_siempre_entre_0_y_1(self):
        puntos = [
            make_punto("A1", -34.0, -59.0),
            make_punto("A2", -34.0001, -59.0),
            make_punto("A3", -36.0, -61.0),
        ]
        response = client.post(ENDPOINT, json={"historial_7d": puntos})

        assert response.status_code == 200
        for alerta in response.json()["alertas"]:
            assert 0.0 < alerta["indice_cohesion"] <= 1.0

    def test_indice_vale_medio_exactamente_en_el_umbral(self):
        """El 0.5 del indice es, por construccion, el umbral de aislamiento."""
        assert main_app._indice_cohesion(0.0) == pytest.approx(1.0)
        assert main_app._indice_cohesion(main_app.UMBRAL_AISLAMIENTO_METROS) == pytest.approx(0.5)

    def test_alerta_equivale_a_indice_bajo_medio(self):
        """
        Invariante del contrato: alerta_aislamiento <=> distancia > umbral <=> indice < 0.5.
        La alerta se deriva de la distancia, pero ambas lecturas deben coincidir.
        """
        puntos = [
            make_punto("A1", -34.0, -59.0),
            make_punto("A2", -34.0001, -59.0),
            make_punto("A3", -34.02, -59.0),
            make_punto("A4", -36.0, -61.0),
        ]
        response = client.post(ENDPOINT, json={"historial_7d": puntos})

        assert response.status_code == 200
        for alerta in response.json()["alertas"]:
            esperada = alerta["distancia_centroide_m"] > UMBRAL_AISLAMIENTO_METROS
            assert alerta["alerta_aislamiento"] is esperada
            assert (alerta["indice_cohesion"] < 0.5) is esperada

    def test_animales_cercanos_no_generan_alerta_aislamiento(self):
        """
        Animales separados por unos pocos metros no superan el umbral
        de 100 m, por lo que alerta_aislamiento debe ser False.
        """
        # Diferencia de ~0.0001 deg ~= 11 m en latitud
        puntos = [
            make_punto("A1", -34.00000, -59.0),
            make_punto("A2", -34.00010, -59.0),
        ]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        for alerta in response.json()["alertas"]:
            assert alerta["alerta_aislamiento"] is False

    def test_umbral_de_aislamiento_sale_de_la_configuracion(self, monkeypatch):
        """
        Con el umbral por defecto (100 m) dos animales a ~55 m no alertan;
        bajandolo a 25 m, la misma entrada pasa a alertar.
        """
        # ~0.0005 deg de latitud ~= 55 m
        puntos = [
            make_punto("A1", -34.00000, -59.0),
            make_punto("A2", -34.00050, -59.0),
        ]

        sin_alerta = client.post(ENDPOINT, json={"historial_7d": puntos}).json()["alertas"]
        assert all(a["alerta_aislamiento"] is False for a in sin_alerta)

        monkeypatch.setattr(main_app, "UMBRAL_AISLAMIENTO_METROS", 25.0)
        con_alerta = client.post(ENDPOINT, json={"historial_7d": puntos}).json()["alertas"]
        assert all(a["alerta_aislamiento"] is True for a in con_alerta)
        # El indice se recalibra con el umbral nuevo
        for alerta in con_alerta:
            assert alerta["indice_cohesion"] == pytest.approx(
                cohesion_esperada(alerta["distancia_centroide_m"], 25.0), rel=1e-6
            )

    def test_usa_ultima_posicion_por_animal(self):
        """
        Cuando hay multiples registros para el mismo animal, se debe usar
        el mas reciente para calcular alertas.
        """
        puntos = [
            # Posicion antigua de A1 (lejos)
            make_punto("A1", -36.0, -59.0, "2024-01-01T00:00:00Z"),
            # Posicion reciente de A1 (cerca de A2)
            make_punto("A1", -34.00000, -59.0, "2024-01-02T00:00:00Z"),
            make_punto("A2", -34.00010, -59.0, "2024-01-01T12:00:00Z"),
        ]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        alertas = {a["animal_id"]: a for a in response.json()["alertas"]}

        # La posicion reciente de A1 esta ~11 m de A2 -> sin alerta
        assert alertas["A1"]["alerta_aislamiento"] is False

    def test_timestamps_se_ordenan_por_instante_no_por_string(self):
        """
        '2024-01-01T23:00:00-03:00' es 02:00Z del 2 de enero, o sea posterior a
        '2024-01-02T01:00:00Z', aunque como string ordene antes. La posicion mas
        reciente de A1 es la cercana a A2, asi que no debe haber alerta.
        """
        puntos = [
            make_punto("A1", -34.00000, -59.0, "2024-01-01T23:00:00-03:00"),  # 02:00Z, la mas reciente
            make_punto("A1", -36.00000, -59.0, "2024-01-02T01:00:00Z"),       # 01:00Z, anterior
            make_punto("A2", -34.00010, -59.0, "2024-01-01T00:00:00Z"),
        ]
        response = client.post(ENDPOINT, json={"historial_7d": puntos})

        assert response.status_code == 200
        alertas = {a["animal_id"]: a for a in response.json()["alertas"]}
        assert alertas["A1"]["alerta_aislamiento"] is False
        assert alertas["A1"]["distancia_centroide_m"] < UMBRAL_AISLAMIENTO_METROS

    def test_mezcla_de_timestamps_naive_y_con_zona_no_rompe(self):
        """Los naive se asumen UTC; mezclarlos con aware no debe lanzar TypeError."""
        puntos = [
            make_punto("A1", -34.0, -59.0, "2024-01-01T00:00:00"),
            make_punto("A1", -34.0, -59.0, "2024-01-02T00:00:00.123456Z"),
            make_punto("A2", -34.0001, -59.0, "2024-01-01T12:00:00+00:00"),
        ]
        response = client.post(ENDPOINT, json={"historial_7d": puntos})

        assert response.status_code == 200
        assert len(response.json()["alertas"]) == 2

    def test_tres_animales_uno_alejado_menor_cohesion(self):
        """
        Con tres animales: dos juntos (A1, A2) y uno muy alejado (A3).
        distancia_centroide_m de cada animal es su distancia al centroide de
        los OTROS dos. A3 debe tener la mayor distancia, el MENOR indice de
        cohesion (el indice es inverso a la distancia) y estar en alerta.
        """
        a1_lat, a2_lat, a3_lat = -34.00000, -34.00010, -36.00000
        lng = -59.0

        puntos = [
            make_punto("A1", a1_lat, lng),
            make_punto("A2", a2_lat, lng),
            make_punto("A3", a3_lat, lng),
        ]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        alertas = {a["animal_id"]: a for a in response.json()["alertas"]}

        # Centroides esperados (promedio de los otros dos)
        centroide_para_a1 = ((a2_lat + a3_lat) / 2, lng)
        centroide_para_a2 = ((a1_lat + a3_lat) / 2, lng)
        centroide_para_a3 = ((a1_lat + a2_lat) / 2, lng)

        dist_a1 = haversine(a1_lat, lng, *centroide_para_a1)
        dist_a2 = haversine(a2_lat, lng, *centroide_para_a2)
        dist_a3 = haversine(a3_lat, lng, *centroide_para_a3)

        assert alertas["A1"]["distancia_centroide_m"] == pytest.approx(dist_a1, rel=1e-4)
        assert alertas["A2"]["distancia_centroide_m"] == pytest.approx(dist_a2, rel=1e-4)
        assert alertas["A3"]["distancia_centroide_m"] == pytest.approx(dist_a3, rel=1e-4)

        # A3 esta mas lejos del centroide de A1+A2 que cualquier otro -> peor cohesion
        assert alertas["A3"]["indice_cohesion"] < alertas["A1"]["indice_cohesion"]
        assert alertas["A3"]["indice_cohesion"] < alertas["A2"]["indice_cohesion"]
        assert alertas["A3"]["alerta_aislamiento"] is True

    def test_cada_animal_tiene_exactamente_una_alerta(self):
        """La respuesta debe tener exactamente un registro de alerta por animal unico."""
        puntos = [
            make_punto("A1", -34.0, -59.0, "2024-01-01T00:00:00Z"),
            make_punto("A1", -34.0, -59.0, "2024-01-01T06:00:00Z"),  # mismo animal, otro timestamp
            make_punto("A2", -34.1, -59.1),
            make_punto("A3", -34.2, -59.2),
        ]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        alertas = response.json()["alertas"]
        ids = [a["animal_id"] for a in alertas]
        assert len(ids) == 3
        assert len(set(ids)) == 3  # sin duplicados


# ===========================================================================
# FILTRO POR RODEO
# ===========================================================================

class TestFiltroPorRodeo:
    """animal_ids filtra el mapa de calor (grilla ajustada al filtro); las alertas siguen usando todo el historial."""

    PUNTOS = [
        make_punto("A1", -34.0000, -59.0000),
        make_punto("A1", -34.0010, -59.0010),
        make_punto("A2", -35.0, -60.0),
    ]

    def test_animal_ids_solo_cuenta_esos_animales(self):
        response = client.post(ENDPOINT, json={"animal_ids": ["A1"], "historial_7d": self.PUNTOS})

        assert response.status_code == 200
        activas = [c for c in response.json()["mapa_calor"] if c["intensidad"] > 0]
        assert len(activas) == 2  # los dos puntos de A1; A2 queda afuera

    def test_grilla_del_mapa_de_calor_se_ajusta_al_filtro(self):
        """Con A1 filtrado la grilla cubre ~0.001 grados, no el grado completo del campo."""
        response = client.post(ENDPOINT, json={"animal_ids": ["A1"], "historial_7d": self.PUNTOS})

        lats = [c["lat"] for c in response.json()["mapa_calor"]]
        assert len(lats) == GRID_SIZE * GRID_SIZE
        assert max(lats) - min(lats) < 0.002

    def test_animal_ids_vacio_devuelve_mapa_vacio_pero_conserva_alertas(self):
        """Un rodeo sin animales no tiene mapa de calor, pero las alertas usan el historial completo."""
        response = client.post(ENDPOINT, json={"animal_ids": [], "historial_7d": self.PUNTOS})

        data = response.json()
        assert data["mapa_calor"] == []
        assert len(data["alertas"]) == 2

    def test_alertas_no_dependen_del_filtro(self):
        con = client.post(ENDPOINT, json={"animal_ids": ["A1"], "historial_7d": self.PUNTOS}).json()["alertas"]
        sin = client.post(ENDPOINT, json={"historial_7d": self.PUNTOS}).json()["alertas"]

        assert sorted(con, key=lambda a: a["animal_id"]) == sorted(sin, key=lambda a: a["animal_id"])


# ===========================================================================
# CONTRATO / ERRORES (patron ml-celo)
# ===========================================================================

class TestContratoYErrores:

    def test_payload_invalido_devuelve_422(self):
        """Falta lat en el punto -> error de validacion de FastAPI."""
        payload = {"historial_7d": [{"animal_id": "A1", "lng": -59.0, "timestamp": "2024-01-01T00:00:00Z"}]}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 422
        data = response.json()
        assert data["detail"][0]["type"] == "missing"
        assert data["detail"][0]["loc"] == ["body", "historial_7d", 0, "lat"]

    def test_timestamp_invalido_devuelve_422(self):
        payload = {"historial_7d": [make_punto("A1", -34.0, -59.0, "ayer a la tarde")]}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 422
        assert response.json()["detail"][0]["loc"] == ["body", "historial_7d", 0, "timestamp"]

    def test_error_interno_devuelve_503_con_trace_id(self, monkeypatch):
        """Ante un fallo inesperado se responde 503 con el formato de error comun a los ml-*."""
        def explotar(*args, **kwargs):
            raise RuntimeError("fallo de calculo")

        monkeypatch.setattr(main_app, "_haversine", explotar)

        puntos = [make_punto("A1", -34.0, -59.0), make_punto("A2", -35.0, -60.0)]
        response = client.post(ENDPOINT, json={"historial_7d": puntos}, headers={"X-Correlation-ID": "abc-123"})

        assert response.status_code == 503
        data = response.json()
        assert data["error"] == "Internal Server Error"
        assert "fallo de calculo" in data["mensaje"]
        assert data["trace_id"] == "abc-123"

    def test_devuelve_el_correlation_id_recibido(self):
        puntos = [make_punto("A1", -34.0, -59.0)]
        response = client.post(ENDPOINT, json={"historial_7d": puntos}, headers={"X-Correlation-ID": "xyz-789"})

        assert response.status_code == 200
        assert response.headers["X-Correlation-ID"] == "xyz-789"
