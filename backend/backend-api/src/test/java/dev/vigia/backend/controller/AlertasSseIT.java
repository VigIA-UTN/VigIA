package dev.vigia.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import dev.vigia.backend.BackendApiApplication;
import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.TelemetriaEventDTO;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.service.EmailService;
import dev.vigia.backend.service.SseAlertaPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * INT03 - Contrato entre la publicación de alertas en el Fanout Exchange de RabbitMQ y el envío por SSE al frontend,
 * sobre TimescaleDB y RabbitMQ reales (Testcontainers).
 * <p>
 * Las alertas se publican en {@code alertas.exchange} como lo hace el worker y las posiciones en
 * {@code telemetria.exchange} como lo hace la ingesta; los clientes se suscriben a {@code GET /api/v1/alertas/stream} y
 * {@code GET /api/v1/animales/mapa/stream} con el JWT de un usuario del escenario. El mail de las alertas nuevas está
 * reemplazado por un mock: no hay servidor SMTP en los IT.
 * <p>
 * Los nombres de los tests llevan el caso de la hoja "Ciclo 1" del documento de Casos de Prueba (INT03-01 a INT03-05).
 */
@DisplayName("INT03 - RabbitMQ (Fanout) y SSE hacia el frontend — IT")
class AlertasSseIT extends GestionRodeoItBase {

    private static final long ESPERA_SSE_MS = 8000;
    /** Margen para que llegue un evento de más (un duplicado o una fuga a otro establecimiento) si lo hubiera. */
    private static final long MARGEN_MS = 1500;

    @MockBean
    private EmailService emailService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitListenerEndpointRegistry listeners;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("INT03-01 - Alerta por SSE - Llega al establecimiento correcto y a ningún otro")
    void INT03_01_alertaLlegaSoloAlEstablecimientoCorrecto() throws Exception {
        MvcResult veterinarioA = streamAlertas("veterinarioA", EST_A, null);
        MvcResult camperoA = streamAlertas("camperoA", EST_A, null);
        MvcResult productorB = streamAlertas("productorB", EST_B, null);

        Long alerta = crearAlerta(idAnimal(EST_A, "A-101"));
        publicarAlerta(alerta, idAnimal(EST_A, "A-101"));

        for (MvcResult cliente : List.of(veterinarioA, camperoA)) {
            List<JsonNode> eventos = esperarEventos(cliente, 1);
            assertThat(eventos).hasSize(1);
            assertThat(eventos.get(0).get("id").asLong()).isEqualTo(alerta);
            assertThat(eventos.get(0).get("animalId").asLong()).isEqualTo(idAnimal(EST_A, "A-101"));
            assertThat(idsDeEventos(cliente)).containsExactly(String.valueOf(alerta));
        }
        assertThat(eventos(productorB)).isEmpty();

        // Un usuario de A no puede abrir el stream de B.
        assertThat(mockMvc.perform(pedidoStreamAlertas("productorA", EST_B, null)).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("INT03-02 - Reconexión con Last-Event-ID - Recupera solo la ventana de desconexión, sin duplicados")
    void INT03_02_reconexionConLastEventId() throws Exception {
        Long animal = idAnimal(EST_A, "A-101");
        MvcResult primeraConexion = streamAlertas("productorA", EST_A, null);
        Long recibida = crearAlerta(animal);
        publicarAlerta(recibida, animal);
        assertThat(esperarEventos(primeraConexion, 1)).hasSize(1);
        String ultimoId = idsDeEventos(primeraConexion).get(0);

        // Desconectado: el worker genera dos alertas de A y una de B, y nadie las recibe en vivo.
        primeraConexion.getRequest().getAsyncContext().complete();
        Long perdida1 = crearAlerta(idAnimal(EST_A, "A-102"));
        publicarAlerta(perdida1, idAnimal(EST_A, "A-102"));
        Long deB = crearAlerta(idAnimal(EST_B, "B-101"));
        publicarAlerta(deB, idAnimal(EST_B, "B-101"));
        Long perdida2 = crearAlerta(idAnimal(EST_A, "A-103"));
        publicarAlerta(perdida2, idAnimal(EST_A, "A-103"));
        // Que el Fanout las haya entregado (y descartado, sin clientes de A) antes de reconectar.
        Thread.sleep(MARGEN_MS);

        // Reconecta como el navegador: misma URL y el header Last-Event-ID con el último id recibido.
        MvcResult reconexion = streamAlertas("productorA", EST_A, ultimoId);
        assertThat(esperarEventos(reconexion, 2)).hasSize(2);
        assertThat(idsDeEventos(reconexion)).containsExactly(String.valueOf(perdida1), String.valueOf(perdida2));

        // Después de la reconexión sigue en vivo, y lo recuperado no vuelve a llegar.
        Long nueva = crearAlerta(animal);
        publicarAlerta(nueva, animal);
        assertThat(esperarEventos(reconexion, 3)).hasSize(3);
        assertThat(idsDeEventos(reconexion)).containsExactly(String.valueOf(perdida1), String.valueOf(perdida2),
                String.valueOf(nueva));
        assertThat(idsDeEventos(reconexion)).doesNotContain(String.valueOf(recibida), String.valueOf(deB));
    }

    @Test
    @DisplayName("INT03-03 - Dos instancias de backend-api - La alerta llega una sola vez al cliente")
    void INT03_03_dosInstanciasEntreganUnaSolaVez() throws Exception {
        try (ConfigurableApplicationContext segunda = segundaInstancia()) {
            int puerto = ((ServletWebServerApplicationContext) segunda).getWebServer().getPort();
            Long animal = idAnimal(EST_A, "A-101");

            // Un cliente en cada instancia; cada instancia tiene su propia cola ligada al exchange.
            Long estA = idEstablecimiento(EST_A);
            MvcResult enLaPrimera = streamAlertas("veterinarioA", EST_A, null);
            List<String> enLaSegunda = new CopyOnWriteArrayList<>();
            CompletableFuture<HttpResponse<Stream<String>>> conexion = streamHttp(puerto,
                    "/api/v1/alertas/stream?establecimientoId=" + estA, bearer("camperoA"), enLaSegunda);
            try {
                esperar(() -> clientesSse(segunda, estA) == 1);
                esperarConsumidores(segunda.getBean(RabbitListenerEndpointRegistry.class));

                Long alerta = crearAlerta(animal);
                publicarAlerta(alerta, animal);

                assertThat(esperarEventos(enLaPrimera, 1)).hasSize(1);
                esperar(() -> datos(enLaSegunda).size() >= 1);
                Thread.sleep(MARGEN_MS);
                assertThat(conexion.get().statusCode()).isEqualTo(200);
                assertThat(idsDeEventos(enLaPrimera)).containsExactly(String.valueOf(alerta));
                assertThat(idsHttp(enLaSegunda)).containsExactly(String.valueOf(alerta));
            } finally {
                conexion.cancel(true);
            }
        }
    }

    @Test
    @DisplayName("INT03-04 - Stream del mapa - Mismo aislamiento por establecimiento")
    void INT03_04_streamDelMapaAisladoPorEstablecimiento() throws Exception {
        Long estA = idEstablecimiento(EST_A);
        Long estB = idEstablecimiento(EST_B);
        MvcResult camperoA = streamMapa("camperoA", EST_A);
        MvcResult productorB = streamMapa("productorB", EST_B);

        publicarPosicion(idAnimal(EST_A, "A-101"), estA, -34.21);
        publicarPosicion(idAnimal(EST_B, "B-101"), estB, -34.31);

        List<JsonNode> deA = esperarEventos(camperoA, 1);
        List<JsonNode> deB = esperarEventos(productorB, 1);
        assertThat(deA).hasSize(1);
        assertThat(deA.get(0).get("establecimientoId").asLong()).isEqualTo(estA);
        assertThat(deA.get(0).get("latitud").asDouble()).isEqualTo(-34.21);
        assertThat(deB).hasSize(1);
        assertThat(deB.get(0).get("establecimientoId").asLong()).isEqualTo(estB);
        assertThat(deB.get(0).get("latitud").asDouble()).isEqualTo(-34.31);

        // Como en el stream de alertas, un usuario de A no puede abrir el de B.
        assertThat(mockMvc.perform(get("/api/v1/animales/mapa/stream")
                        .param("establecimientoId", estB.toString())
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", bearer("productorA")))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    /**
     * Simula la caída del broker con {@code rabbitmqctl stop_app} y {@code start_app}: cierra todas las conexiones y
     * borra las colas no durables (las {@code AnonymousQueue} de cada instancia), pero mantiene el puerto, como un
     * reinicio del servicio.
     */
    @Test
    @DisplayName("INT03-05 - Caída y vuelta de RabbitMQ - El consumidor se recupera solo")
    void INT03_05_caidaYVueltaDeRabbitMq() throws Exception {
        MvcResult alertasA = streamAlertas("productorA", EST_A, null);
        MvcResult mapaA = streamMapa("productorA", EST_A);
        esperarConsumidores(listeners);

        rabbitmqctl("stop_app");
        rabbitmqctl("start_app");

        // Spring reconecta, vuelve a declarar las colas y sus bindings y reinicia los consumidores, sin reiniciar la
        // aplicación ni reconectar a los clientes SSE.
        esperarConsumidores(listeners);
        Long animal = idAnimal(EST_A, "A-101");
        Long alerta = crearAlerta(animal);
        esperar(() -> {
            publicarAlerta(alerta, animal);
            return !eventos(alertasA).isEmpty();
        });
        publicarPosicion(animal, idEstablecimiento(EST_A), -34.41);

        assertThat(idsDeEventos(alertasA)).first().isEqualTo(String.valueOf(alerta));
        assertThat(esperarEventos(mapaA, 1)).isNotEmpty();
        assertThat(eventos(mapaA).get(eventos(mapaA).size() - 1).get("latitud").asDouble()).isEqualTo(-34.41);
        for (MessageListenerContainer contenedor : listeners.getListenerContainers()) {
            assertThat(contenedor.isRunning()).isTrue();
        }
    }

    // ── Alertas y posiciones ─────────────────────────────────────────────────

    /** Una alerta de celo pendiente recién generada, como la guarda el worker. */
    private Long crearAlerta(Long animalId) {
        return jdbcTemplate.queryForObject("INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, "
                + "disparador, valor_umbral, valor_detectado, estado) VALUES (?, 'Celo Detectado', 'ALTA', ?, 'CELO', "
                + "0.947, 0.95, 'PENDIENTE') RETURNING id", Long.class, animalId,
                Timestamp.valueOf(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
    }

    /** El evento que publica el worker en el Fanout cuando genera la alerta. */
    private void publicarAlerta(Long alertaId, Long animalId) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.ALERTAS_EXCHANGE, "", AlertaEventDTO.builder()
                .id(alertaId)
                .animalId(animalId)
                .tipo("Celo Detectado")
                .severidad(SeveridadAlerta.ALTA)
                .tsGeneracion(LocalDateTime.now())
                .disparador(DisparadorAlerta.CELO)
                .valorUmbral(0.947)
                .valorDetectado(0.95)
                .estado(EstadoAlerta.PENDIENTE)
                .nueva(true)
                .build());
    }

    private void publicarPosicion(Long animalId, Long establecimientoId, double latitud) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.TELEMETRIA_EXCHANGE, "", TelemetriaEventDTO.builder()
                .animalId(animalId)
                .establecimientoId(establecimientoId)
                .latitud(latitud)
                .longitud(-58.2)
                .fechaHora(LocalDateTime.now())
                .build());
    }

    // ── Streams por MockMvc (esta instancia) ─────────────────────────────────

    private MockHttpServletRequestBuilder pedidoStreamAlertas(String clave, String establecimiento,
            String lastEventId) throws Exception {
        MockHttpServletRequestBuilder pedido = get("/api/v1/alertas/stream")
                .param("establecimientoId", idEstablecimiento(establecimiento).toString())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", bearer(clave));
        return lastEventId != null ? pedido.header("Last-Event-ID", lastEventId) : pedido;
    }

    private MvcResult streamAlertas(String clave, String establecimiento, String lastEventId) throws Exception {
        MvcResult stream = mockMvc.perform(pedidoStreamAlertas(clave, establecimiento, lastEventId)).andReturn();
        assertThat(stream.getResponse().getStatus()).isEqualTo(200);
        assertThat(stream.getRequest().isAsyncStarted()).isTrue();
        return stream;
    }

    private MvcResult streamMapa(String clave, String establecimiento) throws Exception {
        MvcResult stream = mockMvc.perform(get("/api/v1/animales/mapa/stream")
                        .param("establecimientoId", idEstablecimiento(establecimiento).toString())
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", bearer(clave)))
                .andReturn();
        assertThat(stream.getResponse().getStatus()).isEqualTo(200);
        assertThat(stream.getRequest().isAsyncStarted()).isTrue();
        return stream;
    }

    /** Espera hasta tener {@code cantidad} eventos y deja un margen por si llega alguno de más. */
    private List<JsonNode> esperarEventos(MvcResult stream, int cantidad) throws Exception {
        esperar(() -> eventos(stream).size() >= cantidad);
        Thread.sleep(MARGEN_MS);
        return eventos(stream);
    }

    /** Los datos de cada evento (los heartbeats son comentarios y no cuentan). */
    private List<JsonNode> eventos(MvcResult stream) throws Exception {
        return datos(lineas(stream));
    }

    /** El campo {@code id:} de cada evento, que es lo que el navegador manda después en Last-Event-ID. */
    private List<String> idsDeEventos(MvcResult stream) throws Exception {
        return lineas(stream).stream().filter(l -> l.startsWith("id:")).map(l -> l.substring(3).trim()).toList();
    }

    private static List<String> lineas(MvcResult stream) throws Exception {
        return List.of(stream.getResponse().getContentAsString(StandardCharsets.UTF_8).split("\n"));
    }

    private List<JsonNode> datos(List<String> lineas) throws Exception {
        List<JsonNode> datos = new ArrayList<>();
        for (String linea : lineas) {
            if (linea.startsWith("data:")) {
                datos.add(objectMapper.readTree(linea.substring("data:".length())));
            }
        }
        return datos;
    }

    // ── Segunda instancia de backend-api ─────────────────────────────────────

    /**
     * Otra backend-api sobre la misma base y el mismo RabbitMQ, con servidor HTTP en un puerto libre: declara su propia
     * cola anónima ligada al Fanout, como cada réplica en producción.
     */
    private ConfigurableApplicationContext segundaInstancia() {
        // Como argumentos de línea de comandos, que pisan a los application.yaml de test (los de properties() no).
        return new SpringApplicationBuilder(BackendApiApplication.class)
                .profiles("it")
                .run(Stream.of("server.port=0", "spring.datasource.url", "spring.datasource.username",
                                "spring.datasource.password", "spring.rabbitmq.host", "spring.rabbitmq.port",
                                "spring.rabbitmq.username", "spring.rabbitmq.password")
                        .map(p -> p.contains("=") ? "--" + p : "--" + p + "=" + environment.getProperty(p))
                        .toArray(String[]::new));
    }

    /**
     * Abre un stream SSE por HTTP real y va guardando sus líneas en {@code lineas}. No espera la respuesta: Tomcat
     * manda los headers recién con el primer evento, así que el pedido queda pendiente hasta entonces.
     */
    private static CompletableFuture<HttpResponse<Stream<String>>> streamHttp(int puerto, String ruta, String bearer,
            List<String> lineas) {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Authorization", bearer)
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .build();
        CompletableFuture<HttpResponse<Stream<String>>> respuesta = HttpClient.newHttpClient()
                .sendAsync(pedido, HttpResponse.BodyHandlers.ofLines());
        respuesta.thenAcceptAsync(r -> {
            try {
                r.body().forEach(lineas::add);
            } catch (Exception e) {
                // La conexión se cerró al terminar el test.
            }
        });
        return respuesta;
    }

    /** Clientes SSE de alertas registrados en una instancia para el establecimiento. */
    private static int clientesSse(ConfigurableApplicationContext instancia, Long establecimientoId) {
        Integer clientes = ReflectionTestUtils.invokeMethod(instancia.getBean(SseAlertaPublisher.class),
                "clientesConectados", establecimientoId);
        return clientes == null ? 0 : clientes;
    }

    private static List<String> idsHttp(List<String> lineas) {
        return lineas.stream().filter(l -> l.startsWith("id:")).map(l -> l.substring(3).trim()).toList();
    }

    // ── RabbitMQ ─────────────────────────────────────────────────────────────

    /** Todos los listeners de la instancia corriendo y con consumidores activos en sus colas. */
    private static void esperarConsumidores(RabbitListenerEndpointRegistry registro) throws Exception {
        esperar(() -> registro.getListenerContainers().stream().allMatch(c -> c.isRunning()
                && (!(c instanceof SimpleMessageListenerContainer s) || s.getActiveConsumerCount() > 0)
                && (!(c instanceof AbstractMessageListenerContainer a) || a.getQueueNames().length > 0)));
    }

    /** Corre rabbitmqctl en el contenedor de RabbitMQ de los IT (el que publica el puerto AMQP de la config). */
    private void rabbitmqctl(String comando) throws Exception {
        DockerClient docker = DockerClientFactory.instance().client();
        int puertoAmqp = Integer.parseInt(environment.getProperty("spring.rabbitmq.port"));
        Container rabbit = docker.listContainersCmd().exec().stream()
                .filter(c -> Stream.of(c.getPorts()).map(ContainerPort::getPublicPort)
                        .anyMatch(p -> p != null && p == puertoAmqp))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No está el contenedor de RabbitMQ de los IT"));
        ExecCreateCmdResponse exec = docker.execCreateCmd(rabbit.getId())
                .withCmd("rabbitmqctl", comando)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();
        docker.execStartCmd(exec.getId())
                .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<>())
                .awaitCompletion(60, TimeUnit.SECONDS);
        Long salida = docker.inspectExecCmd(exec.getId()).exec().getExitCodeLong();
        assertThat(salida).as("rabbitmqctl %s", comando).isZero();
    }

    // ── Espera ──────────────────────────────────────────────────────────────

    private interface Condicion {
        boolean cumple() throws Exception;
    }

    private static void esperar(Condicion condicion) throws Exception {
        long limite = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
        while (!condicion.cumple()) {
            if (System.currentTimeMillis() > limite) {
                throw new AssertionError("No se cumplió la condición esperada en 30 s");
            }
            Thread.sleep(200);
        }
    }
}
