package dev.vigia.backend.util;

import dev.vigia.backend.dto.CoordenadaDTO;
import dev.vigia.backend.exception.PoligonoInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GeometryUtils — Tests Unitarios")
class GeometryUtilsTest {

    @Test
    @DisplayName("Debe crear un polígono válido y respetar el orden JTS (longitud = x, latitud = y)")
    void debeCrearPoligonoValidoConOrdenLonLat() {
        // Cuadrado en WGS 84
        List<CoordenadaDTO> coords = List.of(
                new CoordenadaDTO(-34.0, -58.0), // lat, lon
                new CoordenadaDTO(-34.0, -57.0),
                new CoordenadaDTO(-35.0, -57.0),
                new CoordenadaDTO(-35.0, -58.0)
        );

        Polygon polygon = GeometryUtils.crearPoligono(coords);

        assertThat(polygon).isNotNull();
        assertThat(polygon.getSRID()).isEqualTo(4326);
        assertThat(polygon.isValid()).isTrue();

        // Verificar que Coordinate.x = longitud (-58.0) y Coordinate.y = latitud (-34.0)
        Coordinate[] jtsCoordinates = polygon.getCoordinates();
        // 4 vértices + 1 de cierre = 5 puntos
        assertThat(jtsCoordinates).hasSize(5);
        assertThat(jtsCoordinates[0].x).isEqualTo(-58.0);
        assertThat(jtsCoordinates[0].y).isEqualTo(-34.0);
        // Verificar cierre automático
        assertThat(jtsCoordinates[4].equals2D(jtsCoordinates[0])).isTrue();
    }

    @Test
    @DisplayName("Debe rechazar polígono con menos de 3 vértices")
    void debeRechazarPoligonoConMenosDe3Vertices() {
        List<CoordenadaDTO> coords = List.of(
                new CoordenadaDTO(-34.0, -58.0),
                new CoordenadaDTO(-34.0, -57.0)
        );

        assertThatThrownBy(() -> GeometryUtils.crearPoligono(coords))
                .isInstanceOf(PoligonoInvalidoException.class)
                .hasMessageContaining("al menos 3 coordenadas");
    }

    @Test
    @DisplayName("Debe rechazar polígono autointersectante (figura de 8)")
    void debeRechazarPoligonoAutointersectante() {
        // Polígono con forma de "8" (cruza sobre sí mismo)
        List<CoordenadaDTO> coords = List.of(
                new CoordenadaDTO(0.0, 0.0),
                new CoordenadaDTO(1.0, 1.0),
                new CoordenadaDTO(0.0, 1.0),
                new CoordenadaDTO(1.0, 0.0)
        );

        assertThatThrownBy(() -> GeometryUtils.crearPoligono(coords))
                .isInstanceOf(PoligonoInvalidoException.class)
                .hasMessageContaining("no es válida");
    }

    @Test
    @DisplayName("Debe extraer correctamente las coordenadas DTO desde un Polygon JTS")
    void debeExtraerCoordenadasCorrectamente() {
        List<CoordenadaDTO> originales = List.of(
                new CoordenadaDTO(-34.0, -58.0),
                new CoordenadaDTO(-34.0, -57.0),
                new CoordenadaDTO(-35.0, -57.0),
                new CoordenadaDTO(-35.0, -58.0),
                new CoordenadaDTO(-34.0, -58.0) // ya cerrado
        );

        Polygon polygon = GeometryUtils.crearPoligono(originales);
        List<CoordenadaDTO> extraidas = GeometryUtils.extraerCoordenadas(polygon);

        assertThat(extraidas).hasSize(5);
        assertThat(extraidas.get(0).getLatitud()).isEqualTo(-34.0);
        assertThat(extraidas.get(0).getLongitud()).isEqualTo(-58.0);
    }
}
