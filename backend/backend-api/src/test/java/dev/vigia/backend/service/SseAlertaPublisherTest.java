package dev.vigia.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.vigia.backend.dto.AnimalAisladoResponse;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.AlertaAnimalAislado;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.repository.AlertaAnimalAisladoRepository;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;

@ExtendWith(MockitoExtension.class)
class SseAlertaPublisherTest {

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private CeloDatoDesactualizadoEvaluator datoDesactualizadoEvaluator;

    @Mock
    private AlertaAnimalAisladoRepository alertaAnimalAisladoRepository;

    @Mock
    private EstablecimientoRepository establecimientoRepository;

    @Mock
    private dev.vigia.backend.repository.UsuarioRepository usuarioRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private SseAlertaPublisher publisher;

    @Test
    void heartbeat_clienteQueYaSeFue_loSacaDeLaLista() {
        publisher.registrarClienteSse(1L, null);
        SseEmitter cerrado = publisher.registrarClienteSse(1L, null);
        cerrado.complete(); // enviarle a un emitter terminado falla, igual que a un cliente que cortó la conexión

        publisher.enviarHeartbeat();

        assertThat(publisher.clientesConectados(1L)).isEqualTo(1);
    }

    @Test
    void aislamiento_conListaEnElEvento_usaEsaLista() {
        List<AnimalAisladoResponse> delEvento = List.of(AnimalAisladoResponse.builder().animalId(10L).build());

        assertThat(publisher.animalesAislados(900L, DisparadorAlerta.AISLAMIENTO_SOCIAL, delEvento)).isSameAs(delEvento);
        verifyNoInteractions(alertaAnimalAisladoRepository);
    }

    @Test
    void aislamiento_sinListaEnElEvento_laLeeDeLaBase() {
        // Eventos de cambio de estado (confirmar, descartar) y reenvío por lastEventId no traen la lista
        Alerta alerta = new Alerta();
        alerta.setId(900L);
        alerta.setValorUmbral(150.0);
        Animal animal = new Animal();
        animal.setId(10L);
        when(alertaAnimalAisladoRepository.findByAlerta_IdOrderBySegundosFueraDesc(900L))
                .thenReturn(List.of(AlertaAnimalAislado.builder()
                        .alerta(alerta)
                        .animal(animal)
                        .segundosFuera(1800L)
                        .distanciaGrupoM(400.0)
                        .tsInicio(LocalDateTime.now().minusMinutes(30))
                        .build()));

        List<AnimalAisladoResponse> aislados = publisher.animalesAislados(900L, DisparadorAlerta.AISLAMIENTO_SOCIAL, null);

        assertThat(aislados)
                .extracting(AnimalAisladoResponse::getAnimalId, AnimalAisladoResponse::getMinutosFuera,
                        AnimalAisladoResponse::getSeveridad)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, 30L, "ALTA"));
    }

    @Test
    void otroDisparador_noTieneAnimalesAislados() {
        assertThat(publisher.animalesAislados(900L, DisparadorAlerta.CELO, null)).isNull();
        verifyNoInteractions(alertaAnimalAisladoRepository);
    }
}
