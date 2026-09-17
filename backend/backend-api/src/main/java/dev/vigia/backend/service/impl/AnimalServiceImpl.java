package dev.vigia.backend.service.impl;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import dev.vigia.backend.dto.AlertaResponse;
import dev.vigia.backend.dto.AnimalIndicadoresSaludResponse;
import dev.vigia.backend.dto.AnimalMapaResponse;
import dev.vigia.backend.dto.AnimalMetricaResponse;
import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.CargaMasivaResponse;
import dev.vigia.backend.dto.CargaMasivaResult;
import dev.vigia.backend.dto.AnimalResponse;
import dev.vigia.backend.dto.EstadoReproductivoCsvRequest;
import dev.vigia.backend.dto.EstadoReproductivoParseResult;
import dev.vigia.backend.dto.CargaMasivaEstadoResponse;
import dev.vigia.backend.dto.FilaEstadoInvalida;
import dev.vigia.backend.dto.IndicadorResponse;
import dev.vigia.backend.dto.EstadoReproductivoRequest;
import dev.vigia.backend.dto.EstadoReproductivoResponse;
import dev.vigia.backend.dto.EstadoSaludRequest;
import dev.vigia.backend.dto.EstadoSaludResponse;
import dev.vigia.backend.dto.IntervencionResponse;
import dev.vigia.backend.dto.RegistroPesoRequest;
import dev.vigia.backend.dto.RegistroPesoResponse;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.OrigenEstado;
import dev.vigia.backend.model.PerfilReferencia;
import dev.vigia.backend.model.EstadoSalud;
import dev.vigia.backend.model.RegistroPeso;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.repository.EstadoSaludRepository;
import dev.vigia.backend.repository.RegistroPesoRepository;
import dev.vigia.backend.service.AnimalService;
import dev.vigia.backend.service.CargaMasivaService;
import dev.vigia.backend.service.EstadoReproductivoCsvService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnimalServiceImpl implements AnimalService {

        private final AnimalRepository animalRepository;
        private final AlertaRepository alertaRepository;
        private final UsuarioRepository usuarioRepository;
        private final EstablecimientoRepository establecimientoRepository;
        private final RodeoLoteRepository rodeoLoteRepository;
        private final EstadoReproductivoRepository estadoReproductivoRepository;
        private final IntervencionRepository intervencionRepository;
        private final TelemetriaRepository telemetriaRepository;
        private final EstadoSaludRepository estadoSaludRepository;
        private final RegistroPesoRepository registroPesoRepository;
        private final CaravanaRepository caravanaRepository;

        @Value("${vigia.salud.calibracion-dias:7}")
        private int calibracionDias;

        private static final double SIGMA_AMARILLO_MOVIMIENTO = 1.0;

        private static final double SIGMA_ROJO_MOVIMIENTO = 2.0;

        private static final long HORAS_SIN_DATOS_MOVIMIENTO = 6;

        private final CargaMasivaService cargaMasivaService;
        private final EstadoReproductivoCsvService estadoReproductivoCsvService;

        @Override
        @Transactional
        @SuppressWarnings("null")
        public AnimalResponse cargarAnimal(String emailProductor, AnimalRequest request) {
                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                Establecimiento establecimiento = establecimientoRepository.findById(request.getEstablecimientoId())
                                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

                boolean productorActivoEnEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!productorActivoEnEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                if (animalRepository.existsByIdCaravanaOficialAndEstablecimientoId(request.getIdCaravanaOficial(),
                                request.getEstablecimientoId())) {
                        throw new IllegalArgumentException(
                                        "El identificador ya se encuentra registrado en este establecimiento.");
                }

                if (request.getRodeoLoteId() != null) {
                        rodeoLoteRepository.findById(request.getRodeoLoteId())
                                        .orElseThrow(() -> new IllegalArgumentException("Rodeo lote no encontrado."));
                }

                Animal animal = new Animal(
                                establecimiento,
                                request.getRodeoLoteId() != null
                                                ? rodeoLoteRepository.findById(request.getRodeoLoteId()).get()
                                                : null);

                animal.setIdCaravanaOficial(request.getIdCaravanaOficial());
                animal.setAlias(request.getAlias());
                animal.setFechaNacimiento(request.getFechaNacimiento());
                animal.setRaza(request.getRaza());
                animal.setEstadoActual(request.getEstadoActual());

                Animal savedAnimal = animalRepository.save(animal);

                if (request.getPesoInicial() != null) {
                        RegistroPeso registroInicial = new RegistroPeso();
                        registroInicial.setAnimal(savedAnimal);
                        registroInicial.setValorKg(request.getPesoInicial());
                        registroInicial.setUnidad("kg");
                        registroInicial.setFecha(LocalDate.now());
                        registroInicial.setFechaRegistro(java.time.LocalDateTime.now());
                        registroPesoRepository.save(registroInicial);
                }

                if (request.getCaravanaId() != null) {
                        asignarCaravanaInterna(savedAnimal, request.getCaravanaId());
                        savedAnimal = animalRepository.save(savedAnimal);
                }

                return mapToResponse(savedAnimal);
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public AnimalResponse editarAnimal(Long animalId, String emailProductor, AnimalRequest request) {
                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimientoActual = animal.getEstablecimiento();
                boolean esProductorOEmpleadoDelEstablecimiento = establecimientoActual.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!esProductorOEmpleadoDelEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                if (animal.getEstadoActual().equals(EstadoAnimal.INACTIVO)) {
                        throw new IllegalArgumentException("No se puede editar un animal inactivo.");
                }

                Establecimiento nuevoEstablecimiento = establecimientoActual;
                if (!request.getEstablecimientoId().equals(establecimientoActual.getId())) {
                        nuevoEstablecimiento = establecimientoRepository.findById(request.getEstablecimientoId())
                                        .orElseThrow(() -> new IllegalArgumentException(
                                                        "Nuevo establecimiento no encontrado."));

                        boolean activoEnNuevoEstablecimiento = nuevoEstablecimiento.getEmpleados().stream()
                                        .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                        if (!activoEnNuevoEstablecimiento) {
                                throw new IllegalArgumentException(
                                                "El productor no pertenece al nuevo establecimiento destino o no está activo.");
                        }
                }

                if (!request.getIdCaravanaOficial().equals(animal.getIdCaravanaOficial()) ||
                                !request.getEstablecimientoId().equals(establecimientoActual.getId())) {

                        if (animalRepository.existsByIdCaravanaOficialAndEstablecimientoId(
                                        request.getIdCaravanaOficial(),
                                        request.getEstablecimientoId())) {
                                throw new IllegalArgumentException(
                                                "El identificador ya se encuentra registrado en el establecimiento destino.");
                        }
                }

                if (request.getRodeoLoteId() != null) {
                        RodeoLote lote = rodeoLoteRepository.findById(request.getRodeoLoteId())
                                        .orElseThrow(() -> new IllegalArgumentException("Rodeo lote no encontrado."));
                        if (!lote.getEstablecimiento().getId().equals(nuevoEstablecimiento.getId())) {
                                throw new IllegalArgumentException("El rodeo no pertenece al establecimiento destino.");
                        }
                        animal.setRodeoLote(lote);
                } else {
                        animal.setRodeoLote(null);
                }

                animal.setEstablecimiento(nuevoEstablecimiento);
                animal.setIdCaravanaOficial(request.getIdCaravanaOficial());
                animal.setAlias(request.getAlias());
                animal.setFechaNacimiento(request.getFechaNacimiento());
                animal.setRaza(request.getRaza());

                if (request.getCaravanaId() != null) {
                        if (animal.getCaravana() == null || !animal.getCaravana().getId().equals(request.getCaravanaId())) {
                                asignarCaravanaInterna(animal, request.getCaravanaId());
                        }
                }

                Animal savedAnimal = animalRepository.save(animal);
                return mapToResponse(savedAnimal);
        }

        @Override
        @SuppressWarnings("null")
        public void desactivarAnimal(Long animalId, String emailProductor) {
                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean esProductorOEmpleadoDelEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!esProductorOEmpleadoDelEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                if (animal.getEstadoActual().equals(EstadoAnimal.INACTIVO)) {
                        throw new IllegalArgumentException("El animal no está activo.");
                }

                if (animal.getCaravana() != null) {
                        Caravana caravana = animal.getCaravana();
                        caravana.setEstadoSensor(EstadoSensor.DISPONIBLE);
                        caravana.setActivo(false);
                        caravanaRepository.save(caravana);
                        animal.setCaravana(null);
                }

                animal.setEstadoActual(EstadoAnimal.INACTIVO);
                animalRepository.save(animal);
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public AnimalResponse getAnimalById(Long animalId, String emailProductor) {
                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));
                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean esProductorOEmpleadoDelEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!esProductorOEmpleadoDelEstablecimiento && !productorUser.getRol().equals(Rol.ADMINISTRADOR)) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                return mapToResponse(animal);
        }

        @Override
        public List<AnimalResponse> getAllAnimales() {
                List<Animal> animales = animalRepository.findAll();
                return animales.stream().map(this::mapToResponse).toList();
        }

        private void calcularCalibracion(Animal animal, AnimalResponse.AnimalResponseBuilder builder) {
                LocalDate fechaBase = animal.getFechaAlta();
                if (fechaBase != null) {
                        if (animal.getCaravana() != null && animal.getCaravana().getFechaAsignacion() != null) {
                                if (animal.getCaravana().getFechaAsignacion().isAfter(fechaBase)) {
                                        fechaBase = animal.getCaravana().getFechaAsignacion();
                                }
                        }
                        LocalDate finCalibracion = fechaBase.plusDays(calibracionDias);
                        LocalDate hoy = LocalDate.now();
                        if (!hoy.isAfter(finCalibracion)) {
                                builder.enCalibracion(true);
                                builder.diasRestantesCalibracion((int) ChronoUnit.DAYS.between(hoy, finCalibracion));
                        } else {
                                builder.enCalibracion(false);
                                builder.diasRestantesCalibracion(0);
                        }
                }
        }

        private void calcularCalibracionMapa(Animal animal, AnimalMapaResponse.AnimalMapaResponseBuilder builder) {
                LocalDate fechaBase = animal.getFechaAlta();
                if (fechaBase != null) {
                        if (animal.getCaravana() != null && animal.getCaravana().getFechaAsignacion() != null) {
                                if (animal.getCaravana().getFechaAsignacion().isAfter(fechaBase)) {
                                        fechaBase = animal.getCaravana().getFechaAsignacion();
                                }
                        }
                        LocalDate finCalibracion = fechaBase.plusDays(calibracionDias);
                        LocalDate hoy = LocalDate.now();
                        if (!hoy.isAfter(finCalibracion)) {
                                builder.enCalibracion(true);
                                builder.diasRestantesCalibracion((int) ChronoUnit.DAYS.between(hoy, finCalibracion));
                        } else {
                                builder.enCalibracion(false);
                                builder.diasRestantesCalibracion(0);
                        }
                }
        }

        private AnimalResponse mapToResponse(Animal animal) {
                EstadoReproductivo ultimoEstado = animal.getEstadosReproductivos().stream()
                                .findFirst()
                                .orElse(null);

                EstadoSalud ultimoEstadoSalud = animal.getEstadosSalud().stream()
                                .findFirst()
                                .orElse(null);

                AnimalResponse.AnimalResponseBuilder builder = AnimalResponse.builder()
                                .id(animal.getId())
                                .idCaravanaOficial(animal.getIdCaravanaOficial())
                                .alias(animal.getAlias())
                                .fechaNacimiento(animal.getFechaNacimiento())
                                .raza(animal.getRaza())
                                .estadoActual(animal.getEstadoActual())
                                .rodeoLoteId(animal.getRodeoLote() != null ? animal.getRodeoLote().getId() : null)
                                .rodeoLoteNombre(animal.getRodeoLote() != null ? animal.getRodeoLote().getNombre()
                                                : null)
                                .establecimientoId(
                                                animal.getEstablecimiento() != null
                                                                ? animal.getEstablecimiento().getId()
                                                                : null)
                                .fechaAlta(animal.getFechaAlta())
                                .estadoReproductivo(ultimoEstado != null ? ultimoEstado.getEstado() : null)
                                .fechaUltimoParto(ultimoEstado != null ? ultimoEstado.getFechaUltParto() : null)
                                .fechaUltimaInseminacion(
                                                ultimoEstado != null ? ultimoEstado.getFechaUltInseminacion() : null)
                                .estadoSalud(ultimoEstadoSalud != null ? ultimoEstadoSalud.getEstado() : null);

                if (animal.getCaravana() != null) {
                        Caravana c = animal.getCaravana();
                        builder.caravanaId(c.getId())
                                        .caravanaDevEui(c.getDevEui())
                                        .caravanaAlias(c.getAlias())
                                        .caravanaEstado(c.getEstadoSensor())
                                        .caravanaUltimaSenial(c.getUltimaSenial())
                                        .caravanaBateria(c.getBateria());
                }

                calcularCalibracion(animal, builder);
                return builder.build();
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public List<AnimalResponse> listarAnimales(String emailUsuario, Long establecimientoId) {

                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                if (usuario.getRol().equals(Rol.ADMINISTRADOR)) {
                        return animalRepository.findByEstadoActualNot(EstadoAnimal.INACTIVO)
                                        .stream()
                                        .map(this::mapToResponse)
                                        .collect(Collectors.toList());
                }

                List<Establecimiento> establecimientos = establecimientoRepository
                                .findActiveEstablecimientoByUsuarioEmail(emailUsuario);

                if (establecimientos.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "El usuario no pertenece a ningún establecimiento activo.");
                }

                if (establecimientoId != null) {
                        boolean pertenece = establecimientos.stream()
                                        .anyMatch(e -> e.getId().equals(establecimientoId));
                        if (!pertenece) {
                                throw new org.springframework.security.access.AccessDeniedException(
                                                "No tiene acceso a este establecimiento");
                        }
                        return animalRepository
                                        .findByEstablecimientoIdInAndEstadoActualNot(List.of(establecimientoId),
                                                        EstadoAnimal.INACTIVO)
                                        .stream()
                                        .map(this::mapToResponse)
                                        .collect(Collectors.toList());
                }

                List<Long> establecimientoIds = establecimientos.stream()
                                .map(Establecimiento::getId)
                                .collect(Collectors.toList());

                return animalRepository
                                .findByEstablecimientoIdInAndEstadoActualNot(establecimientoIds, EstadoAnimal.INACTIVO)
                                .stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public List<AnimalMapaResponse> getPosicionesMapa(String emailUsuario, Long establecimientoId) {
                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                List<Animal> animales;
                if (usuario.getRol().equals(Rol.ADMINISTRADOR)) {
                        animales = animalRepository.findByEstadoActualNot(EstadoAnimal.INACTIVO);
                        if (establecimientoId != null) {
                                animales = animales.stream()
                                                .filter(a -> a.getEstablecimiento().getId().equals(establecimientoId))
                                                .collect(Collectors.toList());
                        }
                } else {
                        List<Establecimiento> establecimientos = establecimientoRepository
                                        .findActiveEstablecimientoByUsuarioEmail(emailUsuario);

                        if (establecimientos.isEmpty()) {
                                throw new IllegalArgumentException(
                                                "El usuario no pertenece a ningún establecimiento activo.");
                        }

                        if (establecimientoId != null) {
                                boolean pertenece = establecimientos.stream()
                                                .anyMatch(e -> e.getId().equals(establecimientoId));
                                if (!pertenece) {
                                        throw new AccessDeniedException("No tiene acceso a este establecimiento");
                                }
                                animales = animalRepository.findByEstablecimientoIdInAndEstadoActualNot(
                                                List.of(establecimientoId), EstadoAnimal.INACTIVO);
                        } else {
                                List<Long> establecimientoIds = establecimientos.stream()
                                                .map(Establecimiento::getId)
                                                .collect(Collectors.toList());
                                animales = animalRepository.findByEstablecimientoIdInAndEstadoActualNot(
                                                establecimientoIds, EstadoAnimal.INACTIVO);
                        }
                }

                LocalDateTime hace15Minutos = LocalDateTime.now().minusMinutes(15);

                return animales.stream().map(animal -> {
                        AnimalMapaResponse.AnimalMapaResponseBuilder builder = AnimalMapaResponse.builder()
                                        .id(animal.getId())
                                        .identificador(animal.getIdCaravanaOficial());
                        calcularCalibracionMapa(animal, builder);

                        if (animal.getCaravana() != null) {
                                Optional<Telemetria> ultimaPos = telemetriaRepository
                                                .findFirstByCaravanaOrderByFechaHoraDesc(animal.getCaravana());
                                if (ultimaPos.isPresent()) {
                                        Telemetria t = ultimaPos.get();
                                        builder.latitud(t.getLatitud());
                                        builder.longitud(t.getLongitud());
                                        builder.tsUltimaPosicion(t.getFechaHora());
                                        builder.sinDatosRecientes(t.getFechaHora().isBefore(hace15Minutos));
                                } else {
                                        builder.sinDatosRecientes(true);
                                }
                        } else {
                                builder.sinDatosRecientes(true);
                        }

                        List<Alerta> alertasAnimal = alertaRepository.alertasAnimalActivas(animal.getId());
                        List<AlertaResponse> alertasAnimalResponse = alertasAnimal.stream()
                                        .map(this::alertaToAlertaResponse)
                                        .collect(Collectors.toList());
                        builder.alertasAnimal(alertasAnimalResponse);

                        return builder.build();
                }).collect(Collectors.toList());
        }

        @Override
        @SuppressWarnings("null")
        public EstadoReproductivoResponse registrarEstadoReproductivo(Long animalId, String emailProductor,
                        EstadoReproductivoRequest request) {
                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean productorActivoEnEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!productorActivoEnEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                if (animal.getEstadoActual().equals(EstadoAnimal.INACTIVO)) {
                        throw new IllegalArgumentException("El animal no está activo.");
                }

                LocalDate hoy = LocalDate.now();
                if (request.getFechaUltimoParto() != null && request.getFechaUltimoParto().isAfter(hoy)) {
                        throw new IllegalArgumentException("La fecha de último parto no puede ser futura.");
                }
                if (request.getFechaUltimaInseminacion() != null && request.getFechaUltimaInseminacion().isAfter(hoy)) {
                        throw new IllegalArgumentException("La fecha de última inseminación no puede ser futura.");
                }

                EstadoReproductivo estado = new EstadoReproductivo();
                estado.setAnimal(animal);
                estado.setUsuarioRegistrador(productorUser);
                estado.setEstado(request.getEstado());
                estado.setFechaUltParto(request.getFechaUltimoParto());
                estado.setFechaUltInseminacion(request.getFechaUltimaInseminacion());
                estado.setOrigen(OrigenEstado.MANUAL);
                estado.setFechaRegistro(LocalDateTime.now());

                EstadoReproductivo saved = estadoReproductivoRepository.save(estado);

                // Si el estado registrado manualmente es CELO, generamos una Alerta
                // con DisparadorAlerta.CELO para que el dashboard y el panel de alertas
                // lo muestre igual que una detección automática del modelo ML.
                if (request.getEstado() == TipoReproductivo.CELO) {
                        Alerta alertaCelo = new Alerta();
                        alertaCelo.setAnimal(animal);
                        alertaCelo.setTipo("Celo Registrado Manualmente");
                        alertaCelo.setSeveridad(SeveridadAlerta.ALTA);
                        alertaCelo.setTsGeneracion(LocalDateTime.now());
                        alertaCelo.setDisparador(DisparadorAlerta.CELO);
                        alertaCelo.setValorUmbral(1.0);
                        alertaCelo.setValorDetectado(1.0);
                        alertaCelo.setEstado(EstadoAlerta.PENDIENTE);
                        alertaCelo.setConfianzaModelo(1.0f);
                        alertaCelo.setVentanaInicioIns(LocalDateTime.now().plusHours(6));
                        alertaCelo.setVentanaFinIns(LocalDateTime.now().plusHours(24));
                        alertaRepository.save(alertaCelo);
                }

                String registradoPor = productorUser.getNombre();
                if (productorUser.getApellido() != null && !productorUser.getApellido().isEmpty()) {
                        registradoPor += " " + productorUser.getApellido();
                } else if (registradoPor == null || registradoPor.isEmpty()) {
                        registradoPor = productorUser.getEmail();
                }

                return EstadoReproductivoResponse.builder()
                                .id(saved.getId())
                                .animalId(animal.getId())
                                .estado(saved.getEstado().name())
                                .fechaUltimoParto(saved.getFechaUltParto())
                                .fechaUltimaInseminacion(saved.getFechaUltInseminacion())
                                .origen(saved.getOrigen().name())
                                .fechaRegistro(saved.getFechaRegistro())
                                .registradoPor(registradoPor)
                                .build();
        }

        @Override
        @SuppressWarnings("null")
        public EstadoReproductivoResponse editarEstadoReproductivo(Long animalId, Long estadoId, String emailProductor,
                        EstadoReproductivoRequest request) {
                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean productorActivoEnEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!productorActivoEnEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece a este establecimiento o no está activo.");
                }

                if (animal.getEstadoActual().equals(EstadoAnimal.INACTIVO)) {
                        throw new IllegalArgumentException("El animal no está activo.");
                }

                EstadoReproductivo estado = estadoReproductivoRepository.findById(estadoId)
                                .orElseThrow(() -> new IllegalArgumentException("Estado reproductivo no encontrado."));

                if (!estado.getAnimal().getId().equals(animalId)) {
                        throw new IllegalArgumentException("El estado reproductivo no pertenece a este animal.");
                }

                LocalDate hoy = LocalDate.now();
                if (request.getFechaUltimoParto() != null && request.getFechaUltimoParto().isAfter(hoy)) {
                        throw new IllegalArgumentException("La fecha de último parto no puede ser futura.");
                }
                if (request.getFechaUltimaInseminacion() != null && request.getFechaUltimaInseminacion().isAfter(hoy)) {
                        throw new IllegalArgumentException("La fecha de última inseminación no puede ser futura.");
                }

                estado.setEstado(request.getEstado());
                estado.setFechaUltParto(request.getFechaUltimoParto());
                estado.setFechaUltInseminacion(request.getFechaUltimaInseminacion());
                estado.setUsuarioRegistrador(productorUser);

                EstadoReproductivo saved = estadoReproductivoRepository.save(estado);

                String registradoPor = productorUser.getNombre();
                if (productorUser.getApellido() != null && !productorUser.getApellido().isEmpty()) {
                        registradoPor += " " + productorUser.getApellido();
                } else if (registradoPor == null || registradoPor.isEmpty()) {
                        registradoPor = productorUser.getEmail();
                }

                return EstadoReproductivoResponse.builder()
                                .id(saved.getId())
                                .animalId(animal.getId())
                                .estado(saved.getEstado().name())
                                .fechaUltimoParto(saved.getFechaUltParto())
                                .fechaUltimaInseminacion(saved.getFechaUltInseminacion())
                                .origen(saved.getOrigen().name())
                                .fechaRegistro(saved.getFechaRegistro())
                                .registradoPor(registradoPor)
                                .build();
        }

        @Override
        @SuppressWarnings("null")
        public CargaMasivaResponse cargarAnimalesExcel(MultipartFile file, Long establecimientoId,
                        String emailProductor)
                        throws IOException {

                CargaMasivaResult result = cargaMasivaService.procesarCargaMasiva(file, establecimientoId,
                                emailProductor);

                List<AnimalResponse> animalesResponse = result.getAnimalesExitosos().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());

                return CargaMasivaResponse.builder()
                                .procesados(result.getProcesados())
                                .fallidos(result.getFallidos())
                                .exitosos(result.getAnimalesExitosos().size())
                                .animales(animalesResponse)
                                .errores(result.getErrores())
                                .build();
        }

        @Override
        @SuppressWarnings("null")
        @Transactional
        public CargaMasivaEstadoResponse cargarEstadosMasivo(MultipartFile file, Long establecimientoId,
                        String emailProductor)
                        throws IOException {

                Usuario productorUser = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario productor no encontrado."));

                Establecimiento establecimientoSeleccionado = establecimientoRepository.findById(establecimientoId)
                                .orElseThrow(() -> new IllegalArgumentException("Establecimiento no encontrado."));

                boolean productorActivoEnEstablecimiento = establecimientoSeleccionado.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(productorUser) && v.isActivo());

                if (!productorActivoEnEstablecimiento) {
                        throw new IllegalArgumentException(
                                        "El productor no pertenece al establecimiento seleccionado.");
                }

                EstadoReproductivoParseResult parseResult = estadoReproductivoCsvService.parseAndValidate(file);

                int exitosos = 0;
                List<FilaEstadoInvalida> errores = parseResult.getInvalidos();

                for (EstadoReproductivoCsvRequest fila : parseResult.getValidos()) {
                        try {
                                Animal animal = null;
                                if (fila.getIdCaravana() != null && !fila.getIdCaravana().isEmpty()) {
                                        animal = animalRepository
                                                        .findByIdCaravanaOficialAndEstablecimientoId(
                                                                        fila.getIdCaravana(), establecimientoId)
                                                        .orElse(null);
                                }
                                if (animal == null && fila.getAlias() != null && !fila.getAlias().isEmpty()) {
                                        animal = animalRepository
                                                        .findByAliasAndEstablecimientoId(fila.getAlias(),
                                                                        establecimientoId)
                                                        .orElse(null);
                                }

                                if (animal == null) {
                                        throw new IllegalArgumentException(
                                                        "Animal no encontrado con los identificadores proporcionados en este establecimiento.");
                                }

                                if (animal.getEstadoActual().equals(EstadoAnimal.INACTIVO)) {
                                        throw new IllegalArgumentException("El animal no está activo.");
                                }

                                EstadoReproductivo estado = new EstadoReproductivo();
                                estado.setAnimal(animal);
                                estado.setUsuarioRegistrador(productorUser);
                                estado.setEstado(fila.getEstado());
                                estado.setFechaUltParto(fila.getFechaUltimoParto());
                                estado.setFechaUltInseminacion(fila.getFechaUltimaInseminacion());
                                estado.setOrigen(OrigenEstado.CARGA_MASIVA);
                                estado.setFechaRegistro(LocalDateTime.now());

                                estadoReproductivoRepository.save(estado);
                                exitosos++;
                        } catch (Exception e) {
                                String animalIdentifier = fila.getIdCaravana() != null ? fila.getIdCaravana()
                                                : fila.getAlias();
                                errores.add(new FilaEstadoInvalida(0, animalIdentifier + ": " + e.getMessage()));
                        }
                }

                if (!errores.isEmpty()) {
                        try {
                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                        } catch (org.springframework.transaction.NoTransactionException e) {
                                // Ignorar en unit tests sin proxy
                        }
                        exitosos = 0;
                }

                return CargaMasivaEstadoResponse.builder()
                                .procesados(parseResult.getProcesados())
                                .exitosos(exitosos)
                                .fallidos(errores.size())
                                .errores(errores)
                                .build();
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public List<EstadoReproductivoResponse> listarEstadosReproductivos(Long animalId, String emailUsuario) {
                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean tieneAcceso = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(usuario) && v.isActivo())
                                || usuario.getRol().equals(Rol.ADMINISTRADOR);

                if (!tieneAcceso) {
                        throw new AccessDeniedException(
                                        "No tiene acceso a los datos de este animal.");
                }

                List<EstadoReproductivoResponse> historial = new ArrayList<>();

                animal.getEstadosReproductivos().forEach(e -> historial.add(EstadoReproductivoResponse.builder()
                                .id(e.getId())
                                .animalId(animal.getId())
                                .estado(e.getEstado() != null ? e.getEstado().name() : null)
                                .fechaUltimoParto(e.getFechaUltParto())
                                .fechaUltimaInseminacion(e.getFechaUltInseminacion())
                                .origen(e.getOrigen() != null ? e.getOrigen().name() : null)
                                .fechaRegistro(e.getFechaRegistro())
                                .registradoPor(formatNombreUsuario(e.getUsuarioRegistrador()))
                                .build()));

                historial.sort(java.util.Comparator.comparing(
                                EstadoReproductivoResponse::getFechaRegistro,
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));

                return historial;
        }

        private String formatNombreUsuario(Usuario usuario) {
                if (usuario == null) {
                        return null;
                }
                String nombre = usuario.getNombre();
                if (usuario.getApellido() != null && !usuario.getApellido().isEmpty()) {
                        nombre = (nombre != null ? nombre : "") + " " + usuario.getApellido();
                }
                if (nombre == null || nombre.isBlank()) {
                        nombre = usuario.getEmail();
                }
                return nombre;
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public List<IntervencionResponse> listarIntervenciones(Long animalId,
                        String emailUsuario) {
                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean tieneAcceso = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(usuario) && v.isActivo())
                                || usuario.getRol().equals(Rol.ADMINISTRADOR);

                if (!tieneAcceso) {
                        throw new IllegalArgumentException(
                                        "No tiene acceso a los datos de este animal.");
                }

                return intervencionRepository.findByAnimalIdOrderByFechaHoraDesc(animalId).stream()
                                .map(i -> {
                                        String interventorStr = i.getInterventor().getNombre();
                                        if (i.getInterventor().getApellido() != null
                                                        && !i.getInterventor().getApellido().isEmpty()) {
                                                interventorStr += " " + i.getInterventor().getApellido();
                                        }
                                        if (interventorStr == null || interventorStr.isBlank()) {
                                                interventorStr = i.getInterventor().getEmail();
                                        }

                                        return dev.vigia.backend.dto.IntervencionResponse.builder()
                                                        .id(i.getId())
                                                        .alertaId(i.getAlerta() != null ? i.getAlerta().getId() : null)
                                                        .animalId(i.getAnimal().getId())
                                                        .interventor(interventorStr)
                                                        .descripcion(i.getDescripcion())
                                                        .fechaHora(i.getFechaHora())
                                                        .tipo(i.getTipo())
                                                        .build();
                                })
                                .collect(Collectors.toList());
        }

        private AlertaResponse alertaToAlertaResponse(Alerta alerta) {
                return AlertaResponse.builder()
                                .id(alerta.getId())
                                .tipo(alerta.getTipo())
                                .animalId(alerta.getAnimal() != null ? alerta.getAnimal().getId() : null)
                                .tsGeneracion(alerta.getTsGeneracion())
                                .estado(alerta.getEstado())
                                .severidad(alerta.getSeveridad().name())
                                .build();
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public List<AnimalMetricaResponse> getMetricasAnimal(Long animalId, String emailUsuario) {
                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean tieneAcceso = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(usuario) && v.isActivo());

                if (!tieneAcceso) {
                        throw new AccessDeniedException(
                                        "No tiene acceso a los datos de este animal.");
                }

                List<AnimalMetricaResponse> result = new java.util.ArrayList<>();
                LocalDate hoy = LocalDate.now();
                LocalDate hace7Dias = hoy.minusDays(6);

                if (animal.getCaravana() == null) {
                        for (int i = 0; i < 7; i++) {
                                result.add(AnimalMetricaResponse.builder()
                                                .fecha(hace7Dias.plusDays(i))
                                                .sinDatos(true)
                                                .build());
                        }
                        return result;
                }

                LocalDateTime desde = hace7Dias.atStartOfDay();
                LocalDateTime hasta = hoy.atTime(23, 59, 59);

                List<Telemetria> telemetrias = telemetriaRepository
                                .findByCaravanaAndFechaHoraBetweenOrderByFechaHoraAsc(animal.getCaravana(), desde,
                                                hasta);

                Map<LocalDate, List<Telemetria>> agrupadoPorDia = telemetrias.stream()
                                .collect(Collectors.groupingBy(t -> t.getFechaHora().toLocalDate()));

                for (int i = 0; i < 7; i++) {
                        LocalDate fecha = hace7Dias.plusDays(i);
                        List<Telemetria> telemetriasDia = agrupadoPorDia.getOrDefault(fecha,
                                        Collections.emptyList());

                        if (telemetriasDia.isEmpty()) {
                                result.add(AnimalMetricaResponse.builder()
                                                .fecha(fecha)
                                                .sinDatos(true)
                                                .build());
                        } else {
                                OptionalDouble optActividad = telemetriasDia.stream()
                                                .filter(t -> t.getMoveState() != null)
                                                .mapToDouble(Telemetria::getMoveState)
                                                .average();
                                Double avgActividad = optActividad.isPresent() ? optActividad.getAsDouble() : null;

                                OptionalDouble optLat = telemetriasDia.stream()
                                                .filter(t -> t.getLatitud() != null)
                                                .mapToDouble(Telemetria::getLatitud)
                                                .average();
                                Double avgLat = optLat.isPresent() ? optLat.getAsDouble() : null;

                                OptionalDouble optLon = telemetriasDia.stream()
                                                .filter(t -> t.getLongitud() != null)
                                                .mapToDouble(Telemetria::getLongitud)
                                                .average();
                                Double avgLon = optLon.isPresent() ? optLon.getAsDouble() : null;

                                boolean sinDatos = (avgActividad == null && avgLat == null && avgLon == null);

                                result.add(AnimalMetricaResponse.builder()
                                                .fecha(fecha)
                                                .actividadMotora(avgActividad)
                                                .latitud(avgLat)
                                                .longitud(avgLon)
                                                .sinDatos(sinDatos)
                                                .build());
                        }
                }
                return result;
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public EstadoSaludResponse registrarEstadoSalud(Long animalId, String emailUsuario,
                        EstadoSaludRequest request) {
                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean perteneceAEstablecimiento = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(usuario) && v.isActivo());

                if (!perteneceAEstablecimiento && !usuario.getRol().equals(Rol.ADMINISTRADOR)) {
                        throw new AccessDeniedException("No tiene acceso a este establecimiento");
                }

                if (animal.getEstadoActual().equals(EstadoAnimal.INACTIVO)) {
                        throw new IllegalArgumentException(
                                        "No se pueden registrar estados de salud en animales inactivos.");
                }

                EstadoSalud estadoSalud = new EstadoSalud();
                estadoSalud.setAnimal(animal);
                estadoSalud.setUsuarioVeterinario(usuario);
                estadoSalud.setFechaRevision(request.getFecha() != null ? request.getFecha() : LocalDate.now());
                estadoSalud.setEstado(request.getEstado());
                estadoSalud.setDiagnosticoNotas(request.getDiagnosticoNotas());
                estadoSalud.setTratamientoIndicado(request.getTratamientoIndicado());
                estadoSalud.setOrigen(OrigenEstado.MANUAL);
                estadoSalud.setFechaRegistro(LocalDateTime.now());

                EstadoSalud savedEstado = estadoSaludRepository.save(estadoSalud);

                return mapToEstadoSaludResponse(savedEstado);
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public List<EstadoSaludResponse> listarEstadosSalud(Long animalId, String emailUsuario) {
                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean tieneAcceso = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(usuario) && v.isActivo())
                                || usuario.getRol().equals(Rol.ADMINISTRADOR);

                if (!tieneAcceso) {
                        throw new AccessDeniedException(
                                        "No tiene acceso a los datos de este animal.");
                }

                return estadoSaludRepository.findByAnimalIdOrderByFechaRegistroDesc(animalId).stream()
                                .map(this::mapToEstadoSaludResponse)
                                .collect(Collectors.toList());
        }

        private EstadoSaludResponse mapToEstadoSaludResponse(EstadoSalud estadoSalud) {
                return EstadoSaludResponse.builder()
                                .id(estadoSalud.getId())
                                .animalId(estadoSalud.getAnimal() != null ? estadoSalud.getAnimal().getId() : null)
                                .estado(estadoSalud.getEstado() != null ? estadoSalud.getEstado().name() : null)
                                .fechaRevision(estadoSalud.getFechaRevision())
                                .diagnosticoNotas(estadoSalud.getDiagnosticoNotas())
                                .tratamientoIndicado(estadoSalud.getTratamientoIndicado())
                                .origen(estadoSalud.getOrigen() != null ? estadoSalud.getOrigen().name() : null)
                                .fechaRegistro(estadoSalud.getFechaRegistro())
                                .registradoPor(formatNombreUsuario(estadoSalud.getUsuarioVeterinario()))
                                .build();
        }

        // ── Historial de pesos ────────────────────────────────────────────────────────

        @Override
        @Transactional
        @SuppressWarnings("null")
        public RegistroPesoResponse registrarPeso(Long animalId, String email, RegistroPesoRequest request) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                validarAccesoEstablecimiento(usuario, animal.getEstablecimiento());

                if (animal.getEstadoActual().equals(EstadoAnimal.INACTIVO)) {
                        throw new IllegalArgumentException("No se pueden registrar pesos en animales inactivos.");
                }

                RegistroPeso registro = new RegistroPeso();
                registro.setAnimal(animal);
                registro.setFecha(request.getFecha() != null ? request.getFecha() : LocalDate.now());
                registro.setValorKg(request.getValorKg());
                registro.setUnidad(request.getUnidad());
                registro.setFechaRegistro(LocalDateTime.now());

                return mapToRegistroPesoResponse(registroPesoRepository.save(registro));
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public RegistroPesoResponse editarPeso(Long animalId, Long pesoId, String email, RegistroPesoRequest request) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                validarAccesoEstablecimiento(usuario, animal.getEstablecimiento());

                RegistroPeso registro = registroPesoRepository.findById(pesoId)
                                .orElseThrow(() -> new IllegalArgumentException("Registro de peso no encontrado."));

                if (!registro.getAnimal().getId().equals(animalId)) {
                        throw new IllegalArgumentException("El registro de peso no corresponde a este animal.");
                }

                registro.setFecha(request.getFecha() != null ? request.getFecha() : registro.getFecha());
                registro.setValorKg(request.getValorKg());
                registro.setUnidad(request.getUnidad());

                return mapToRegistroPesoResponse(registroPesoRepository.save(registro));
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public void eliminarPeso(Long animalId, Long pesoId, String email) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                validarAccesoEstablecimiento(usuario, animal.getEstablecimiento());

                RegistroPeso registro = registroPesoRepository.findById(pesoId)
                                .orElseThrow(() -> new IllegalArgumentException("Registro de peso no encontrado."));

                if (!registro.getAnimal().getId().equals(animalId)) {
                        throw new IllegalArgumentException("El registro de peso no corresponde a este animal.");
                }

                registroPesoRepository.delete(registro);
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public List<RegistroPesoResponse> listarHistorialPesos(Long animalId, String email) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                validarAccesoEstablecimiento(usuario, animal.getEstablecimiento());

                return registroPesoRepository.findByAnimalIdOrderByFechaDesc(animalId).stream()
                                .map(this::mapToRegistroPesoResponse)
                                .collect(Collectors.toList());
        }

        private void validarAccesoEstablecimiento(Usuario usuario, Establecimiento establecimiento) {
                boolean tieneAcceso = establecimiento.getEmpleados().stream()
                                .anyMatch(e -> e.getUsuario().equals(usuario) && e.isActivo())
                                || usuario.getRol().equals(Rol.ADMINISTRADOR);
                if (!tieneAcceso) {
                        throw new AccessDeniedException("No tiene acceso a este establecimiento.");
                }
        }

        private RegistroPesoResponse mapToRegistroPesoResponse(RegistroPeso registro) {
                return RegistroPesoResponse.builder()
                                .id(registro.getId())
                                .animalId(registro.getAnimal() != null ? registro.getAnimal().getId() : null)
                                .fecha(registro.getFecha())
                                .valorKg(registro.getValorKg())
                                .unidad(registro.getUnidad())
                                .fechaRegistro(registro.getFechaRegistro())
                                .build();
        }

        @Override
        @Transactional
        public AnimalIndicadoresSaludResponse getIndicadoresSalud(Long animalId, String emailProductor) {
                Usuario usuario = usuarioRepository.findByEmail(emailProductor)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                Establecimiento establecimiento = animal.getEstablecimiento();

                boolean tieneAcceso = establecimiento.getEmpleados().stream()
                                .anyMatch(v -> v.getUsuario().equals(usuario) && v.isActivo())
                                || usuario.getRol().equals(Rol.ADMINISTRADOR);

                if (!tieneAcceso) {
                        throw new AccessDeniedException(
                                        "No tiene acceso a los datos de este animal.");
                }

                List<IndicadorResponse> indicadores = new ArrayList<>();
                indicadores.add(calcularIndicadorMovimiento(animal));
                indicadores.add(calcularIndicadorReproductivo(animal));

                return AnimalIndicadoresSaludResponse.builder()
                                .animalId(animal.getId())
                                .caravana(animal.getCaravana() != null ? animal.getCaravana().getCodigo() : null)
                                .indicadores(indicadores)
                                .build();
        }

        private IndicadorResponse calcularIndicadorMovimiento(Animal animal) {
                PerfilReferencia perfilAnimal = animal.getPerfilReferencia();

                Optional<Telemetria> ultimaTelemetria = animal.getCaravana() != null
                                ? telemetriaRepository.findFirstByCaravanaOrderByFechaHoraDesc(animal.getCaravana())
                                : Optional.empty();

                if (ultimaTelemetria.isEmpty() || perfilAnimal == null) {
                        return IndicadorResponse.builder()
                                        .nombre("Estado de movimiento")
                                        .descripcion(descripcionMovimiento("SIN DATOS"))
                                        .valor("0")
                                        .estado("SIN DATOS")
                                        .build();
                }

                Telemetria telemetria = ultimaTelemetria.get();

                if (telemetriaDesactualizada(telemetria)) {
                        return IndicadorResponse.builder()
                                        .nombre("Estado de movimiento")
                                        .descripcion(descripcionSinDatosPorCollarDesactualizado())
                                        .valor(String.valueOf(telemetria.getMoveState()))
                                        .estado("SIN DATOS")
                                        .build();
                }

                Gson gson = new Gson();
                JsonObject jsonPerfil = gson.fromJson(perfilAnimal.getPerfilJson(), JsonObject.class);
                Float meanMov = jsonPerfil.get("mean_moveState_mean").getAsFloat();
                Float stdMov = jsonPerfil.get("mean_moveState_std").getAsFloat();

                String estado = getEstadoSalud(telemetria.getMoveState(), meanMov, stdMov);

                return IndicadorResponse.builder()
                                .nombre("Estado de movimiento")
                                .descripcion(descripcionMovimiento(estado))
                                .valor(String.valueOf(telemetria.getMoveState()))
                                .estado(estado)
                                .build();
        }

        /** El collar se considera desactualizado si su última transmisión supera {@link #HORAS_SIN_DATOS_MOVIMIENTO}. */
        private boolean telemetriaDesactualizada(Telemetria telemetria) {
                Duration antiguedad = Duration.between(telemetria.getFechaHora(), LocalDateTime.now());
                return antiguedad.compareTo(Duration.ofHours(HORAS_SIN_DATOS_MOVIMIENTO)) > 0;
        }

        private String descripcionSinDatosPorCollarDesactualizado() {
                return "El collar no transmitió datos en las últimas " + HORAS_SIN_DATOS_MOVIMIENTO
                                + " horas; no se puede calcular el estado de movimiento actual.";
        }

        private IndicadorResponse calcularIndicadorReproductivo(Animal animal) {
                Optional<EstadoReproductivo> estadoReproductivo = estadoReproductivoRepository
                                .findFirstByAnimalIdOrderByFechaRegistroDesc(animal.getId());

                if (estadoReproductivo.isEmpty()) {
                        return IndicadorResponse.builder()
                                        .nombre("Estado reproductivo")
                                        .descripcion(descripcionReproductivo("SIN DATOS"))
                                        .valor("SIN DATOS")
                                        .estado("SIN DATOS")
                                        .build();
                }

                EstadoReproductivo estadoRep = estadoReproductivo.get();
                String estado = getEstadoReproductivo(estadoRep);

                return IndicadorResponse.builder()
                                .nombre("Estado reproductivo")
                                .descripcion(descripcionReproductivo(estado))
                                .valor(estadoRep.getEstado().name())
                                .estado(estado)
                                .build();
        }

        private String getEstadoSalud(Integer valor, Float mean, Float std) {
                if ((mean - SIGMA_AMARILLO_MOVIMIENTO * std) < valor && valor < (mean + SIGMA_AMARILLO_MOVIMIENTO * std)) {
                        return "VERDE";
                } else if ((mean - SIGMA_ROJO_MOVIMIENTO * std) < valor && valor < (mean + SIGMA_ROJO_MOVIMIENTO * std)) {
                        return "AMARILLO";
                } else {
                        return "ROJO";
                }
        }

        private static final List<TipoReproductivo> ESTADOS_REPRODUCTIVOS_OK = Arrays.asList(
                        TipoReproductivo.GESTANTE, TipoReproductivo.PARIDA, TipoReproductivo.VACIA,
                        TipoReproductivo.INSEMINADA);

        private String getEstadoReproductivo(EstadoReproductivo estadoRep) {
                if (ESTADOS_REPRODUCTIVOS_OK.contains(estadoRep.getEstado())) {
                        return "VERDE";
                } else if (estadoRep.getEstado().equals(TipoReproductivo.POSIBLE_CELO)) {
                        return "AMARILLO";
                } else {
                        return "ROJO";
                }
        }

        private String descripcionMovimiento(String estado) {
                switch (estado) {
                        case "VERDE":
                                return "Actividad dentro del rango normal para este animal.";
                        case "AMARILLO":
                                return "Actividad fuera del rango normal, dentro de un margen leve.";
                        case "ROJO":
                                return "Actividad muy alejada del rango normal para este animal.";
                        default:
                                return "No hay datos de telemetría o perfil de referencia suficientes para calcular este indicador.";
                }
        }

        private String descripcionReproductivo(String estado) {
                switch (estado) {
                        case "VERDE":
                                return "Estado reproductivo dentro de lo esperado.";
                        case "AMARILLO":
                                return "Estado reproductivo a seguir de cerca (posible celo).";
                        case "ROJO":
                                return "Estado reproductivo que requiere atención.";
                        default:
                                return "No hay registros de estado reproductivo cargados para este animal.";
                }
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public AnimalResponse vincularCaravana(Long animalId, Long caravanaId, String emailUsuario) {
                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                validarPermisoGestionCaravana(usuario, animal.getEstablecimiento());

                if (animal.getEstadoActual().equals(EstadoAnimal.INACTIVO)) {
                        throw new IllegalArgumentException("No se puede vincular una caravana a un animal inactivo.");
                }

                asignarCaravanaInterna(animal, caravanaId);
                Animal savedAnimal = animalRepository.save(animal);
                return mapToResponse(savedAnimal);
        }

        @Override
        @Transactional
        @SuppressWarnings("null")
        public AnimalResponse desvincularCaravana(Long animalId, String emailUsuario) {
                Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Animal animal = animalRepository.findById(animalId)
                                .orElseThrow(() -> new IllegalArgumentException("Animal no encontrado."));

                validarPermisoGestionCaravana(usuario, animal.getEstablecimiento());

                if (animal.getCaravana() != null) {
                        Caravana caravana = animal.getCaravana();
                        caravana.setEstadoSensor(EstadoSensor.DISPONIBLE);
                        caravana.setActivo(false);
                        caravanaRepository.save(caravana);

                        animal.setCaravana(null);
                        animal = animalRepository.save(animal);
                }

                return mapToResponse(animal);
        }

        private void asignarCaravanaInterna(Animal animal, Long caravanaId) {
                Caravana nuevaCaravana = caravanaRepository.findById(caravanaId)
                                .orElseThrow(() -> new IllegalArgumentException("Caravana no encontrada."));

                if (nuevaCaravana.getEstablecimiento() == null ||
                                !nuevaCaravana.getEstablecimiento().getId().equals(animal.getEstablecimiento().getId())) {
                        throw new IllegalArgumentException("La caravana no pertenece al mismo establecimiento que el animal.");
                }

                if (nuevaCaravana.getEstadoSensor() != null && nuevaCaravana.getEstadoSensor().equals(EstadoSensor.INACTIVO)) {
                        throw new IllegalArgumentException("La caravana se encuentra inactiva y no puede ser vinculada.");
                }

                Optional<Animal> animalVinculado = animalRepository.findByCaravanaId(nuevaCaravana.getId());
                if (animalVinculado.isPresent() && !animalVinculado.get().getId().equals(animal.getId())) {
                        Animal otroAnimal = animalVinculado.get();
                        throw new IllegalArgumentException("La caravana ya se encuentra vinculada a otro animal ("
                                        + (otroAnimal.getAlias() != null ? otroAnimal.getAlias()
                                                        : otroAnimal.getIdCaravanaOficial())
                                        + ").");
                }

                if (animal.getCaravana() != null && !animal.getCaravana().getId().equals(nuevaCaravana.getId())) {
                        Caravana caravanaAnterior = animal.getCaravana();
                        caravanaAnterior.setEstadoSensor(EstadoSensor.DISPONIBLE);
                        caravanaAnterior.setActivo(false);
                        caravanaRepository.save(caravanaAnterior);
                }

                animal.setCaravana(nuevaCaravana);
                nuevaCaravana.setEstadoSensor(EstadoSensor.VINCULADO);
                nuevaCaravana.setActivo(true);
                nuevaCaravana.setFechaAsignacion(LocalDate.now());

                caravanaRepository.save(nuevaCaravana);
        }

        private void validarPermisoGestionCaravana(Usuario usuario, Establecimiento establecimiento) {
                if (usuario.getRol().equals(Rol.VETERINARIO)) {
                        throw new AccessDeniedException("El rol VETERINARIO no tiene permisos para asignar o desvincular caravanas.");
                }

                boolean tieneAcceso = usuario.getRol().equals(Rol.ADMINISTRADOR) ||
                                (establecimiento != null && establecimiento.getEmpleados().stream()
                                                .anyMatch(v -> v.getUsuario().equals(usuario) && v.isActivo()));

                if (!tieneAcceso) {
                        throw new AccessDeniedException("No tiene permisos sobre este establecimiento.");
                }
        }
}
