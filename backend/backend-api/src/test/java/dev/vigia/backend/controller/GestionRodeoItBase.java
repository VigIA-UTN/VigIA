package dev.vigia.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de los IT de la regresión E2 (gestión del rodeo): animales, rodeos/lotes, caravanas, carga masiva, estado
 * reproductivo, peso y actividad. Suma a {@link SeguridadItBase} los accesos a los datos del escenario por su nombre
 * visible: animales por identificador ({@code A-101}), rodeos por nombre ({@code Rodeo A1}) y caravanas por código
 * ({@code FX-A-01}, {@code FX-A-L1}).
 * <p>
 * Todo lo que crean los tests vive en Fixture A o B, así que {@code limpiar.sql} lo borra en la próxima carga.
 */
abstract class GestionRodeoItBase extends SeguridadItBase {

    // ── Datos del escenario por nombre visible ─────────────────────────────────

    protected Long idAnimal(String establecimiento, String identificador) {
        return jdbcTemplate.queryForObject("SELECT a.id FROM animales a JOIN establecimientos e "
                + "ON e.id = a.establecimiento_id WHERE e.nombre = ? AND a.id_caravana_oficial = ?",
                Long.class, establecimiento, identificador);
    }

    protected Long idRodeo(String establecimiento, String nombre) {
        return jdbcTemplate.queryForObject("SELECT l.id FROM rodeo_lote l JOIN establecimientos e "
                + "ON e.id = l.establecimiento_id WHERE e.nombre = ? AND l.nombre = ?",
                Long.class, establecimiento, nombre);
    }

    protected Long idCaravana(String codigo) {
        return jdbcTemplate.queryForObject("SELECT id FROM caravanas WHERE codigo = ?", Long.class, codigo);
    }

    protected String estadoCaravana(String codigo) {
        return jdbcTemplate.queryForObject("SELECT estado_sensor FROM caravanas WHERE codigo = ?", String.class,
                codigo);
    }

    protected int cantidadAnimales(String establecimiento) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM animales a JOIN establecimientos e "
                + "ON e.id = a.establecimiento_id WHERE e.nombre = ?", Integer.class, establecimiento);
    }

    // ── Animales por la API ──────────────────────────────────────────────────

    /** Cuerpo de alta/edición válido; cada test pisa lo que prueba. */
    protected Map<String, Object> cuerpoAnimal(String identificador, String establecimiento, String rodeo) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idCaravanaOficial", identificador);
        cuerpo.put("alias", "Alias " + identificador);
        cuerpo.put("fechaNacimiento", LocalDate.of(2024, 3, 1).toString());
        cuerpo.put("raza", "Angus");
        cuerpo.put("rodeoLoteId", idRodeo(establecimiento, rodeo));
        cuerpo.put("establecimientoId", idEstablecimiento(establecimiento));
        return cuerpo;
    }

    protected MvcResult altaAnimal(String clave, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/animales")
                        .header("Authorization", bearer(clave))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andReturn();
    }

    /** Cuerpo de la respuesta como texto: los errores de negocio de estos endpoints vuelven como mensaje plano. */
    protected String texto(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
