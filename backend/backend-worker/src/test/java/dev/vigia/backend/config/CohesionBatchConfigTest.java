package dev.vigia.backend.config;

import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CohesionBatchConfig — Tests Unitarios")
class CohesionBatchConfigTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private EstablecimientoRepository establecimientoRepository;

    @Mock
    private TelemetriaRepository telemetriaRepository;

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RegistroCohesionRepository registroCohesionRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private RestTemplate restTemplate;

    private CohesionBatchConfig batchConfig;

    @BeforeEach
    void setUp() {
        batchConfig = new CohesionBatchConfig(
                jobRepository,
                transactionManager,
                establecimientoRepository,
                telemetriaRepository,
                alertaRepository,
                rabbitTemplate,
                registroCohesionRepository,
                animalRepository
        );
        ReflectionTestUtils.setField(batchConfig, "restTemplate", restTemplate);
    }

    @Test
    @DisplayName("Sin telemetría en las últimas 24h, no llama a ml-geo ni persiste registros")
    void sinTelemetria_noProcesa() throws Exception {
        Establecimiento est = new Establecimiento();
        est.setId(1L);

        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(Collections.emptyList());

        ItemProcessor<Establecimiento, Establecimiento> processor = batchConfig.cohesionProcessor();
        Establecimiento result = processor.process(est);

        assertThat(result).isSameAs(est);
        verifyNoInteractions(restTemplate);
        verifyNoInteractions(registroCohesionRepository);
    }

    @Test
    @SuppressWarnings("null")
    @DisplayName("Con telemetría y respuesta ml-geo exitosa, guarda RegistroCohesion por cada animal")
    void conTelemetria_guardaRegistroCohesionPorAnimal() throws Exception {
        Establecimiento est = new Establecimiento();
        est.setId(1L);
        est.setUmbralAislamientoM(120.0);

        Animal a1 = new Animal();
        a1.setId(10L);
        Caravana c1 = new Caravana();
        c1.setAnimal(a1);

        Telemetria t1 = new Telemetria();
        t1.setCaravana(c1);
        t1.setLatitud(-34.60);
        t1.setLongitud(-58.38);
        t1.setFechaHora(LocalDateTime.now().minusHours(1));

        when(telemetriaRepository.findByEstablecimientoAndFechaHoraBetween(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(t1));

        CohesionBatchConfig.GeoAlerta ga = new CohesionBatchConfig.GeoAlerta();
        ga.setAnimalId("10");
        ga.setIndiceCohesion(0.85);
        ga.setDistanciaCentroideM(35.0);
        ga.setAlertaAislamiento(false);
        ga.setTiempoFueraGrupoS(0.0);

        CohesionBatchConfig.GeoResponse geoResp = new CohesionBatchConfig.GeoResponse();
        geoResp.setAlertas(List.of(ga));

        when(restTemplate.postForEntity(anyString(), any(), eq(CohesionBatchConfig.GeoResponse.class)))
                .thenReturn(new ResponseEntity<>(geoResp, HttpStatus.OK));

        ItemProcessor<Establecimiento, Establecimiento> processor = batchConfig.cohesionProcessor();
        processor.process(est);

        ArgumentCaptor<RegistroCohesion> captor = ArgumentCaptor.forClass(RegistroCohesion.class);
        verify(registroCohesionRepository).save(captor.capture());

        RegistroCohesion saved = captor.getValue();
        assertThat(saved.getAnimal().getId()).isEqualTo(10L);
        assertThat(saved.getEstablecimiento().getId()).isEqualTo(1L);
        assertThat(saved.getIndiceCohesion()).isEqualTo(0.85);
        assertThat(saved.getDistanciaCentroideM()).isEqualTo(35.0);
        assertThat(saved.getTiempoFueraGrupoS()).isEqualTo(0L);
        assertThat(saved.getFecha()).isNotNull();

        // Como alertaAislamiento es false, no debe generar alerta
        verifyNoInteractions(alertaRepository);
    }
}
