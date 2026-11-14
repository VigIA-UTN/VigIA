package dev.vigia.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * RNF02 (defecto del Ciclo 1, fila 431): {@code /animales} y {@code /animales/mapa} empeoraban más del 25 % entre 10 y
 * 200 animales. La cantidad de consultas SQL de un pedido no puede depender de cuántos animales tiene el
 * establecimiento: si crece con ellos, hay una por animal (N+1).
 */
@DisplayName("RNF02 - Consultas SQL por pedido, independientes del tamaño del rodeo — IT")
class ConsultasPorPedidoIT extends MapaDashboardItBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("GET /animales hace las mismas consultas con 20 animales más")
    void listaDeAnimales() throws Exception {
        int animalesAntes = leer(animales("productorA")).size();
        long antes = consultas(() -> animales("productorA"));
        agregarAnimales(20);
        long despues = consultas(() -> animales("productorA"));

        assertThat(leer(animales("productorA"))).hasSize(animalesAntes + 20);
        assertThat(antes).isPositive();
        assertThat(despues).as("consultas con %s animales más", 20).isEqualTo(antes);
    }

    @Test
    @DisplayName("GET /animales/mapa hace las mismas consultas con 20 animales más")
    void mapaDePosiciones() throws Exception {
        int animalesAntes = mapaOk("productorA", EST_A).size();
        long antes = consultas(() -> mapaOk("productorA", EST_A));
        agregarAnimales(20);
        long despues = consultas(() -> mapaOk("productorA", EST_A));

        assertThat(mapaOk("productorA", EST_A)).hasSize(animalesAntes + 20);
        assertThat(antes).isPositive();
        assertThat(despues).as("consultas con %s animales más", 20).isEqualTo(antes);
    }

    private interface Pedido {
        void hacer() throws Exception;
    }

    /** Sentencias JDBC que preparó Hibernate durante el pedido (el login va antes, en bearer()). */
    private long consultas(Pedido pedido) throws Exception {
        bearer("productorA");
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        estadisticas.clear();
        try {
            pedido.hacer();
            return estadisticas.getPrepareStatementCount();
        } finally {
            estadisticas.setStatisticsEnabled(false);
        }
    }

    private MvcResult animales(String clave) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/v1/animales")
                        .param("establecimientoId", idEstablecimiento(EST_A).toString())
                        .header("Authorization", bearer(clave)))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(200);
        return resultado;
    }

    /** Animales activos nuevos en Fixture A, cada uno con su caravana, copiados de A-101. */
    private void agregarAnimales(int cantidad) {
        jdbcTemplate.update("""
                WITH base AS (SELECT a.*, c.estado_sensor, c.establecimiento_id AS est_caravana
                              FROM animales a JOIN caravanas c ON c.id = a.caravana_id WHERE a.id = ?),
                     k AS (INSERT INTO caravanas (codigo, dev_eui, fecha_asignacion, estado_sensor, establecimiento_id)
                           SELECT 'NP-' || g, 'NP-' || g, current_date - 30, b.estado_sensor, b.est_caravana
                           FROM base b, generate_series(1, ?) g
                           RETURNING id, codigo)
                INSERT INTO animales (establecimiento_id, rodeo_lote_id, id_caravana_oficial, fecha_nacimiento, raza,
                                      estado_actual, fecha_alta, caravana_id)
                SELECT b.establecimiento_id, b.rodeo_lote_id, k.codigo, b.fecha_nacimiento, b.raza, b.estado_actual,
                       b.fecha_alta, k.id
                FROM base b, k
                """, idAnimal(EST_A, "A-101"), cantidad);
    }
}
