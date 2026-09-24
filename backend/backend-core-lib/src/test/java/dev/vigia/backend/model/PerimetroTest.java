package dev.vigia.backend.model;

import dev.vigia.backend.dto.CoordenadaDTO;
import dev.vigia.backend.util.GeometryUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Perimetro — Tests de Dominio")
class PerimetroTest {

    private Polygon crearPoligonoValido() {
        return GeometryUtils.crearPoligono(List.of(
                new CoordenadaDTO(-34.0, -58.0),
                new CoordenadaDTO(-34.0, -57.0),
                new CoordenadaDTO(-35.0, -57.0),
                new CoordenadaDTO(-35.0, -58.0)));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Debe instanciar Perímetro con estado activo y validar sus invariantes")
    void debeInstanciarPerimetroValido() {
        Establecimiento est = new Establecimiento();
        est.setId(1L);
        est.setNombre("Establecimiento La Juanita");

        Polygon poly = crearPoligonoValido();
        Perimetro perimetro = new Perimetro(est, "Potrero Norte", "Perímetro principal", poly);

        assertThat(perimetro.getEstablecimiento()).isEqualTo(est);
        assertThat(perimetro.getNombre()).isEqualTo("Potrero Norte");
        assertThat(perimetro.getDescripcion()).isEqualTo("Perímetro principal");
        assertThat(perimetro.isActivo()).isTrue();
        assertThat(perimetro.getArea()).isEqualTo(poly);
    }

    @Test
    @DisplayName("Debe fallar si el establecimiento o nombre son nulos")
    void debeFallarSiParametrosSonNulos() {
        Polygon poly = crearPoligonoValido();
        assertThatThrownBy(() -> new Perimetro(null, "Test", poly))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser nulo");

        Establecimiento est = new Establecimiento();
        assertThatThrownBy(() -> new Perimetro(est, "   ", poly))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede estar vacío");
    }

    @Test
    @DisplayName("Debe permitir activar y desactivar explícitamente")
    void debeActivarYDesactivar() {
        Establecimiento est = new Establecimiento();
        Perimetro perimetro = new Perimetro(est, "Potrero", crearPoligonoValido());

        perimetro.desactivar();
        assertThat(perimetro.isActivo()).isFalse();
        assertThat(perimetro.getFechaModificacion()).isNotNull();

        perimetro.activar();
        assertThat(perimetro.isActivo()).isTrue();
    }

    @Test
    @DisplayName("Debe permitir asignar y remover lotes")
    void debeAsignarYRemoverLotes() {
        Establecimiento est = new Establecimiento();
        Perimetro perimetro = new Perimetro(est, "Potrero", crearPoligonoValido());

        RodeoLote lote1 = new RodeoLote(est);
        lote1.setId(10L);
        lote1.setNombre("Lote Invernada");

        RodeoLote lote2 = new RodeoLote(est);
        lote2.setId(20L);
        lote2.setNombre("Lote Cría");

        perimetro.asignarLote(lote1);
        perimetro.asignarLote(lote2);

        assertThat(perimetro.getLotes()).containsExactlyInAnyOrder(lote1, lote2);

        perimetro.removerLote(lote1);
        assertThat(perimetro.getLotes()).containsExactly(lote2);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Debe actualizar polígono completo validándolo")
    void debeActualizarPoligonoCompleto() {
        Establecimiento est = new Establecimiento();
        Perimetro perimetro = new Perimetro(est, "Potrero", crearPoligonoValido());

        Polygon nuevoPoly = GeometryUtils.crearPoligono(List.of(
                new CoordenadaDTO(-30.0, -60.0),
                new CoordenadaDTO(-30.0, -59.0),
                new CoordenadaDTO(-31.0, -59.0),
                new CoordenadaDTO(-31.0, -60.0)));

        perimetro.actualizarArea(nuevoPoly);
        assertThat(perimetro.getArea()).isEqualTo(nuevoPoly);
        assertThat(perimetro.getFechaModificacion()).isNotNull();
    }

    @Test
    @DisplayName("Debe crearse con tolerancia 0 y zona no estricta por defecto")
    void debeTenerUmbralPorDefecto() {
        Perimetro perimetro = new Perimetro(new Establecimiento(), "Potrero", crearPoligonoValido());

        assertThat(perimetro.getDistanciaToleranciaMetros()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(perimetro.isZonaEstricta()).isFalse();
        assertThat(perimetro.getToleranciaEfectivaMetros()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Debe configurar tolerancia y zona estricta")
    void debeConfigurarUmbral() {
        Perimetro perimetro = new Perimetro(new Establecimiento(), "Potrero", crearPoligonoValido());

        perimetro.configurarUmbral(new BigDecimal("25.50"), true);

        assertThat(perimetro.getDistanciaToleranciaMetros()).isEqualByComparingTo("25.50");
        assertThat(perimetro.isZonaEstricta()).isTrue();
    }

    @Test
    @DisplayName("Debe fallar si la tolerancia es negativa o nula")
    void debeFallarConToleranciaInvalida() {
        Perimetro perimetro = new Perimetro(new Establecimiento(), "Potrero", crearPoligonoValido());

        assertThatThrownBy(() -> perimetro.configurarUmbral(new BigDecimal("-1"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser negativa");
        assertThatThrownBy(() -> perimetro.configurarUmbral(null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser nula");

        assertThat(perimetro.getDistanciaToleranciaMetros()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("La tolerancia efectiva debe ignorarse en zona estricta")
    void toleranciaEfectivaIgnoradaEnZonaEstricta() {
        Perimetro perimetro = new Perimetro(new Establecimiento(), "Potrero", crearPoligonoValido());

        perimetro.configurarUmbral(new BigDecimal("50"), false);
        assertThat(perimetro.getToleranciaEfectivaMetros()).isEqualByComparingTo("50");

        perimetro.configurarUmbral(new BigDecimal("50"), true);
        assertThat(perimetro.getToleranciaEfectivaMetros()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(perimetro.getDistanciaToleranciaMetros()).isEqualByComparingTo("50");
    }
}
