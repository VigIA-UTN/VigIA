package dev.vigia.backend.controller;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import dev.vigia.backend.config.BatchScheduler;
import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.Perimetro;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.PerimetroRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.service.AlertaArchivadorService;
import dev.vigia.backend.service.AnestroDetectorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El webhook de TTN contra la base (H2) y sin @Transactional en la clase: cada request corre como en
 * producción, con sus propias transacciones. Cubre lo que depende de Open Session in View en el worker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Webhook de ingesta sin Open Session in View")
class IngestaSinOpenInViewTest {

    private static final String API_KEY = "Bearer test-api-key";

    @MockBean
    private ConnectionFactory connectionFactory;
    @MockBean
    private RabbitTemplate rabbitTemplate;
    // La distancia al perímetro se calcula con SQL nativo de PostGIS, que H2 no corre.
    @MockBean
    private PerimetroRepository perimetroRepository;
    // Los jobs programados arrancan con el contexto y escribirían las mismas filas que el test.
    @MockBean
    private BatchScheduler batchScheduler;
    @MockBean
    private AlertaArchivadorService alertaArchivadorService;
    @MockBean
    private AnestroDetectorService anestroDetectorService;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private EstablecimientoRepository establecimientoRepository;
    @Autowired
    private RodeoLoteRepository rodeoLoteRepository;
    @Autowired
    private CaravanaRepository caravanaRepository;
    @Autowired
    private AnimalRepository animalRepository;
    @Autowired
    private TelemetriaRepository telemetriaRepository;
    @Autowired
    private AlertaRepository alertaRepository;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() {
        limpiar();

        Establecimiento est = new Establecimiento();
        est.setNombre("Estancia OSIV");
        est.setLatitud(-34.0);
        est.setLongitud(-58.0);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        est = establecimientoRepository.save(est);

        RodeoLote rodeo = new RodeoLote(est);
        rodeo.setNombre("Rodeo OSIV");
        rodeo = rodeoLoteRepository.save(rodeo);

        Caravana caravana = new Caravana();
        caravana.setCodigo("C-OSIV-W1");
        caravana.setDevEui("DEV-OSIV-W1");
        caravana.setActivo(true);
        caravana = caravanaRepository.save(caravana);

        Animal animal = new Animal(est, rodeo);
        animal.setAlias("Lola");
        animal.setEstadoActual(EstadoAnimal.ACTIVO);
        animal.setCaravana(caravana);
        animalRepository.save(animal);
    }

    /** Sin rollback: se limpia antes y después, para no dejarle datos a la clase de test que sigue. */
    @AfterEach
    void limpiar() {
        alertaRepository.deleteAll();
        telemetriaRepository.deleteAll();
        animalRepository.deleteAll();
        caravanaRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
        establecimientoRepository.deleteAll();
    }

    private ResultActions enviarUplink(int fCnt, String decodedPayload) throws Exception {
        String body = "{\"end_device_ids\":{\"dev_eui\":\"DEV-OSIV-W1\"},\"uplink_message\":{\"f_cnt\":" + fCnt
                + ",\"received_at\":\"" + Instant.now() + "\",\"decoded_payload\":" + decodedPayload + "}}";
        return mockMvc.perform(post("/api/v1/ingesta/uplink")
                .header("Authorization", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String gnss(double latitud, double longitud) {
        return "{\"type\":\"GNSSPosition\",\"latitude\":" + latitud + ",\"longitude\":" + longitud
                + ",\"moveState\":3,\"battery\":\"88.0%\"}";
    }

    @Test
    @DisplayName("GNSS crea la fila y el heartbeat actualiza esa misma fila")
    void gnssYHeartbeat_creanYActualizanLaFila() throws Exception {
        enviarUplink(1, gnss(-34.0, -58.0)).andExpect(status().isOk()).andExpect(content().string("OK"));
        enviarUplink(2, "{\"type\":\"Heartbeat\",\"moveState\":5,\"battery\":\"77.0%\"}")
                .andExpect(status().isOk()).andExpect(content().string("OK"));

        List<Telemetria> filas = telemetriaRepository.findAll();
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getMoveState()).isEqualTo(5);
        assertThat(filas.get(0).getBateria()).isEqualTo(77.0);
        assertThat(caravanaRepository.findByDevEui("DEV-OSIV-W1").orElseThrow().getUltimoFrameCounter())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Posición fuera del perímetro: crea la alerta con el nombre del lote")
    void gnssFueraDelPerimetro_creaAlertaConElNombreDelLote() throws Exception {
        when(perimetroRepository.findPerimetrosActivosByLoteId(anyLong())).thenReturn(List.of(mock(Perimetro.class)));
        // calcularDistanciasAPerimetrosActivos devuelve vacío: ningún perímetro admite la posición

        enviarUplink(1, gnss(-35.0, -59.0)).andExpect(status().isOk());

        List<Alerta> alertas = alertaRepository.findAll();
        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).getDisparador()).isEqualTo(DisparadorAlerta.FUERA_DE_PERIMETRO);
        assertThat(alertas.get(0).getPosibleCausa()).contains("Rodeo OSIV");
    }

    @Test
    @DisplayName("Mientras publica en RabbitMQ, el webhook no retiene una conexión a la base")
    void uplink_mientrasPublicaEnRabbit_noRetieneConexion() throws Exception {
        CountDownLatch publicando = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        doAnswer(inv -> {
            publicando.countDown();
            liberar.await(10, TimeUnit.SECONDS);
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.TELEMETRIA_EXCHANGE), eq(""), any(Object.class));
        HikariPoolMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();

        ExecutorService hilo = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> envio = hilo.submit(() -> enviarUplink(1, gnss(-34.0, -58.0)).andReturn());
            assertThat(publicando.await(10, TimeUnit.SECONDS)).isTrue();
            int activasMientrasPublica = pool.getActiveConnections();
            liberar.countDown();

            assertThat(envio.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            assertThat(activasMientrasPublica).isZero();
        } finally {
            liberar.countDown();
            hilo.shutdownNow();
        }
    }
}
