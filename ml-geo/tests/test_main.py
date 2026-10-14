# pyrefly: ignore [missing-import]
import pytest
# pyrefly: ignore [missing-import]
from fastapi.testclient import TestClient
import sys
import os
import math
from datetime import datetime, timedelta, timezone

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
        assert main_app._indice_cohesion(0.0, UMBRAL_AISLAMIENTO_METROS) == pytest.approx(1.0)
        assert main_app._indice_cohesion(UMBRAL_AISLAMIENTO_METROS, UMBRAL_AISLAMIENTO_METROS) == pytest.approx(0.5)

    def test_en_rodeo_compacto_alerta_equivale_a_indice_bajo_medio(self):
        """
        En un rodeo compacto: alerta_aislamiento <=> distancia > umbral <=> indice < 0.5.
        La alerta es estar fuera del grupo principal, pero acá ambas lecturas coinciden.
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

    def test_en_rodeo_estirado_la_punta_tiene_indice_bajo_sin_alerta(self):
        """
        11 animales en fila separados 90 m: cada uno enlaza con el siguiente, así
        que forman un solo grupo y nadie alerta. Pero la punta queda a ~450 m del
        centroide, con indice < 0.5. Alerta e indice pueden no coincidir.
        """
        paso = 90 / 111_195  # ~90 m en grados de latitud
        puntos = [make_punto(f"F{i}", -34.0 - i * paso, -59.0) for i in range(11)]
        response = client.post(ENDPOINT, json={"historial_7d": puntos})

        assert response.status_code == 200
        alertas = {a["animal_id"]: a for a in response.json()["alertas"]}
        assert all(a["alerta_aislamiento"] is False for a in alertas.values())
        assert alertas["F0"]["distancia_centroide_m"] == pytest.approx(450, rel=0.01)
        assert alertas["F0"]["indice_cohesion"] < 0.5

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
        bajandolo a 25 m mediante el campo del request, la misma entrada pasa a alertar.
        El campo del request tiene prioridad sobre el global.
        """
        # ~0.0005 deg de latitud ~= 55 m
        puntos = [
            make_punto("A1", -34.00000, -59.0),
            make_punto("A2", -34.00050, -59.0),
        ]

        # Sin umbral en el request => usa el global (100 m) => sin alerta
        sin_alerta = client.post(ENDPOINT, json={"historial_7d": puntos}).json()["alertas"]
        assert all(a["alerta_aislamiento"] is False for a in sin_alerta)

        # Enviando umbral_aislamiento_m=25 en el request => alerta
        con_alerta = client.post(
            ENDPOINT, json={"umbral_aislamiento_m": 25.0, "historial_7d": puntos}
        ).json()["alertas"]
        assert all(a["alerta_aislamiento"] is True for a in con_alerta)
        # El indice se recalibra con el umbral nuevo
        for alerta in con_alerta:
            assert alerta["indice_cohesion"] == pytest.approx(
                cohesion_esperada(alerta["distancia_centroide_m"], 25.0), rel=1e-6
            )

    def test_umbral_del_request_tiene_prioridad_sobre_el_global(self, monkeypatch):
        """
        El campo umbral_aislamiento_m del request tiene prioridad sobre el
        global UMBRAL_AISLAMIENTO_METROS, aunque este se cambie con monkeypatch.
        """
        # ~0.0005 deg ~= 55 m
        puntos = [
            make_punto("A1", -34.00000, -59.0),
            make_punto("A2", -34.00050, -59.0),
        ]
        # Global modificado a 200 m => sin alerta con ese umbral
        monkeypatch.setattr(main_app, "UMBRAL_AISLAMIENTO_METROS", 200.0)

        # Pero el request envia 25 m => debe alertar
        resp = client.post(
            ENDPOINT, json={"umbral_aislamiento_m": 25.0, "historial_7d": puntos}
        ).json()["alertas"]
        assert all(a["alerta_aislamiento"] is True for a in resp)
        for alerta in resp:
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
            make_punto("A1", -34.0, -59.0, "2024-01-01T00:20:00.123456Z"),
            make_punto("A2", -34.0001, -59.0, "2024-01-01T00:10:00+00:00"),
        ]
        response = client.post(ENDPOINT, json={"historial_7d": puntos})

        assert response.status_code == 200
        assert len(response.json()["alertas"]) == 2

    def test_tres_animales_uno_alejado_menor_cohesion(self):
        """
        Con tres animales: dos juntos (A1, A2) y uno muy alejado (A3).
        A1+A2 forman el cluster principal (tamaño 2); A3 es su propio cluster
        (tamaño 1).

        La distancia de cada animal se mide ahora contra el CENTROIDE DEL
        GRUPO PRINCIPAL (centroide de A1+A2), no contra el centroide de todos
        los demás.
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

        # El centroide del grupo principal {A1, A2}
        centroide_lat = (a1_lat + a2_lat) / 2
        dist_a1 = haversine(a1_lat, lng, centroide_lat, lng)
        dist_a2 = haversine(a2_lat, lng, centroide_lat, lng)
        dist_a3 = haversine(a3_lat, lng, centroide_lat, lng)

        assert alertas["A1"]["distancia_centroide_m"] == pytest.approx(dist_a1, abs=1.0)
        assert alertas["A2"]["distancia_centroide_m"] == pytest.approx(dist_a2, abs=1.0)
        assert alertas["A3"]["distancia_centroide_m"] == pytest.approx(dist_a3, rel=1e-4)

        # A3 está fuera del grupo principal -> alerta; A1 y A2 están dentro -> sin alerta
        assert alertas["A1"]["alerta_aislamiento"] is False
        assert alertas["A2"]["alerta_aislamiento"] is False
        assert alertas["A3"]["alerta_aislamiento"] is True

        # A3 tiene el menor índice de cohesión
        assert alertas["A3"]["indice_cohesion"] < alertas["A1"]["indice_cohesion"]
        assert alertas["A3"]["indice_cohesion"] < alertas["A2"]["indice_cohesion"]

    def test_cada_animal_tiene_exactamente_una_alerta(self):
        """La respuesta debe tener exactamente un registro de alerta por animal unico."""
        puntos = [
            make_punto("A1", -34.0, -59.0, "2024-01-01T00:00:00Z"),
            make_punto("A1", -34.0, -59.0, "2024-01-01T00:20:00Z"),  # mismo animal, otro timestamp
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


    def test_animal_con_ultima_posicion_vieja_no_se_clasifica(self):
        """
        A3 dejó de transmitir hace 2 h estando lejos: su posición vieja no se
        compara con el rodeo actual (no hay alerta falsa) y queda fuera de la respuesta.
        """
        puntos = [
            make_punto("A1", -34.0, -59.0, "2024-01-01T12:00:00Z"),
            make_punto("A2", -34.0001, -59.0, "2024-01-01T12:00:00Z"),
            make_punto("A3", -34.02, -59.0, "2024-01-01T10:00:00Z"),
        ]
        response = client.post(ENDPOINT, json={"historial_7d": puntos})

        assert response.status_code == 200
        alertas = response.json()["alertas"]
        assert sorted(a["animal_id"] for a in alertas) == ["A1", "A2"]
        assert not any(a["alerta_aislamiento"] for a in alertas)

    def test_vigencia_del_request_reemplaza_la_por_defecto(self):
        puntos = [
            make_punto("A1", -34.0, -59.0, "2024-01-01T12:00:00Z"),
            make_punto("A2", -34.0001, -59.0, "2024-01-01T12:00:00Z"),
            make_punto("A3", -34.02, -59.0, "2024-01-01T10:00:00Z"),
        ]
        response = client.post(ENDPOINT, json={"historial_7d": puntos, "vigencia_posicion_s": 3 * 3600})

        assert response.status_code == 200
        alertas = {a["animal_id"]: a for a in response.json()["alertas"]}
        assert alertas["A3"]["alerta_aislamiento"] is True


# ===========================================================================
# GRUPO PRINCIPAL - Historia de usuario: Identificar animales alejados del grupo
# ===========================================================================

class TestGrupoPrincipal:
    """Tests de los criterios de aceptación de la historia de usuario:
    'Identificar los animales que se alejaron del grupo'.
    """

    def test_subgrupo_alejado_junto_alerta_como_alejado_no_cohesionado(self):
        """
        CA: Un subgrupo que se aleja junto se reconoce como alejado y NO
        como cohesionado.

        5 animales juntos (grupo principal) + 2 que se alejaron juntos.
        Los 2 del subgrupo deben tener alerta_aislamiento=True incluso
        estando a menos del umbral entre sí.
        """
        # Grupo principal: 5 animales apiados
        grupo_lat = -34.0
        lng = -59.0
        puntos = [make_punto(f"G{i}", grupo_lat + i * 0.0001, lng) for i in range(5)]

        # Subgrupo alejado: 2 animales juntos entre sí pero lejos del grupo
        # ~2 km al sur (< umbral entre ellos, > umbral del grupo principal)
        subgrupo_lat = -34.020
        puntos += [
            make_punto("S1", subgrupo_lat,        lng),
            make_punto("S2", subgrupo_lat + 0.0001, lng),  # ~11 m de S1
        ]

        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        alertas = {a["animal_id"]: a for a in response.json()["alertas"]}

        # Los del grupo principal no alertan
        for i in range(5):
            assert alertas[f"G{i}"]["alerta_aislamiento"] is False, \
                f"G{i} no debería alertar (está en el grupo principal)"

        # El subgrupo alerta COMPLETO, aunque S1 y S2 estén juntos entre sí
        assert alertas["S1"]["alerta_aislamiento"] is True, \
            "S1 debe alertar: el subgrupo se alejó del grupo principal"
        assert alertas["S2"]["alerta_aislamiento"] is True, \
            "S2 debe alertar: aunque S2 esté cerca de S1, ambos están alejados del grupo"

    def test_grupo_principal_es_el_de_mayor_cantidad(self):
        """
        CA: Si el rodeo se divide, el grupo principal es el de mayor cantidad.

        7 animales juntos (principal) vs 3 animales juntos (subgrupo).
        Los 3 del subgrupo alertan; los 7 no.
        """
        lng = -59.0

        # Grupo grande: 7 animales
        puntos = [make_punto(f"P{i}", -34.0 + i * 0.0001, lng) for i in range(7)]

        # Subgrupo chico: 3 animales a ~5 km
        puntos += [make_punto(f"Q{i}", -34.05 + i * 0.0001, lng) for i in range(3)]

        payload = {"historial_7d": puntos}
        response = client.post(ENDPOINT, json=payload)

        assert response.status_code == 200
        alertas = {a["animal_id"]: a for a in response.json()["alertas"]}

        for i in range(7):
            assert alertas[f"P{i}"]["alerta_aislamiento"] is False, \
                f"P{i} no debe alertar (grupo principal)"

        for i in range(3):
            assert alertas[f"Q{i}"]["alerta_aislamiento"] is True, \
                f"Q{i} debe alertar (fuera del grupo principal)"

    def test_grupo_principal_expuesto_en_funcion_interna(self):
        """
        Test unitario de _grupo_principal: el cluster más grande es el retornado;
        en empate retorna None.
        """
        # Caso: cluster de 3 + cluster de 1 -> retorna el de 3
        posiciones = {
            "A": (-34.0000, -59.0),
            "B": (-34.0001, -59.0),  # ~11 m de A -> mismo cluster
            "C": (-34.0002, -59.0),  # ~22 m de A -> mismo cluster
            "D": (-36.0,    -59.0),  # lejos -> cluster propio
        }
        assert main_app._grupo_principal(posiciones, umbral_m=100.0) == {"A", "B", "C"}

        # Caso empate: 2 clusters de tamaño 1 -> None
        posiciones_empate = {"X": (-34.0, -59.0), "Y": (-36.0, -59.0)}
        assert main_app._grupo_principal(posiciones_empate, umbral_m=100.0) is None

    def test_clasificar_marca_fuera_del_grupo_principal(self):
        posiciones = {
            "A": (-34.0000, -59.0),
            "B": (-34.0001, -59.0),
            "C": (-34.0002, -59.0),
            "D": (-34.0200, -59.0),  # ~2.2 km del resto
        }
        clasificacion = main_app._clasificar(posiciones, umbral_m=100.0)

        assert {a for a, (_, alejado) in clasificacion.items() if alejado} == {"D"}
        # La distancia se mide contra el centroide del grupo principal (B)
        assert clasificacion["B"][0] == pytest.approx(0.0, abs=1e-3)

    def test_clasificar_en_empate_compara_contra_el_resto(self):
        """Sin grupo principal, alejado = distancia al centroide del resto > umbral."""
        clasificacion = main_app._clasificar({"X": (-34.0, -59.0), "Y": (-36.0, -59.0)}, umbral_m=100.0)

        assert clasificacion["X"][1] is True
        assert clasificacion["Y"][1] is True

    def test_clasificar_un_animal_o_ninguno(self):
        assert main_app._clasificar({"A": (-34.0, -59.0)}, umbral_m=100.0) == {"A": (0.0, False)}
        assert main_app._clasificar({}, umbral_m=100.0) == {}


# ===========================================================================
# TIEMPO FUERA DEL GRUPO - Historia de usuario: Calcular el tiempo acumulado
# ===========================================================================

INTERVALO_S = 300  # Debe coincidir con INTERVALO_COHESION_S en main.py
T0 = datetime(2024, 1, 1, tzinfo=timezone.utc)  # alineado al inicio de un intervalo
SUR_2KM = -34.02  # ~2.2 km al sur del rodeo


def en_intervalo(i: int, segundos: int = 0) -> str:
    return (T0 + timedelta(seconds=i * INTERVALO_S + segundos)).isoformat()


def serie(animal_id: str, lat: float, intervalos, segundos: int = 0, lng: float = -59.0):
    """Una posición por intervalo, fija en (lat, lng)."""
    return [make_punto(animal_id, lat, lng, en_intervalo(i, segundos)) for i in intervalos]


def rodeo(intervalos, n: int = 5, segundos: int = 0):
    """n animales G0..Gn-1 a ~11 m entre sí."""
    return [p for i in range(n) for p in serie(f"G{i}", -34.0 + i * 0.0001, intervalos, segundos)]


def tiempos(puntos, **extra):
    response = client.post(ENDPOINT, json={"historial_7d": puntos, **extra})
    assert response.status_code == 200
    return {a["animal_id"]: a["tiempo_fuera_grupo_s"] for a in response.json()["alertas"]}


class TestTiempoFueraGrupo:
    """El historial se agrupa en intervalos de 5 min; cada intervalo fuera del grupo suma 300 s."""

    def test_rodeo_cohesionado_no_acumula_tiempo(self):
        t = tiempos(rodeo(range(6)))

        assert all(v == 0.0 for v in t.values())

    def test_animal_alejado_acumula_solo_los_intervalos_fuera(self):
        puntos = rodeo(range(6))
        puntos += serie("A", -34.0002, range(0, 3))  # junto al rodeo
        puntos += serie("A", SUR_2KM, range(3, 6))   # alejado
        t = tiempos(puntos)

        assert t["A"] == 3 * INTERVALO_S
        assert all(t[f"G{i}"] == 0.0 for i in range(5))

    def test_subgrupo_alejado_junto_acumula_tiempo(self):
        puntos = rodeo(range(4))
        puntos += serie("S1", SUR_2KM, range(4))
        puntos += serie("S2", SUR_2KM + 0.0001, range(4))  # ~11 m de S1
        t = tiempos(puntos)

        assert t["S1"] == t["S2"] == 4 * INTERVALO_S
        assert all(t[f"G{i}"] == 0.0 for i in range(5))

    def test_huecos_sin_datos_no_suman(self):
        """A está alejado todo el tiempo pero no transmite en los intervalos 2 y 3."""
        puntos = rodeo(range(6))
        puntos += serie("A", SUR_2KM, [0, 1, 4, 5])
        t = tiempos(puntos)

        assert t["A"] == 4 * INTERVALO_S

    def test_caravanas_desfasadas_dentro_del_intervalo_se_comparan_juntas(self):
        """
        El rodeo transmite al segundo 5 y A al segundo 290 de cada intervalo.
        Caen en el mismo intervalo, así que A se compara contra el rodeo.
        """
        puntos = rodeo(range(3), segundos=5)
        puntos += serie("A", SUR_2KM, range(3), segundos=290)
        t = tiempos(puntos)

        assert t["A"] == 3 * INTERVALO_S

    def test_umbral_del_request_cambia_el_tiempo(self):
        """A está a ~55 m de G1: dentro del grupo con 100 m, afuera con 25 m."""
        puntos = rodeo(range(3), n=2)
        puntos += serie("A", -34.0001 - 0.0005, range(3))

        assert tiempos(puntos)["A"] == 0.0
        assert tiempos(puntos, umbral_aislamiento_m=25.0)["A"] == 3 * INTERVALO_S

    def test_subgrupo_cercano_alerta_y_acumula_tiempo(self):
        """
        Regresión: 6 animales juntos y un subgrupo de 4 a ~140 m. Contra el
        centroide de "todos los demás" el subgrupo quedaba a ~93 m y no sumaba
        tiempo aunque alertaba. Alerta y tiempo deben salir del mismo cálculo.
        """
        puntos = rodeo(range(2), n=6)  # G0..G5 entre -34.0000 y -34.0005
        borde_sur = -34.0005 - 140 / 111_195
        puntos += [p for i in range(4) for p in serie(f"S{i}", borde_sur - i * 0.0001, range(2))]
        response = client.post(ENDPOINT, json={"historial_7d": puntos})

        assert response.status_code == 200
        for alerta in response.json()["alertas"]:
            alejado = alerta["animal_id"].startswith("S")
            assert alerta["alerta_aislamiento"] is alejado
            assert alerta["tiempo_fuera_grupo_s"] == (2 * INTERVALO_S if alejado else 0.0)


def continuos(puntos, **extra):
    response = client.post(ENDPOINT, json={"historial_7d": puntos, **extra})
    assert response.status_code == 200
    return {a["animal_id"]: a["tiempo_fuera_continuo_s"] for a in response.json()["alertas"]}


class TestTiempoFueraContinuo:
    """Tramo final fuera del grupo sin volver, contado hacia atrás desde el último intervalo con datos del animal."""

    def test_alejado_en_los_ultimos_intervalos(self):
        puntos = rodeo(range(6))
        puntos += serie("A", -34.0002, range(0, 3))  # junto al rodeo
        puntos += serie("A", SUR_2KM, range(3, 6))   # alejado

        assert continuos(puntos)["A"] == 3 * INTERVALO_S
        assert tiempos(puntos)["A"] == 3 * INTERVALO_S

    def test_volver_al_grupo_corta_el_tramo(self):
        puntos = rodeo(range(6))
        puntos += serie("A", SUR_2KM, range(0, 3))
        puntos += serie("A", -34.0002, range(3, 6))

        assert continuos(puntos)["A"] == 0.0
        assert tiempos(puntos)["A"] == 3 * INTERVALO_S

    def test_solo_cuenta_el_ultimo_tramo(self):
        puntos = rodeo(range(6))
        puntos += serie("A", SUR_2KM, [0, 1])
        puntos += serie("A", -34.0002, [2])
        puntos += serie("A", SUR_2KM, range(3, 6))

        assert continuos(puntos)["A"] == 3 * INTERVALO_S
        assert tiempos(puntos)["A"] == 5 * INTERVALO_S

    def test_huecos_sin_datos_no_cortan_el_tramo(self):
        """A está alejado todo el tiempo pero no transmite en los intervalos 2 y 3."""
        puntos = rodeo(range(6))
        puntos += serie("A", SUR_2KM, [0, 1, 4, 5])

        assert continuos(puntos)["A"] == 4 * INTERVALO_S

    def test_rodeo_cohesionado_no_suma(self):
        assert all(v == 0.0 for v in continuos(rodeo(range(6))).values())


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

    @pytest.mark.parametrize("umbral", [0, -50.0])
    def test_umbral_no_positivo_devuelve_422(self, umbral):
        puntos = [make_punto("A1", -34.0, -59.0), make_punto("A2", -34.0, -59.0)]
        response = client.post(ENDPOINT, json={"umbral_aislamiento_m": umbral, "historial_7d": puntos})

        assert response.status_code == 422
        assert response.json()["detail"][0]["loc"] == ["body", "umbral_aislamiento_m"]

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
