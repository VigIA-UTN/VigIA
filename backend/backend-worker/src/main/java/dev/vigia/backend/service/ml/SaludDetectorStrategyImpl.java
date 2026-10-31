package dev.vigia.backend.service.ml;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.vigia.backend.mensajeria.PublicadorDeAlertas;
import dev.vigia.backend.client.MlSaludClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.ml.AlertaEventDTO;
import dev.vigia.backend.dto.ml.PerfilCalibracionRequestDTO;
import dev.vigia.backend.dto.ml.PerfilCalibracionResponseDTO;
import dev.vigia.backend.dto.ml.PuntoTelemetriaDTO;
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.dto.ml.SaludPredictRequestDTO;
import dev.vigia.backend.dto.ml.SaludPredictResponseDTO;
import dev.vigia.backend.util.SerieComparativaBuilder;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.PerfilReferencia;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.PerfilReferenciaRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static dev.vigia.backend.service.ml.VentanaTelemetria.SAMPLES_PER_DAY;
import static dev.vigia.backend.service.ml.VentanaTelemetria.conDatos;
import static dev.vigia.backend.service.ml.VentanaTelemetria.puntosParaMl;

@Slf4j
@Service
public class SaludDetectorStrategyImpl implements DetectorStrategy {

    private final MlSaludClient mlSaludClient;
    private final TelemetriaRepository telemetriaRepository;
    private final AlertaRepository alertaRepository;
    private final PerfilReferenciaRepository perfilReferenciaRepository;
    private final PublicadorDeAlertas publicadorDeAlertas;
    private final ObjectMapper objectMapper;
    private final int calibracionDias;
    private final int recalibracionDias;
    private final double coberturaMinima;
    private final int huecoMaximoMinutos;

    public SaludDetectorStrategyImpl(
            MlSaludClient mlSaludClient,
            TelemetriaRepository telemetriaRepository,
            AlertaRepository alertaRepository,
            PerfilReferenciaRepository perfilReferenciaRepository,
            PublicadorDeAlertas publicadorDeAlertas,
            ObjectMapper objectMapper,
            @Value("${vigia.salud.calibracion-dias:7}") int calibracionDias,
            @Value("${vigia.salud.recalibracion-dias:30}") int recalibracionDias,
            @Value("${vigia.salud.cobertura-minima:0.9}") double coberturaMinima,
            @Value("${vigia.salud.hueco-maximo-minutos:60}") int huecoMaximoMinutos) {
        this.mlSaludClient = mlSaludClient;
        this.telemetriaRepository = telemetriaRepository;
        this.alertaRepository = alertaRepository;
        this.perfilReferenciaRepository = perfilReferenciaRepository;
        this.publicadorDeAlertas = publicadorDeAlertas;
        this.objectMapper = objectMapper;
        this.calibracionDias = calibracionDias;
        this.recalibracionDias = recalibracionDias;
        this.coberturaMinima = coberturaMinima;
        this.huecoMaximoMinutos = huecoMaximoMinutos;
    }

    @Override
    public void process(List<Telemetria> telemetriaList, String correlationId) {
        if (telemetriaList == null || telemetriaList.isEmpty()) {
            return;
        }
        try {
            procesar(telemetriaList, correlationId);
        } catch (RuntimeException e) {
            marcarInferenciaPendiente(telemetriaList, correlationId, e);
        }
    }

    @SuppressWarnings("null")
    private void procesar(List<Telemetria> telemetriaList, String correlationId) {

        Caravana caravana = telemetriaList.get(0).getCaravana();
        Animal animal = caravana != null ? caravana.getAnimal() : null;
        LocalDateTime ahora = LocalDateTime.now();

        if (animal == null) {
            log.warn("Caravana sin animal asociado, no se puede evaluar salud (correlationId: {}).", correlationId);
            marcarCompletados(telemetriaList);
            return;
        }

        if (alertaRepository.existsByAnimalAndDisparadorAndTsGeneracionAfter(animal,
                DisparadorAlerta.SALUD, ahora.minusHours(24))) {
            log.info("Omitiendo inferencia de salud para animal {} porque ya tiene una alerta reciente (últimas 24hs).",
                    animal.getId());
            marcarCompletados(telemetriaList);
            return;
        }

        Optional<PerfilReferencia> perfilExistente = perfilReferenciaRepository.findByAnimal(animal);

        PerfilReferencia perfilReferencia;
        if (perfilExistente.isEmpty()) {
            // Primera calibración: los primeros N días de datos del animal, sin importar cuándo se dio de
            // alta (decidido 2026-10-16, reemplaza los "7 días desde el alta" de RF03). Se cuenta desde el
            // día en que se le asignó la caravana, para no usar datos de un animal anterior que la tuvo.
            LocalDateTime desdeAsignacion = caravana.getFechaAsignacion() != null
                    ? caravana.getFechaAsignacion().atStartOfDay()
                    : LocalDate.EPOCH.atStartOfDay(); // sin fecha de asignación: el primer dato de la caravana
            Optional<Telemetria> primerDato = telemetriaRepository
                    .findFirstByCaravanaAndFechaHoraGreaterThanEqualOrderByFechaHoraAsc(caravana, desdeAsignacion);
            if (primerDato.isEmpty()) {
                log.info("Animal {} en calibración: su caravana no tiene datos desde {}.", animal.getId(),
                        desdeAsignacion);
                marcarCompletados(telemetriaList);
                return;
            }
            LocalDateTime inicio = primerDato.get().getFechaHora();
            LocalDateTime fin = inicio.plusDays(calibracionDias);
            if (ahora.isBefore(fin)) {
                log.info("Animal {} en calibración: primer dato {}, termina {}. No se generan predicciones.",
                        animal.getId(), inicio, fin);
                marcarCompletados(telemetriaList);
                return;
            }
            perfilReferencia = calcularYPersistirPerfil(animal, perfilExistente, caravana, inicio, fin, ahora,
                    correlationId);
            if (perfilReferencia == null) {
                // Los primeros N días no alcanzan (p. ej. el tag estuvo sin cobertura esa semana): en vez de
                // reintentar siempre la misma ventana, los últimos N días. Igual empiezan después del primer
                // dato, porque ahora >= fin = inicio + N días (decidido 2026-10-29).
                perfilReferencia = calcularYPersistirPerfil(animal, perfilExistente, caravana,
                        ahora.minusDays(calibracionDias), ahora, ahora, correlationId);
            }
        } else if (perfilExistente.get().getFechaCalculo().isBefore(ahora.minusDays(recalibracionDias))) {
            // Recalibración periódica: los últimos N días "recientes",
            // para que el perfil se adapte a cambios legítimos de
            // comportamiento con el tiempo (ver discusión de deriva del
            // perfil).
            PerfilReferencia recalculado = calcularYPersistirPerfil(animal, perfilExistente, caravana,
                    ahora.minusDays(calibracionDias), ahora, ahora, correlationId);
            // Sin datos suficientes para recalibrar se sigue evaluando con el perfil anterior; su fechaCalculo
            // no cambia, así que se reintenta en el próximo ciclo.
            perfilReferencia = recalculado != null ? recalculado : perfilExistente.get();
        } else {
            perfilReferencia = perfilExistente.get();
        }

        if (perfilReferencia == null) {
            // Todavía no hay suficiente telemetría para armar una
            // ventana de calibración completa (ej. el dispositivo
            // tuvo caídas). Se reintenta en el próximo ciclo del
            // batch, no es un error.
            marcarCompletados(telemetriaList);
            return;
        }

        Map<String, Object> perfil = deserializarPerfil(perfilReferencia.getPerfilJson());

        // ml-salud exige EXACTAMENTE SAMPLES_PER_DAY (288) puntos: 24hs a 5 min, ver
        // features_salud.py::compute_features. telemetriaList son todas las lecturas de las ultimas 24hs
        // (SaludBatchConfig), ordenadas por fechaHora. Con mas de 288 (los simuladores de la demo mandan
        // cada 5 s) van las 288 mas recientes, como siempre; con menos (uplinks perdidos), puntosParaMl
        // rellena los huecos o, si faltan demasiados, se reintenta en el proximo ciclo.
        List<PuntoTelemetriaDTO> serie = puntosParaMl(telemetriaList, ahora.minusHours(24), SAMPLES_PER_DAY,
                coberturaMinima, huecoMaximoMinutos);
        if (serie.isEmpty()) {
            log.info(
                    "Animal {} todavia no tiene una ventana completa de 24hs para evaluar ({} lecturas; se exige el {} % de los slots de 5 min y ningun hueco de mas de {} min); se reintenta en el proximo ciclo.",
                    animal.getId(), telemetriaList.size(), Math.round(coberturaMinima * 100), huecoMaximoMinutos);
            marcarCompletados(telemetriaList);
            return;
        }
        if (serie.size() > SAMPLES_PER_DAY) {
            serie = serie.subList(serie.size() - SAMPLES_PER_DAY, serie.size());
        }
        // El grafico comparativo de la alerta muestra las lecturas reales, no los puntos rellenados.
        List<Telemetria> ventanaEvaluar = conDatos(telemetriaList);
        if (ventanaEvaluar.size() > SAMPLES_PER_DAY) {
            ventanaEvaluar = ventanaEvaluar.subList(ventanaEvaluar.size() - SAMPLES_PER_DAY, ventanaEvaluar.size());
        }

        SaludPredictResponseDTO response = mlSaludClient.predict(
                SaludPredictRequestDTO.builder().serie(serie).perfil(perfil).build(), correlationId);

        log.info("Respuesta de microservicio de salud recibida: {}", response);
        marcarCompletados(telemetriaList);

        if (response != null && response.isDesvioDetectado()) {
            generarAlerta(animal, response, ventanaEvaluar, perfil, perfilReferencia.getFechaCalculo());
        }
    }

    @SuppressWarnings("null")
    private PerfilReferencia calcularYPersistirPerfil(Animal animal, Optional<PerfilReferencia> perfilExistente,
            Caravana caravana, LocalDateTime desde, LocalDateTime hasta, LocalDateTime ahora, String correlationId) {
        boolean esPrimerCalculo = perfilExistente.isEmpty();

        List<Telemetria> calibracion = telemetriaRepository
                .findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(caravana, desde, hasta);

        // Mismo criterio que la ventana evaluada: los huecos chicos se rellenan; sin cobertura suficiente no
        // se llama a ml-salud, que respondería 422 por no tener 7 días completos.
        List<PuntoTelemetriaDTO> puntos = puntosParaMl(calibracion, desde, calibracionDias * SAMPLES_PER_DAY,
                coberturaMinima, huecoMaximoMinutos);
        if (puntos.isEmpty()) {
            log.warn(
                    "Animal {} sin cobertura suficiente en la ventana de calibración [{}, {}) ({} lecturas; se exige el {} % de los slots de 5 min y ningún hueco de más de {} min), se reintenta en el próximo ciclo.",
                    animal.getId(), desde, hasta, calibracion.size(), Math.round(coberturaMinima * 100),
                    huecoMaximoMinutos);
            return null;
        }

        log.info("Calculando perfil de salud para animal {} ({} puntos de [{}, {}), primera calibración: {}, correlationId: {})",
                animal.getId(), puntos.size(), desde, hasta, esPrimerCalculo, correlationId);

        PerfilCalibracionResponseDTO response = mlSaludClient.calcularPerfil(
                PerfilCalibracionRequestDTO.builder().calibracion(puntos).build(), correlationId);

        if (response == null || response.getPerfil() == null) {
            log.warn("ml-salud no devolvió un perfil válido para el animal {} (correlationId: {}).",
                    animal.getId(), correlationId);
            return null;
        }

        try {
            PerfilReferencia perfilReferencia = perfilExistente.orElseGet(PerfilReferencia::new);
            perfilReferencia.setAnimal(animal);
            perfilReferencia.setPerfilJson(objectMapper.writeValueAsString(response.getPerfil()));
            perfilReferencia.setFechaCalculo(ahora);
            return perfilReferenciaRepository.save(perfilReferencia);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el perfil de salud del animal " + animal.getId(), e);
        }
    }

    private Map<String, Object> deserializarPerfil(String perfilJson) {
        try {
            return objectMapper.readValue(perfilJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Perfil de salud persistido inválido/corrupto", e);
        }
    }

    private void generarAlerta(Animal animal, SaludPredictResponseDTO response, List<Telemetria> ventanaEvaluar,
            Map<String, Object> perfil, LocalDateTime perfilCalculadoEn) {
        SeveridadAlerta severidad = mapearSeveridad(response.getSeveridad());
        if (severidad == null) {
            // severidad 0 = sin desvío, response.desvioDetectado() ya
            // filtra esto, pero se deja la guarda por las dudas ante
            // una respuesta inconsistente del microservicio.
            return;
        }

        log.info("¡Desvío sanitario detectado para animal {}! severidad={} confianza={}",
                animal.getId(), response.getSeveridad(), response.getConfianzaModelo());

        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setTipo("SALUD");
        alerta.setSeveridad(severidad);
        alerta.setTsGeneracion(LocalDateTime.now());
        alerta.setDisparador(DisparadorAlerta.SALUD);
        alerta.setValorUmbral(0.5);
        alerta.setValorDetectado((double) response.getConfianzaModelo());
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setConfianzaModelo(response.getConfianzaModelo());
        alerta.setDesvioDetectado(true);
        alerta.setPosibleCausa(response.getPosibleCausa());
        alerta.setSerieComparativaJson(serializarSerieComparativa(animal, ventanaEvaluar, perfil, perfilCalculadoEn,
                severidad.name(), response.getPosibleCausa()));
        alerta.setAccionRecomendada(buildAccionRecomendada(response.getPosibleCausa(), severidad));

        alerta = alertaRepository.save(alerta);

        AlertaEventDTO eventDTO = AlertaEventDTO.builder()
                .id(alerta.getId())
                .animalId(animal.getId())
                .tipo(alerta.getTipo())
                .severidad(alerta.getSeveridad())
                .tsGeneracion(alerta.getTsGeneracion())
                .disparador(alerta.getDisparador())
                .valorUmbral(alerta.getValorUmbral())
                .valorDetectado(alerta.getValorDetectado())
                .estado(alerta.getEstado())
                .confianzaModelo(alerta.getConfianzaModelo())
                .desvioDetectado(alerta.getDesvioDetectado())
                .posibleCausa(alerta.getPosibleCausa())
                .accionRecomendada(buildAccionRecomendada(alerta.getPosibleCausa(), alerta.getSeveridad()))
                .nueva(true)
                .build();

        publicadorDeAlertas.publicar(eventDTO);
        log.info("Alerta de salud {} generada; se publica al commitear", alerta.getId());
    }

    private String serializarSerieComparativa(Animal animal, List<Telemetria> ventanaEvaluar,
            Map<String, Object> perfil, LocalDateTime perfilCalculadoEn, String severidad, String posibleCausa) {
        try {
            SerieComparativaSnapshot snapshot = SerieComparativaBuilder.build(
                    ventanaEvaluar, perfil, perfilCalculadoEn, severidad, posibleCausa);
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("No se pudo serializar la serie comparativa para el animal {}: {}",
                    animal.getId(), e.getMessage());
            return null;
        }
    }

    private SeveridadAlerta mapearSeveridad(int severidad) {
        switch (severidad) {
            case 1:
                return SeveridadAlerta.BAJA;
            case 2:
                return SeveridadAlerta.MEDIA;
            case 3:
                return SeveridadAlerta.ALTA;
            default:
                return null;
        }
    }

    private void marcarCompletados(List<Telemetria> telemetriaList) {
        // Toca estadoInferenciaSalud (propio de este pipeline), no
        // estadoInferenciaCelo (propio de celo) -- ver comentario en
        // Telemetria.java sobre por que estan separados.
        List<Telemetria> pendientes = telemetriaList.stream()
                .filter(t -> t.getEstadoInferenciaSalud() == EstadoInferencia.PENDIENTE)
                .collect(Collectors.toList());
        pendientes.forEach(t -> t.setEstadoInferenciaSalud(EstadoInferencia.COMPLETADO));
        telemetriaRepository.saveAll(pendientes);
    }

    /** ml-salud no respondió (o falló el procesamiento): se deja la telemetría para reintentar. */
    void marcarInferenciaPendiente(List<Telemetria> telemetriaList, String correlationId, Throwable t) {
        log.error(
                "Fallo al comunicarse con el microservicio ML de salud (Correlation-ID: {}). Marcando como inferencia pendiente. Motivo: {}",
                correlationId, t.getMessage());
        telemetriaList
                .forEach(telemetria -> telemetria.setEstadoInferenciaSalud(EstadoInferencia.INFERENCIA_PENDIENTE));
        telemetriaRepository.saveAll(telemetriaList);
    }

    String buildAccionRecomendada(String posibleCausa, SeveridadAlerta severidad) {
        // ml-salud puede devolver posible_causa nula: no mostrar "null" al productor.
        String causa = (posibleCausa == null || posibleCausa.isBlank())
                ? "no determinada"
                : posibleCausa.trim();
        if (severidad == null) {
            return "Revisar al animal y realizar seguimiento clínico. Causa sospechada: " + causa + ".";
        }
        switch (severidad) {
            case ALTA:
                return "Atención veterinaria urgente y aislamiento en enfermería. Causa sospechada: " + causa
                        + " (riesgo alto).";
            case MEDIA:
                return "Apartar al animal para revisión clínica y evaluar tratamiento. Causa sospechada: "
                        + causa + " (riesgo medio).";
            case BAJA:
                return "Monitorear la evolución y el comportamiento del animal en los próximos rodeos. Causa sospechada: "
                        + causa + " (riesgo bajo).";
            default:
                return "Revisar al animal y realizar seguimiento clínico. Causa sospechada: " + causa + ".";
        }
    }
}
