package dev.vigia.backend.controller;

import dev.vigia.backend.dto.ttn.TTNWebhookRequestDTO;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.service.GeofenceVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngestaController — Tests Unitarios (webhook TTN)")
class IngestaControllerTest {

    @Mock
    private TelemetriaRepository telemetriaRepository;
    @Mock
    private CaravanaRepository caravanaRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private GeofenceVerificationService geofenceVerificationService;

    private IngestaController controller;

    private Caravana caravana;
    private Animal animal;
    private static final String DEV_EUI = "AA-BB-CC-DD";

    @BeforeEach
    void setUp() {
        controller = new IngestaController(telemetriaRepository, caravanaRepository, animalRepository,
                eventPublisher, rabbitTemplate, geofenceVerificationService);

        caravana = new Caravana();
        caravana.setId(1L);
        caravana.setDevEui(DEV_EUI);

        Establecimiento establecimiento = new Establecimiento();
        establecimiento.setId(10L);

        animal = new Animal(establecimiento, null);
        animal.setId(5L);
    }

    private TTNWebhookRequestDTO payloadCon(Integer fCnt) {
        TTNWebhookRequestDTO.EndDeviceIds endDeviceIds = new TTNWebhookRequestDTO.EndDeviceIds();
        endDeviceIds.setDevEui(DEV_EUI);

        TTNWebhookRequestDTO.DecodedPayload decoded = new TTNWebhookRequestDTO.DecodedPayload();
        decoded.setType("GNSSPosition");
        decoded.setLatitude(-34.5);
        decoded.setLongitude(-58.5);
        decoded.setBattery("80%");

        TTNWebhookRequestDTO.UplinkMessage uplink = new TTNWebhookRequestDTO.UplinkMessage();
        uplink.setDecodedPayload(decoded);
        uplink.setReceivedAt("2026-01-01T00:00:00Z");
        uplink.setFCnt(fCnt);

        TTNWebhookRequestDTO payload = new TTNWebhookRequestDTO();
        payload.setEndDeviceIds(endDeviceIds);
        payload.setUplinkMessage(uplink);
        return payload;
    }

    @Test
    @DisplayName("Sin dev_eui en el payload, ignora sin consultar repositorios")
    void sinDevEui_ignorado() {
        TTNWebhookRequestDTO payload = new TTNWebhookRequestDTO();

        ResponseEntity<String> response = controller.receiveUplink(payload);

        assertThat(response.getBody()).isEqualTo("Ignorado: Sin dev_eui");
        verifyNoInteractions(caravanaRepository);
    }

    @Test
    @DisplayName("Busca la caravana por dev_eui, no por codigo")
    void buscaPorDevEuiNoPorCodigo() {
        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.empty());

        controller.receiveUplink(payloadCon(1));

        verify(caravanaRepository, times(1)).findByDevEui(DEV_EUI);
        verify(caravanaRepository, never()).findByCodigo(any());
    }

    @Test
    @DisplayName("dev_eui no registrado en el sistema, ignora")
    void devEuiNoRegistrado_ignorado() {
        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.receiveUplink(payloadCon(1));

        assertThat(response.getBody()).isEqualTo("Ignorado: Dispositivo no registrado");
        verifyNoInteractions(telemetriaRepository);
    }

    @Test
    @DisplayName("Caravana sin animal asociado, ignora")
    void sinAnimalAsociado_ignorado() {
        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.receiveUplink(payloadCon(1));

        assertThat(response.getBody()).isEqualTo("Ignorado: Sin animal asociado");
        verifyNoInteractions(telemetriaRepository);
    }

    @Test
    @DisplayName("Frame counter menor o igual al último persistido, ignora por idempotencia")
    void frameCounterDuplicado_ignoradoPorIdempotencia() {
        caravana.setUltimoFrameCounter(5);
        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.of(animal));

        ResponseEntity<String> response = controller.receiveUplink(payloadCon(5));

        assertThat(response.getBody()).isEqualTo("Ignorado: frame duplicado (idempotencia)");
        verifyNoInteractions(telemetriaRepository, rabbitTemplate, geofenceVerificationService);
        verify(caravanaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Frame counter mayor al último persistido, procesa y actualiza ultimoFrameCounter")
    void frameCounterNuevo_procesaYActualizaContador() {
        caravana.setUltimoFrameCounter(5);
        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.of(animal));
        when(telemetriaRepository.findFirstByCaravanaOrderByFechaHoraDesc(caravana)).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.receiveUplink(payloadCon(6));

        assertThat(response.getBody()).isEqualTo("OK");
        verify(telemetriaRepository, times(1)).save(any(Telemetria.class));

        ArgumentCaptor<Caravana> caravanaCaptor = ArgumentCaptor.forClass(Caravana.class);
        verify(caravanaRepository, times(1)).save(caravanaCaptor.capture());
        assertThat(caravanaCaptor.getValue().getUltimoFrameCounter()).isEqualTo(6);
    }

    @Test
    @DisplayName("Primer uplink sin ultimoFrameCounter previo, procesa y guarda el contador")
    void sinFrameCounterPrevio_procesaYGuardaContador() {
        caravana.setUltimoFrameCounter(null);
        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.of(animal));
        when(telemetriaRepository.findFirstByCaravanaOrderByFechaHoraDesc(caravana)).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.receiveUplink(payloadCon(1));

        assertThat(response.getBody()).isEqualTo("OK");

        ArgumentCaptor<Caravana> caravanaCaptor = ArgumentCaptor.forClass(Caravana.class);
        verify(caravanaRepository, times(1)).save(caravanaCaptor.capture());
        assertThat(caravanaCaptor.getValue().getUltimoFrameCounter()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin decoded_payload, ignora sin tocar telemetria ni el contador")
    void sinDecodedPayload_ignorado() {
        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.of(animal));

        TTNWebhookRequestDTO.EndDeviceIds endDeviceIds = new TTNWebhookRequestDTO.EndDeviceIds();
        endDeviceIds.setDevEui(DEV_EUI);
        TTNWebhookRequestDTO payload = new TTNWebhookRequestDTO();
        payload.setEndDeviceIds(endDeviceIds);
        payload.setUplinkMessage(null);

        ResponseEntity<String> response = controller.receiveUplink(payload);

        assertThat(response.getBody()).isEqualTo("Ignorado: Sin decoded_payload");
        verifyNoInteractions(telemetriaRepository);
        verify(caravanaRepository, never()).save(any());
    }
}
