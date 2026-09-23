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

client = TestClient(app)

# Helpers

ENDPOINT = "/api/internal/ml/geo/analyze"
GRID_SIZE = 20  # Debe coincidir con la constante en main.py
UMBRAL_SUBUTILIZADA = 0.15


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

    def test_todos_en_el_mismo_punto_un_bin_con_intensidad_maxima(self):
        """
        Caso borde: todos los puntos en la misma coordenada.
        El codigo expande el bounding-box en +/-0.0001 para evitar division
        por cero, por lo que todos los puntos caeran en el mismo bin central.
        Ese bin debe tener intensidad == 1.0 y los demas == 0.0 -> subutilizadas.
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
        # Todas las demas tienen intensidad 0 -> subutilizadas
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

    def test_celda_subutilizada_cuando_intensidad_baja(self):
        """Celdas con intensidad <= UMBRAL_SUBUTILIZADA deben marcarse subutilizadas."""
        puntos = [
            make_punto("A1", -34.0, -59.0, "2024-01-01T00:00:00Z"),
            make_punto("A2", -35.0, -60.0, "2024-01-01T01:00:00Z"),
        ]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        for celda in response.json()["mapa_calor"]:
            if celda["intensidad"] <= UMBRAL_SUBUTILIZADA:
                assert celda["subutilizada"] is True
            else:
                assert celda["subutilizada"] is False

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

    def test_un_solo_animal_cohesion_cero_sin_alerta(self):
        """
        Caso borde: un unico animal en el historial.
        No puede medirse aislamiento respecto a un rodeo,
        por lo que indice_cohesion debe ser 0.0 y alerta_aislamiento False.
        """
        puntos = [make_punto("A1", -34.0, -59.0)]
        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        alertas = response.json()["alertas"]
        assert len(alertas) == 1
        assert alertas[0]["animal_id"] == "A1"
        assert alertas[0]["indice_cohesion"] == pytest.approx(0.0)
        assert alertas[0]["alerta_aislamiento"] is False

    def test_todos_en_el_mismo_punto_cohesion_cero(self):
        """
        Caso borde: todos los animales en la misma coordenada exacta.
        La distancia de cada animal al centroide de los demas debe ser ~0.
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
            assert alerta["indice_cohesion"] == pytest.approx(0.0, abs=1e-3)
            assert alerta["alerta_aislamiento"] is False

    def test_puntos_dispersos_distancia_correcta(self):
        """
        Con animales en ubicaciones distintas, el indice de cohesion
        debe aproximarse al valor calculado con haversine manual.
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
            assert alerta["indice_cohesion"] == pytest.approx(dist_esperada, rel=1e-4)
            assert alerta["alerta_aislamiento"] is True  # >> 100 m

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

    def test_usa_ultima_posicion_por_animal(self):
        """
        Cuando hay multiples registros para el mismo animal, se debe usar
        el mas reciente (mayor timestamp lexicografico) para calcular alertas.
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

    def test_tres_animales_uno_alejado_mayor_indice_cohesion(self):
        """
        Con tres animales: dos juntos (A1, A2) y uno muy alejado (A3).
        El indice_cohesion de cada animal es su distancia al centroide de
        los OTROS dos. Por lo tanto:
          - cohesion(A1) = haversine(A1, centroide(A2, A3))
          - cohesion(A2) = haversine(A2, centroide(A1, A3))
          - cohesion(A3) = haversine(A3, centroide(A1, A2))  <- el mayor
        A3 siempre debe tener el mayor indice y estar en alerta.
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

        assert alertas["A1"]["indice_cohesion"] == pytest.approx(dist_a1, rel=1e-4)
        assert alertas["A2"]["indice_cohesion"] == pytest.approx(dist_a2, rel=1e-4)
        assert alertas["A3"]["indice_cohesion"] == pytest.approx(dist_a3, rel=1e-4)

        # A3 esta mas lejos del centroide de A1+A2 que cualquier otro
        assert alertas["A3"]["indice_cohesion"] > alertas["A1"]["indice_cohesion"]
        assert alertas["A3"]["indice_cohesion"] > alertas["A2"]["indice_cohesion"]
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
# FILTRO POR RODEO / ZONAS SUBUTILIZADAS
# ===========================================================================

class TestFiltroYZonasSubutilizadas:
    """animal_ids filtra el mapa de calor (grilla ajustada al filtro) y define las zonas subutilizadas del campo."""

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
        assert data["celdas_subutilizadas"] is None
        assert len(data["alertas"]) == 2

    def test_alertas_no_dependen_del_filtro(self):
        con = client.post(ENDPOINT, json={"animal_ids": ["A1"], "historial_7d": self.PUNTOS}).json()["alertas"]
        sin = client.post(ENDPOINT, json={"historial_7d": self.PUNTOS}).json()["alertas"]

        assert sorted(con, key=lambda a: a["animal_id"]) == sorted(sin, key=lambda a: a["animal_id"])

    def test_celdas_subutilizadas_se_calculan_sobre_todo_el_campo(self):
        """
        Campo = bbox de A1 y A2 (1 grado). Filtrando A1 (todo en una esquina), solo las celdas
        que A1 pisa dejan de ser subutilizadas; las demas, incluida la de A2, si lo son.
        """
        response = client.post(ENDPOINT, json={"animal_ids": ["A1"], "historial_7d": self.PUNTOS})

        zonas = response.json()["celdas_subutilizadas"]
        assert zonas["lat_step"] == pytest.approx(1.0 / GRID_SIZE)
        assert zonas["lng_step"] == pytest.approx(1.0 / GRID_SIZE)
        assert len(zonas["celdas"]) == GRID_SIZE * GRID_SIZE - 1  # A1 cae entero en una celda
        assert {"lat", "lng"} == set(zonas["celdas"][0].keys())

    def test_sin_filtro_las_celdas_subutilizadas_son_las_no_visitadas(self):
        response = client.post(ENDPOINT, json={"historial_7d": self.PUNTOS})

        # A1 (2 puntos, 1 celda: 1.0) y A2 (1 punto: 0.5) superan el umbral -> 398 subutilizadas
        assert len(response.json()["celdas_subutilizadas"]["celdas"]) == GRID_SIZE * GRID_SIZE - 2
