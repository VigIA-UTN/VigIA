package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H0004 - Alta manual de animales, validación de ID único — IT")
class AltaAnimalIT extends GestionRodeoItBase {

    @Test
    @DisplayName("H0004-01 - Alta manual - Datos válidos con peso inicial y caravana")
    void H0004_01_altaValidaConPesoYCaravana() throws Exception {
        Map<String, Object> cuerpo = cuerpoAnimal("A-201", EST_A, "Rodeo A1");
        cuerpo.put("alias", "Nueva");
        cuerpo.put("pesoInicial", 350.0);
        cuerpo.put("caravanaId", idCaravana("FX-A-L1"));

        MvcResult resultado = altaAnimal("productorA", cuerpo);

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        JsonNode creado = leer(resultado);
        assertThat(creado.get("idCaravanaOficial").asText()).isEqualTo("A-201");
        assertThat(creado.get("estadoActual").asText()).isEqualTo("ACTIVO");
        assertThat(creado.get("rodeoLoteNombre").asText()).isEqualTo("Rodeo A1");
        assertThat(creado.get("caravanaCodigo").asText()).isEqualTo("FX-A-L1");

        Long id = creado.get("id").asLong();
        assertThat(jdbcTemplate.queryForList("SELECT valor_kg FROM registros_peso WHERE animal_id = ?",
                Double.class, id)).containsExactly(350.0);
        assertThat(estadoCaravana("FX-A-L1")).isEqualTo("VINCULADO");
    }

    @Test
    @DisplayName("H0004-02 - Unicidad - Identificador repetido en el mismo establecimiento")
    void H0004_02_identificadorRepetidoEnElEstablecimiento() throws Exception {
        MvcResult resultado = altaAnimal("productorA", cuerpoAnimal("A-101", EST_A, "Rodeo A1"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("El identificador ya se encuentra registrado en este establecimiento.");
        assertThat(cantidadAnimales(EST_A)).isEqualTo(6);
    }

    @Test
    @DisplayName("H0004-03 - Unicidad - Mismo identificador en otro establecimiento")
    void H0004_03_mismoIdentificadorEnOtroEstablecimiento() throws Exception {
        MvcResult resultado = altaAnimal("productorB", cuerpoAnimal("A-101", EST_B, "Rodeo B1"));

        assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        assertThat(cantidadAnimales(EST_B)).isEqualTo(7);
        assertThat(cantidadAnimales(EST_A)).isEqualTo(6);
    }

    @Test
    @DisplayName("H0004-04 - Validaciones - Campos obligatorios y formato")
    void H0004_04_camposObligatoriosYFormato() throws Exception {
        Map<String, Map<String, Object>> variantes = new LinkedHashMap<>();
        variantes.put("sin identificador", sin(cuerpoAnimal("A-202", EST_A, "Rodeo A1"), "idCaravanaOficial"));
        variantes.put("sin fecha de nacimiento", sin(cuerpoAnimal("A-203", EST_A, "Rodeo A1"), "fechaNacimiento"));
        Map<String, Object> fechaFutura = cuerpoAnimal("A-204", EST_A, "Rodeo A1");
        fechaFutura.put("fechaNacimiento", LocalDate.now().plusDays(1).toString());
        variantes.put("fecha de nacimiento futura", fechaFutura);
        variantes.put("sin rodeo", sin(cuerpoAnimal("A-205", EST_A, "Rodeo A1"), "rodeoLoteId"));
        Map<String, Object> pesoNegativo = cuerpoAnimal("A-206", EST_A, "Rodeo A1");
        pesoNegativo.put("pesoInicial", -10.0);
        variantes.put("peso inicial negativo", pesoNegativo);

        for (Map.Entry<String, Map<String, Object>> variante : variantes.entrySet()) {
            assertThat(altaAnimal("productorA", variante.getValue()).getResponse().getStatus())
                    .as(variante.getKey()).isEqualTo(400);
        }
        assertThat(cantidadAnimales(EST_A)).isEqualTo(6);
    }

    @Test
    @DisplayName("H0004-05 - Validaciones - Rodeo ajeno, rodeo dado de baja y alias repetido")
    void H0004_05_rodeoAjenoRodeoInactivoYAliasRepetido() throws Exception {
        Map<String, Object> rodeoAjeno = cuerpoAnimal("A-207", EST_A, "Rodeo A1");
        rodeoAjeno.put("rodeoLoteId", idRodeo(EST_B, "Rodeo B1"));
        MvcResult resultado = altaAnimal("productorA", rodeoAjeno);
        // Un rodeo de otro establecimiento es un acceso cruzado: 403, como pide RNF03 (ver AccesoCruzadoIT).
        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(texto(resultado)).contains("El rodeo no pertenece al establecimiento.");

        jdbcTemplate.update("UPDATE rodeo_lote SET activo = 0 WHERE id = ?", idRodeo(EST_A, "Rodeo A2"));
        resultado = altaAnimal("productorA", cuerpoAnimal("A-208", EST_A, "Rodeo A2"));
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("El rodeo/lote no está activo.");

        Map<String, Object> aliasRepetido = cuerpoAnimal("A-209", EST_A, "Rodeo A1");
        aliasRepetido.put("alias", "Calibrada A");
        resultado = altaAnimal("productorA", aliasRepetido);
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(texto(resultado)).isEqualTo("El nombre/alias ya se encuentra registrado en este establecimiento.");

        assertThat(cantidadAnimales(EST_A)).isEqualTo(6);
    }

    private static Map<String, Object> sin(Map<String, Object> cuerpo, String campo) {
        cuerpo.remove(campo);
        return cuerpo;
    }
}
