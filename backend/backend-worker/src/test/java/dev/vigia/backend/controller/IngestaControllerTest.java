package dev.vigia.backend.controller;

import dev.vigia.backend.config.RabbitMQConfig;
import dev.vigia.backend.dto.ml.TelemetriaEventDTO;
import dev.vigia.backend.dto.ttn.TTNWebhookRequestDTO;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.EstadoInferencia;
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
import static org.mockito.ArgumentMatchers.eq;
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

    private TTNWebhookRequestDTO heartbeatCon(Integer fCnt, String battery, Integer moveState) {
        TTNWebhookRequestDTO.EndDeviceIds endDeviceIds = new TTNWebhookRequestDTO.EndDeviceIds();
        endDeviceIds.setDevEui(DEV_EUI);

        TTNWebhookRequestDTO.DecodedPayload decoded = new TTNWebhookRequestDTO.DecodedPayload();
        decoded.setType("Heartbeat");
        decoded.setBattery(battery);
        decoded.setMoveState(moveState);

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

    @Test
    @DisplayName("Posición procesada: publica evento de telemetría en RabbitMQ y dispara verificación de geocerca")
    void posicionProcesada_publicaRabbitYVerificaGeocerca() {
        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.of(animal));
        when(telemetriaRepository.findFirstByCaravanaOrderByFechaHoraDesc(caravana)).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.receiveUplink(payloadCon(1));

        assertThat(response.getBody()).isEqualTo("OK");

        ArgumentCaptor<TelemetriaEventDTO> eventCaptor = ArgumentCaptor.forClass(TelemetriaEventDTO.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.TELEMETRIA_EXCHANGE), eq(""), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAnimalId()).isEqualTo(animal.getId());
        assertThat(eventCaptor.getValue().getLatitud()).isEqualTo(-34.5);
        assertThat(eventCaptor.getValue().getLongitud()).isEqualTo(-58.5);

        verify(geofenceVerificationService, times(1))
                .verificarPosicion(eq(animal), eq(-34.5), eq(-58.5), any());
        verify(eventPublisher, times(1)).publishEvent("NUEVA_TELEMETRIA");
    }

    @Test
    @DisplayName("Heartbeat sin telemetría previa, crea fila nueva sin coordenadas con batería y moveState")
    void heartbeatSinTelemetriaPrevia_creaFilaNueva() {
        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.of(animal));
        when(telemetriaRepository.findFirstByCaravanaOrderByFechaHoraDesc(caravana)).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.receiveUplink(heartbeatCon(1, "75%", 0));

        assertThat(response.getBody()).isEqualTo("OK");

        ArgumentCaptor<Telemetria> telemetriaCaptor = ArgumentCaptor.forClass(Telemetria.class);
        verify(telemetriaRepository, times(1)).save(telemetriaCaptor.capture());
        Telemetria guardada = telemetriaCaptor.getValue();
        assertThat(guardada.getLatitud()).isNull();
        assertThat(guardada.getLongitud()).isNull();
        assertThat(guardada.getMoveState()).isEqualTo(0);
        assertThat(guardada.getBateria()).isEqualTo(75.0);
        assertThat(guardada.getEstadoInferenciaCelo()).isEqualTo(EstadoInferencia.PENDIENTE);
        assertThat(guardada.getEstadoInferenciaSalud()).isEqualTo(EstadoInferencia.PENDIENTE);

        verify(eventPublisher, times(1)).publishEvent("NUEVA_TELEMETRIA");
    }

    @Test
    @DisplayName("Heartbeat con telemetría previa, actualiza la fila existente en vez de crear una nueva")
    void heartbeatConTelemetriaPrevia_actualizaFilaExistente() {
        Telemetria previa = new Telemetria();
        previa.setCaravana(caravana);
        previa.setLatitud(-34.5);
        previa.setLongitud(-58.5);
        previa.setMoveState(0);
        previa.setBateria(90.0);

        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.of(animal));
        when(telemetriaRepository.findFirstByCaravanaOrderByFechaHoraDesc(caravana)).thenReturn(Optional.of(previa));

        ResponseEntity<String> response = controller.receiveUplink(heartbeatCon(1, "60%", 1));

        assertThat(response.getBody()).isEqualTo("OK");

        ArgumentCaptor<Telemetria> telemetriaCaptor = ArgumentCaptor.forClass(Telemetria.class);
        verify(telemetriaRepository, times(1)).save(telemetriaCaptor.capture());
        Telemetria guardada = telemetriaCaptor.getValue();
        assertThat(guardada).isSameAs(previa);
        assertThat(guardada.getLatitud()).isEqualTo(-34.5);
        assertThat(guardada.getMoveState()).isEqualTo(1);
        assertThat(guardada.getBateria()).isEqualTo(60.0);

        // Al actualizar una fila existente no se considera una fila "nueva" de telemetría
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Heartbeat con batería en formato inválido, no falla y no pisa el valor previo")
    void heartbeatConBateriaInvalida_noFalla() {
        Telemetria previa = new Telemetria();
        previa.setCaravana(caravana);
        previa.setBateria(90.0);

        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.of(animal));
        when(telemetriaRepository.findFirstByCaravanaOrderByFechaHoraDesc(caravana)).thenReturn(Optional.of(previa));

        ResponseEntity<String> response = controller.receiveUplink(heartbeatCon(1, "no-numerico", 1));

        assertThat(response.getBody()).isEqualTo("OK");
        assertThat(previa.getBateria()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("Heartbeat con batería, escribe bateria y ultimaSenial en la Caravana")
    void heartbeatConBateria_actualizaCaravana() {
        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.of(animal));
        when(telemetriaRepository.findFirstByCaravanaOrderByFechaHoraDesc(caravana)).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.receiveUplink(heartbeatCon(1, "75%", 0));

        assertThat(response.getBody()).isEqualTo("OK");

        ArgumentCaptor<Caravana> caravanaCaptor = ArgumentCaptor.forClass(Caravana.class);
        verify(caravanaRepository, times(1)).save(caravanaCaptor.capture());
        assertThat(caravanaCaptor.getValue().getBateria()).isEqualTo(75.0);
        assertThat(caravanaCaptor.getValue().getUltimaSenial()).isNotNull();
    }

    @Test
    @DisplayName("Heartbeat sin batería en el payload pero con valor previo en Telemetria, la Caravana conserva el último valor conocido")
    void heartbeatSinBateriaEnPayload_caravanaConservaUltimoValor() {
        Telemetria previa = new Telemetria();
        previa.setCaravana(caravana);
        previa.setBateria(90.0);

        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.of(animal));
        when(telemetriaRepository.findFirstByCaravanaOrderByFechaHoraDesc(caravana)).thenReturn(Optional.of(previa));

        ResponseEntity<String> response = controller.receiveUplink(heartbeatCon(1, null, 1));

        assertThat(response.getBody()).isEqualTo("OK");

        ArgumentCaptor<Caravana> caravanaCaptor = ArgumentCaptor.forClass(Caravana.class);
        verify(caravanaRepository, times(1)).save(caravanaCaptor.capture());
        assertThat(caravanaCaptor.getValue().getBateria()).isEqualTo(90.0);
    }

    private TTNWebhookRequestDTO conSesion(TTNWebhookRequestDTO payload, String sessionKeyId) {
        payload.getUplinkMessage().setSessionKeyId(sessionKeyId);
        return payload;
    }

    private void stubCaravanaConAnimal() {
        when(caravanaRepository.findByDevEui(DEV_EUI)).thenReturn(Optional.of(caravana));
        when(animalRepository.findByCaravanaId(caravana.getId())).thenReturn(Optional.of(animal));
        lenient().when(telemetriaRepository.findFirstByCaravanaOrderByFechaHoraDesc(caravana))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Misma sesión y fCnt <= último, ignora")
    void mismaSesion_fCntMenorOIgual_ignorado() {
        caravana.setUltimoFrameCounter(5);
        caravana.setUltimaSessionKeyId("A");
        stubCaravanaConAnimal();

        ResponseEntity<String> response = controller.receiveUplink(conSesion(payloadCon(5), "A"));

        assertThat(response.getBody()).isEqualTo("Ignorado: frame duplicado (idempotencia)");
        verify(caravanaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Misma sesión y fCnt mayor, procesa")
    void mismaSesion_fCntMayor_procesa() {
        caravana.setUltimoFrameCounter(5);
        caravana.setUltimaSessionKeyId("A");
        stubCaravanaConAnimal();

        ResponseEntity<String> response = controller.receiveUplink(conSesion(payloadCon(6), "A"));

        assertThat(response.getBody()).isEqualTo("OK");
        assertThat(caravana.getUltimoFrameCounter()).isEqualTo(6);
    }

    @Test
    @DisplayName("Sesión nueva (rejoin) con fCnt menor al último: procesa, resetea el contador y guarda la sesión")
    void sesionNueva_fCntMenor_procesaYResetea() {
        caravana.setUltimoFrameCounter(219);
        caravana.setUltimaSessionKeyId("A");
        stubCaravanaConAnimal();

        ResponseEntity<String> response = controller.receiveUplink(conSesion(payloadCon(1), "B"));

        assertThat(response.getBody()).isEqualTo("OK");
        assertThat(caravana.getUltimoFrameCounter()).isEqualTo(1);
        assertThat(caravana.getUltimaSessionKeyId()).isEqualTo("B");
    }

    @Test
    @DisplayName("Fila legacy sin session key guardada: el primer uplink con sesión se acepta aunque fCnt < último")
    void caravanaLegacySinSessionKey_procesa() {
        caravana.setUltimoFrameCounter(219);
        caravana.setUltimaSessionKeyId(null);
        stubCaravanaConAnimal();

        ResponseEntity<String> response = controller.receiveUplink(conSesion(payloadCon(2), "B"));

        assertThat(response.getBody()).isEqualTo("OK");
        assertThat(caravana.getUltimoFrameCounter()).isEqualTo(2);
        assertThat(caravana.getUltimaSessionKeyId()).isEqualTo("B");
    }

    @Test
    @DisplayName("Regresión: 219 guardado, rejoin y fCnt 1, 2, 3 se aceptan; repetir el 3 se ignora")
    void replayRejoin_aceptaSecuenciaNuevaEIgnoraRepetido() {
        caravana.setUltimoFrameCounter(219);
        caravana.setUltimaSessionKeyId("A");
        stubCaravanaConAnimal();

        for (int fCnt = 1; fCnt <= 3; fCnt++) {
            assertThat(controller.receiveUplink(conSesion(payloadCon(fCnt), "B")).getBody()).isEqualTo("OK");
        }
        assertThat(controller.receiveUplink(conSesion(payloadCon(3), "B")).getBody())
                .isEqualTo("Ignorado: frame duplicado (idempotencia)");
        assertThat(caravana.getUltimoFrameCounter()).isEqualTo(3);
    }

    @Test
    @DisplayName("fCnt ausente (0) en la misma sesión con contador previo > 0, ignora")
    void fCntAusente_mismaSesion_ignorado() {
        caravana.setUltimoFrameCounter(5);
        caravana.setUltimaSessionKeyId("A");
        stubCaravanaConAnimal();

        ResponseEntity<String> response = controller.receiveUplink(conSesion(payloadCon(null), "A"));

        assertThat(response.getBody()).isEqualTo("Ignorado: frame duplicado (idempotencia)");
    }

    @Test
    @DisplayName("fCnt ausente (0) en una sesión nueva, procesa")
    void fCntAusente_sesionNueva_procesa() {
        caravana.setUltimoFrameCounter(5);
        caravana.setUltimaSessionKeyId("A");
        stubCaravanaConAnimal();

        ResponseEntity<String> response = controller.receiveUplink(conSesion(payloadCon(null), "B"));

        assertThat(response.getBody()).isEqualTo("OK");
        assertThat(caravana.getUltimoFrameCounter()).isEqualTo(0);
        assertThat(caravana.getUltimaSessionKeyId()).isEqualTo("B");
    }

    @Test
    @DisplayName("Fix GNSS fallido (coordenadas ~0), ignora sin guardar ni publicar")
    void fixGnssFallido_ignorado() {
        stubCaravanaConAnimal();
        TTNWebhookRequestDTO payload = payloadCon(1);
        payload.getUplinkMessage().getDecodedPayload().setLatitude(5.877471754111438e-39);
        payload.getUplinkMessage().getDecodedPayload().setLongitude(5.877471754111438e-39);

        ResponseEntity<String> response = controller.receiveUplink(payload);

        assertThat(response.getBody()).isEqualTo("Ignorado: fix GNSS fallido");
        verify(telemetriaRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate, geofenceVerificationService);
        verify(caravanaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fix GNSS real (coordenadas válidas), procesa")
    void fixGnssReal_procesa() {
        stubCaravanaConAnimal();

        ResponseEntity<String> response = controller.receiveUplink(payloadCon(1));

        assertThat(response.getBody()).isEqualTo("OK");
        verify(telemetriaRepository, times(1)).save(any(Telemetria.class));
    }
}
