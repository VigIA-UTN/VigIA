package dev.vigia.backend.service;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.CoordenadaDTO;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.model.*;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.PerimetroRepository;
import dev.vigia.backend.util.GeometryUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeofenceVerificationService — Tests Unitarios")
class GeofenceVerificationServiceTest {

    @Mock
    private PerimetroRepository perimetroRepository;

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private GeofenceVerificationService geofenceVerificationService;

    private Animal animal;
    private RodeoLote lote;
    private Perimetro perimetro;

    private record Distancia(Long perimetroId, Double distanciaMetros)
            implements PerimetroRepository.DistanciaPerimetro {
        @Override
        public Long getPerimetroId() {
            return perimetroId;
        }

        @Override
        public Double getDistanciaMetros() {
            return distanciaMetros;
        }
    }

    @BeforeEach
    void setUp() {
        Establecimiento establecimiento = new Establecimiento();
        establecimiento.setId(10L);
        establecimiento.setNombre("Establecimiento Central");

        lote = new RodeoLote(establecimiento);
        lote.setId(100L);
        lote.setNombre("Lote Reproductores");

        animal = new Animal(establecimiento, lote);
        animal.setId(55L);

        perimetro = new Perimetro(establecimiento, "Potrero 1",
                GeometryUtils.crearPoligono(List.of(
                        new CoordenadaDTO(-34.0, -58.0),
                        new CoordenadaDTO(-34.0, -57.0),
                        new CoordenadaDTO(-35.0, -57.0),
                        new CoordenadaDTO(-35.0, -58.0))));
        ReflectionTestUtils.setField(perimetro, "id", 1L);
    }

    private void stubDistancia(double lat, double lon, double distanciaMetros) {
        when(perimetroRepository.findPerimetrosActivosByLoteId(100L)).thenReturn(List.of(perimetro));
        when(perimetroRepository.calcularDistanciasAPerimetrosActivos(100L, lat, lon))
                .thenReturn(List.of(new Distancia(1L, distanciaMetros)));
    }

    private void stubAlertaReciente(Optional<Alerta> alerta) {
        when(alertaRepository.findFirstByAnimalAndDisparadorAndTsGeneracionAfterOrderByTsGeneracionDesc(eq(animal),
                eq(DisparadorAlerta.FUERA_DE_PERIMETRO), any()))
                .thenReturn(alerta);
    }

    @SuppressWarnings("null")
    private void stubSinAlertaReciente() {
        stubAlertaReciente(Optional.empty());
        when(alertaRepository.save(any(Alerta.class))).thenAnswer(inv -> {
            Alerta a = inv.getArgument(0);
            a.setId(999L);
            return a;
        });
    }

    @Test
    @DisplayName("Si el animal no tiene lote asignado, no verifica ni emite alertas")
    void animalSinLote_noVerifica() {
        animal.setRodeoLote(null);

        geofenceVerificationService.verificarPosicion(animal, -34.5, -57.5, LocalDateTime.now());

        verifyNoInteractions(perimetroRepository);
        verifyNoInteractions(alertaRepository);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Si el lote no tiene perímetros activos configurados, no emite alerta")
    void loteSinPerimetrosActivos_noEmiteAlerta() {
        when(perimetroRepository.findPerimetrosActivosByLoteId(100L)).thenReturn(Collections.emptyList());

        geofenceVerificationService.verificarPosicion(animal, -34.5, -57.5, LocalDateTime.now());

        verify(perimetroRepository, times(1)).findPerimetrosActivosByLoteId(100L);
        verify(perimetroRepository, never()).calcularDistanciasAPerimetrosActivos(any(), anyDouble(), anyDouble());
        verifyNoInteractions(alertaRepository);
    }

    @Test
    @DisplayName("Si el animal está dentro del perímetro, no emite alerta")
    void animalDentroDePerimetro_noEmiteAlerta() {
        stubDistancia(-34.5, -57.5, 0.0);

        geofenceVerificationService.verificarPosicion(animal, -34.5, -57.5, LocalDateTime.now());

        verify(perimetroRepository, times(1)).calcularDistanciasAPerimetrosActivos(100L, -34.5, -57.5);
        verifyNoInteractions(alertaRepository);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Fuera del polígono pero dentro de la tolerancia configurada, no emite alerta")
    void animalFueraDelPoligonoDentroDeTolerancia_noEmiteAlerta() {
        perimetro.configurarUmbral(new BigDecimal("50"), false);
        stubDistancia(-40.0, -60.0, 30.0);

        geofenceVerificationService.verificarPosicion(animal, -40.0, -60.0, LocalDateTime.now());

        verifyNoInteractions(alertaRepository);
        verifyNoInteractions(rabbitTemplate);
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Fuera del polígono y fuera de la tolerancia configurada, emite alerta")
    void animalFueraDeTolerancia_emiteAlerta() {
        perimetro.configurarUmbral(new BigDecimal("50"), false);
        stubDistancia(-40.0, -60.0, 80.0);
        stubSinAlertaReciente();

        geofenceVerificationService.verificarPosicion(animal, -40.0, -60.0, LocalDateTime.now());

        verify(alertaRepository, times(1)).save(any(Alerta.class));
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.ALERTAS_EXCHANGE), eq(""),
                any(AlertaEventDTO.class));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Con zona estricta, cualquier punto fuera del polígono emite alerta ignorando la tolerancia")
    void zonaEstricta_ignoraToleranciaYEmiteAlerta() {
        perimetro.configurarUmbral(new BigDecimal("50"), true);
        stubDistancia(-40.0, -60.0, 1.0);
        stubSinAlertaReciente();

        geofenceVerificationService.verificarPosicion(animal, -40.0, -60.0, LocalDateTime.now());

        verify(alertaRepository, times(1)).save(any(Alerta.class));
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.ALERTAS_EXCHANGE), eq(""),
                any(AlertaEventDTO.class));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Si el animal está fuera del perímetro, genera alerta FUERA_DE_PERIMETRO y publica a RabbitMQ")
    void animalFueraDePerimetro_generaAlertaYPublicaRabbit() {
        stubDistancia(-40.0, -60.0, 1000.0);
        stubSinAlertaReciente();

        LocalDateTime horaSalida = LocalDateTime.of(2026, 9, 24, 10, 15, 0);
        geofenceVerificationService.verificarPosicion(animal, -40.0, -60.0, horaSalida);

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository, times(1)).save(captor.capture());
        Alerta capturada = captor.getValue();
        assertThat(capturada.getDisparador()).isEqualTo(DisparadorAlerta.FUERA_DE_PERIMETRO);
        assertThat(capturada.getLatitud()).isEqualTo(-40.0);
        assertThat(capturada.getLongitud()).isEqualTo(-60.0);
        assertThat(capturada.getTsGeneracion()).isEqualTo(horaSalida);
        assertThat(capturada.getTipo()).isEqualTo("Fuera de Perímetro");
        assertThat(capturada.getSeveridad()).isEqualTo(SeveridadAlerta.ALTA);
        assertThat(capturada.getAccionRecomendada())
                .isEqualTo("Verificar ubicación del animal en el mapa y retornarlo al perímetro autorizado.");

        ArgumentCaptor<AlertaEventDTO> eventCaptor = ArgumentCaptor.forClass(AlertaEventDTO.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.ALERTAS_EXCHANGE), eq(""),
                eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAccionRecomendada())
                .isEqualTo("Verificar ubicación del animal en el mapa y retornarlo al perímetro autorizado.");
        assertThat(eventCaptor.getValue().getLatitud()).isEqualTo(-40.0);
        assertThat(eventCaptor.getValue().getLongitud()).isEqualTo(-60.0);
        assertThat(eventCaptor.getValue().getTsGeneracion()).isEqualTo(horaSalida);
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Con una alerta pendiente en el episodio activo, actualiza la posición sin emitir otra alerta")
    void animalSigueFuera_actualizaPosicionDeAlertaPendiente() {
        stubDistancia(-41.0, -61.0, 1000.0);
        LocalDateTime horaSalida = LocalDateTime.now().minusHours(1);
        Alerta activa = new Alerta();
        activa.setId(999L);
        activa.setEstado(EstadoAlerta.PENDIENTE);
        activa.setTsGeneracion(horaSalida);
        activa.setLatitud(-40.0);
        activa.setLongitud(-60.0);
        stubAlertaReciente(Optional.of(activa));

        geofenceVerificationService.verificarPosicion(animal, -41.0, -61.0, LocalDateTime.now());

        verify(alertaRepository, times(1)).save(activa);
        assertThat(activa.getLatitud()).isEqualTo(-41.0);
        assertThat(activa.getLongitud()).isEqualTo(-61.0);
        assertThat(activa.getTsGeneracion()).isEqualTo(horaSalida);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Si la alerta reciente ya fue resuelta, omite por cooldown sin modificarla")
    void animalFueraDePerimetroConAlertaResuelta_omitePorCooldown() {
        stubDistancia(-40.0, -60.0, 1000.0);
        Alerta resuelta = new Alerta();
        resuelta.setEstado(EstadoAlerta.CONFIRMADA);
        resuelta.setLatitud(-39.0);
        stubAlertaReciente(Optional.of(resuelta));

        geofenceVerificationService.verificarPosicion(animal, -40.0, -60.0, LocalDateTime.now());

        verify(alertaRepository, never()).save(any());
        assertThat(resuelta.getLatitud()).isEqualTo(-39.0);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
