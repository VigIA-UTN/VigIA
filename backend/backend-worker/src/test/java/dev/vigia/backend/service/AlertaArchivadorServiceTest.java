package dev.vigia.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.repository.AlertaRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Establecimiento;

@ExtendWith(MockitoExtension.class)
public class AlertaArchivadorServiceTest {

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AlertaArchivadorService alertaArchivadorService;

    private Alerta alertaVencida1;
    private Alerta alertaVencida2;

    @BeforeEach
    void setUp() {
        Establecimiento establecimiento = new Establecimiento();
        establecimiento.setId(100L);
        Animal animal = new Animal();
        animal.setEstablecimiento(establecimiento);
        
        alertaVencida1 = new Alerta();
        alertaVencida1.setId(1L);
        alertaVencida1.setEstado(EstadoAlerta.PENDIENTE);
        alertaVencida1.setAnimal(animal);

        alertaVencida2 = new Alerta();
        alertaVencida2.setId(2L);
        alertaVencida2.setEstado(EstadoAlerta.PENDIENTE);
        alertaVencida2.setAnimal(animal);
    }

    @SuppressWarnings("null")
    @Test
    void archivarAlertasVencidas_Success_ArchivaAlertas() {
        // Arrange
        List<Alerta> vencidas = List.of(alertaVencida1, alertaVencida2);
        when(alertaRepository.findAlertasCeloPendientesVencidas(any(LocalDateTime.class)))
                .thenReturn(vencidas);

        // Act
        alertaArchivadorService.archivarAlertasVencidas();

        // Assert
        assertEquals(EstadoAlerta.ARCHIVADA, alertaVencida1.getEstado());
        assertEquals("oportunidad_no_aprovechada", alertaVencida1.getMotivoDescarte());

        assertEquals(EstadoAlerta.ARCHIVADA, alertaVencida2.getEstado());
        assertEquals("oportunidad_no_aprovechada", alertaVencida2.getMotivoDescarte());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Alerta>> captor = ArgumentCaptor.forClass(List.class);
        verify(alertaRepository, times(1)).saveAll(captor.capture());

        List<Alerta> savedAlertas = captor.getValue();
        assertEquals(2, savedAlertas.size());

        verify(rabbitTemplate, times(2)).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @SuppressWarnings("null")
    @Test
    void archivarAlertasVencidas_Success_NoHaceNadaSiNoHayVencidas() {
        // Arrange
        when(alertaRepository.findAlertasCeloPendientesVencidas(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // Act
        alertaArchivadorService.archivarAlertasVencidas();

        // Assert
        verify(alertaRepository, never()).saveAll(any());
    }
}
