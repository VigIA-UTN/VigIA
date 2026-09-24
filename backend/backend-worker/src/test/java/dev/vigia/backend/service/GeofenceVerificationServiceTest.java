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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

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
        verify(perimetroRepository, never()).findPerimetrosQueContienen(any(), anyDouble(), anyDouble());
        verifyNoInteractions(alertaRepository);
    }

    @Test
    @DisplayName("Si el animal está dentro del perímetro, no emite alerta")
    void animalDentroDePerimetro_noEmiteAlerta() {
        when(perimetroRepository.findPerimetrosActivosByLoteId(100L)).thenReturn(List.of(perimetro));
        when(perimetroRepository.findPerimetrosQueContienen(100L, -34.5, -57.5)).thenReturn(List.of(perimetro));

        geofenceVerificationService.verificarPosicion(animal, -34.5, -57.5, LocalDateTime.now());

        verify(perimetroRepository, times(1)).findPerimetrosQueContienen(100L, -34.5, -57.5);
        verifyNoInteractions(alertaRepository);
        verifyNoInteractions(rabbitTemplate);
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Si el animal está fuera del perímetro, genera alerta FUERA_DE_PERIMETRO y publica a RabbitMQ")
    void animalFueraDePerimetro_generaAlertaYPublicaRabbit() {
        when(perimetroRepository.findPerimetrosActivosByLoteId(100L)).thenReturn(List.of(perimetro));
        when(perimetroRepository.findPerimetrosQueContienen(100L, -40.0, -60.0)).thenReturn(Collections.emptyList());
        when(alertaRepository.existsByAnimalAndDisparadorAndTsGeneracionAfter(eq(animal),
                eq(DisparadorAlerta.FUERA_DE_PERIMETRO), any()))
                .thenReturn(false);

        when(alertaRepository.save(any(Alerta.class))).thenAnswer(inv -> {
            Alerta a = inv.getArgument(0);
            a.setId(999L);
            return a;
        });

        geofenceVerificationService.verificarPosicion(animal, -40.0, -60.0, LocalDateTime.now());

        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        verify(alertaRepository, times(1)).save(captor.capture());
        Alerta capturada = captor.getValue();
        assertThat(capturada.getDisparador()).isEqualTo(DisparadorAlerta.FUERA_DE_PERIMETRO);
        assertThat(capturada.getTipo()).isEqualTo("Fuera de Perímetro");
        assertThat(capturada.getSeveridad()).isEqualTo(SeveridadAlerta.ALTA);
        assertThat(capturada.getAccionRecomendada())
                .isEqualTo("Verificar ubicación del animal en el mapa y retornarlo al perímetro autorizado.");

        ArgumentCaptor<AlertaEventDTO> eventCaptor = ArgumentCaptor.forClass(AlertaEventDTO.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.ALERTAS_EXCHANGE), eq(""),
                eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAccionRecomendada())
                .isEqualTo("Verificar ubicación del animal en el mapa y retornarlo al perímetro autorizado.");
    }

    @SuppressWarnings("null")
    @Test
    @DisplayName("Si el animal está fuera de perímetro pero ya tiene alerta reciente, omite por cooldown")
    void animalFueraDePerimetroConAlertaReciente_omitePorCooldown() {
        when(perimetroRepository.findPerimetrosActivosByLoteId(100L)).thenReturn(List.of(perimetro));
        when(perimetroRepository.findPerimetrosQueContienen(100L, -40.0, -60.0)).thenReturn(Collections.emptyList());
        when(alertaRepository.existsByAnimalAndDisparadorAndTsGeneracionAfter(eq(animal),
                eq(DisparadorAlerta.FUERA_DE_PERIMETRO), any()))
                .thenReturn(true);

        geofenceVerificationService.verificarPosicion(animal, -40.0, -60.0, LocalDateTime.now());

        verify(alertaRepository, never()).save(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}
