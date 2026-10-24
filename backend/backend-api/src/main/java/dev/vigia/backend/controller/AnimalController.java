package dev.vigia.backend.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.vigia.backend.dto.AnimalIndicadoresSaludResponse;
import dev.vigia.backend.dto.AnimalMapaResponse;
import dev.vigia.backend.dto.AnimalMetricaResponse;
import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.AnimalResponse;
import dev.vigia.backend.dto.VincularCaravanaRequest;
import dev.vigia.backend.dto.CargaMasivaEstadoResponse;
import dev.vigia.backend.dto.CargaMasivaResponse;
import dev.vigia.backend.dto.CohesionResponse;
import dev.vigia.backend.dto.EstadoReproductivoRequest;
import dev.vigia.backend.dto.EstadoReproductivoResponse;
import dev.vigia.backend.dto.EstadoSaludRequest;
import dev.vigia.backend.dto.EstadoSaludResponse;
import dev.vigia.backend.dto.IntervencionRequest;
import dev.vigia.backend.dto.IntervencionResponse;
import dev.vigia.backend.dto.LineaTiempoEventoResponse;
import dev.vigia.backend.dto.RegistroPesoRequest;
import dev.vigia.backend.dto.RegistroPesoResponse;
import dev.vigia.backend.service.AlertaService;
import dev.vigia.backend.service.AnimalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/animales")
@RequiredArgsConstructor
public class AnimalController {

    private final AnimalService animalService;
    private final AlertaService alertaService;
    private final dev.vigia.backend.service.SseTelemetriaPublisher sseTelemetriaPublisher;

    /**
     * GET /api/v1/animales?establecimientoId={id}
     * <p>
     * Lista los animales del establecimiento indicado (o de todos los
     * establecimientos del usuario si no se especifica).
     *
     * @param establecimientoId (opcional) ID del establecimiento a filtrar
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de animales, 400 si hay error, 403 si acceso
     *         denegado
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> listarAnimales(
            @RequestParam(name = "establecimientoId", required = false) Long establecimientoId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            List<AnimalResponse> response = animalService.listarAnimales(email, establecimientoId);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/animales/mapa?establecimientoId={id}
     * <p>
     * Devuelve la última posición conocida de cada animal del establecimiento,
     * para pintarlos en el mapa.
     *
     * @param establecimientoId (opcional) ID del establecimiento a filtrar
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 200 con las posiciones de los animales, 400 si hay error, 403 si
     *         acceso denegado
     */
    @GetMapping("/mapa")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> getPosicionesMapa(
            @RequestParam(name = "establecimientoId", required = false) Long establecimientoId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            List<AnimalMapaResponse> response = animalService.getPosicionesMapa(email, establecimientoId);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/animales/mapa/stream?establecimientoId={id}
     * <p>
     * Abre un stream Server-Sent Events (SSE) que publica en tiempo real las
     * actualizaciones de posición de los animales del establecimiento indicado.
     *
     * @param establecimientoId ID del establecimiento a suscribir
     * @return emisor SSE registrado para el establecimiento indicado
     */
    @GetMapping(value = "/mapa/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamPosicionesMapa(
            @RequestParam(name = "establecimientoId") Long establecimientoId) {
        // En una app real, aquí también se debería verificar que el usuario actual
        // (authentication)
        // tiene acceso a ese establecimiento. Se asume validado por simplicidad en esta
        // demo.
        return sseTelemetriaPublisher.registrarClienteSse(establecimientoId);
    }

    /**
     * GET /api/v1/animales/{id}
     * <p>
     * Obtiene el detalle de un animal por su ID.
     *
     * @param animalId       ID del animal
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con el detalle del animal, 400 si el animal no existe o no
     *         pertenece al usuario
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<AnimalResponse> getAnimalById(
            @PathVariable("id") Long animalId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            AnimalResponse response = animalService.getAnimalById(animalId, emailProductor);
            return ResponseEntity.ok().body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * POST /api/v1/animales
     * <p>
     * Registra un nuevo animal en el establecimiento del productor autenticado.
     *
     * @param request        datos del animal a crear
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 201 con el animal creado, 400 si hay error de validación
     */
    @PostMapping
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> cargarAnimal(
            @Valid @RequestBody AnimalRequest request,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            AnimalResponse response = animalService.cargarAnimal(emailProductor, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * DELETE /api/v1/animales/{id}
     * <p>
     * Desactiva (baja lógica) un animal.
     *
     * @param animalId       ID del animal a desactivar
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 si se desactivó correctamente, 400 si hay error
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> desactivarAnimal(
            @PathVariable("id") Long animalId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            animalService.desactivarAnimal(animalId, emailProductor);
            return ResponseEntity.ok().body("Animal desactivado exitosamente");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PUT /api/v1/animales/{id}
     * <p>
     * Edita los datos de un animal existente.
     *
     * @param animalId       ID del animal a editar
     * @param request        nuevos datos del animal
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con el animal actualizado, 400 si hay error
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> editarAnimal(
            @PathVariable("id") Long animalId,
            @Valid @RequestBody AnimalRequest request,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            AnimalResponse response = animalService.editarAnimal(animalId, emailProductor, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/animales/{id}/estado-reproductivo
     * <p>
     * Lista el historial de estados reproductivos registrados para un animal.
     *
     * @param animalId       ID del animal
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de estados reproductivos, 400 si hay error, 403 si
     *         acceso denegado
     */
    @GetMapping("/{id}/estado-reproductivo")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> listarEstadosReproductivos(
            @PathVariable("id") Long animalId,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            List<dev.vigia.backend.dto.EstadoReproductivoResponse> response = animalService
                    .listarEstadosReproductivos(animalId, email);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/animales/{id}/estado-reproductivo
     * <p>
     * Registra un nuevo estado reproductivo para un animal.
     *
     * @param animalId       ID del animal
     * @param request        datos del estado reproductivo a registrar
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 201 con el estado reproductivo creado, 400 si hay error
     */
    @PostMapping("/{id}/estado-reproductivo")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO')")
    public ResponseEntity<?> registrarEstadoReproductivo(
            @PathVariable("id") Long animalId,
            @Valid @RequestBody EstadoReproductivoRequest request,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            EstadoReproductivoResponse response = animalService.registrarEstadoReproductivo(animalId, emailProductor,
                    request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PUT /api/v1/animales/{id}/estado-reproductivo/{estadoId}
     * <p>
     * Edita un estado reproductivo existente de un animal.
     *
     * @param animalId       ID del animal
     * @param estadoId       ID del estado reproductivo a editar
     * @param request        nuevos datos del estado reproductivo
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con el estado reproductivo actualizado, 400 si hay error
     */
    @PutMapping("/{id}/estado-reproductivo/{estadoId}")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO')")
    public ResponseEntity<?> editarEstadoReproductivo(
            @PathVariable("id") Long animalId,
            @PathVariable("estadoId") Long estadoId,
            @Valid @RequestBody dev.vigia.backend.dto.EstadoReproductivoRequest request,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            dev.vigia.backend.dto.EstadoReproductivoResponse response = animalService.editarEstadoReproductivo(animalId,
                    estadoId, emailProductor, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/animales/{id}/intervenciones
     * <p>
     * Lista las intervenciones (tratamientos, vacunaciones, revisiones,
     * inseminaciones, etc.) registradas para un animal.
     *
     * @param animalId       ID del animal
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de intervenciones, 400 si hay error
     */
    @GetMapping("/{id}/intervenciones")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> listarIntervenciones(
            @PathVariable("id") Long animalId,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            List<IntervencionResponse> response = animalService.listarIntervenciones(animalId, email);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/animales/{id}/intervenciones
     * <p>
     * Registra una intervención (tratamiento, vacunación, revisión veterinaria u
     * otra) sobre un animal, sin alerta asociada.
     *
     * @param animalId       ID del animal
     * @param request        tipo, fecha/hora (opcional) y descripción
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 201 con la intervención creada, 400 si hay error, 403 si acceso
     *         denegado, 404 si el animal no existe
     */
    @PostMapping("/{id}/intervenciones")
    @PreAuthorize("hasRole('VETERINARIO')")
    public ResponseEntity<?> registrarIntervencion(
            @PathVariable("id") Long animalId,
            @RequestBody IntervencionRequest request,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            IntervencionResponse response = animalService.registrarIntervencion(animalId, email, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Animal no encontrado.".equals(e.getMessage())) {
                return ResponseEntity.status(404).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/animales/csv?establecimientoId={id}
     * <p>
     * Carga masiva de animales a partir de un archivo (CSV/Excel).
     *
     * @param file              archivo con los animales a cargar
     * @param establecimientoId ID del establecimiento destino
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 201 con el resumen de la carga, 400 si hay error de validación o de
     *         lectura del archivo
     */
    @PostMapping("/csv")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> cargarAnimalesExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("establecimientoId") Long establecimientoId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            CargaMasivaResponse response = animalService.cargarAnimalesExcel(file, establecimientoId, emailProductor);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/animales/estado-reproductivo/csv?establecimientoId={id}
     * <p>
     * Carga masiva de estados reproductivos a partir de un archivo (CSV/Excel).
     *
     * @param file              archivo con los estados reproductivos a cargar
     * @param establecimientoId ID del establecimiento destino
     * @param authentication    contexto de seguridad inyectado por Spring Security
     * @return 201 con el resumen de la carga, 400 si hay error de validación o de
     *         lectura del archivo
     */
    @PostMapping("/estado-reproductivo/csv")
    @PreAuthorize("hasRole('PRODUCTOR')")
    public ResponseEntity<?> cargarEstadosMasivo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("establecimientoId") Long establecimientoId,
            Authentication authentication) {

        try {
            String emailProductor = authentication.getName();
            CargaMasivaEstadoResponse response = animalService.cargarEstadosMasivo(file, establecimientoId,
                    emailProductor);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/animales/{id}/metricas
     * <p>
     * Devuelve las métricas calculadas para un animal (actividad, desplazamiento,
     * etc.).
     *
     * @param animalId       ID del animal
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de métricas, 400 si hay error, 403 si acceso
     *         denegado
     */
    @GetMapping("/{id}/metricas")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO')")
    public ResponseEntity<?> getMetricasAnimal(
            @PathVariable("id") Long animalId,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            List<AnimalMetricaResponse> response = animalService.getMetricasAnimal(animalId, email);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/animales/{id}/estado-salud
     * <p>
     * Registra un nuevo estado de salud (SANO, EN_OBSERVACION, ENFERMO) para un
     * animal.
     *
     * @param animalId       ID del animal
     * @param request        datos del estado de salud a registrar
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 201 con el estado de salud creado, 400 si hay error, 403 si acceso
     *         denegado
     */
    @PostMapping("/{id}/estado-salud")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO')")
    public ResponseEntity<?> registrarEstadoSalud(
            @PathVariable("id") Long animalId,
            @Valid @RequestBody EstadoSaludRequest request,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            EstadoSaludResponse response = animalService.registrarEstadoSalud(animalId, email, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * GET /api/v1/animales/{id}/estado-salud
     * <p>
     * Lista el historial de estados de salud registrados para un animal.
     *
     * @param animalId       ID del animal
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de estados de salud, 400 si hay error, 403 si
     *         acceso denegado
     */
    @GetMapping("/{id}/estado-salud")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> listarEstadosSalud(
            @PathVariable("id") Long animalId,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            List<EstadoSaludResponse> response = animalService.listarEstadosSalud(animalId, email);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Historial de pesos ──────────────────────────────────────────────────────

    /**
     * GET /api/v1/animales/{id}/historial-pesos
     * <p>
     * Lista el historial de pesos registrados para un animal.
     *
     * @param animalId       ID del animal
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con la lista de registros de peso, 400 si hay error, 403 si
     *         acceso denegado
     */
    @GetMapping("/{id}/historial-pesos")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> listarHistorialPesos(
            @PathVariable("id") Long animalId,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            List<RegistroPesoResponse> response = animalService.listarHistorialPesos(animalId, email);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/animales/{id}/historial-pesos
     * <p>
     * Registra un nuevo peso para un animal.
     *
     * @param animalId       ID del animal
     * @param request        datos del registro de peso
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 201 con el registro de peso creado, 400 si hay error, 403 si acceso
     *         denegado
     */
    @PostMapping("/{id}/historial-pesos")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> registrarPeso(
            @PathVariable("id") Long animalId,
            @Valid @RequestBody RegistroPesoRequest request,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            RegistroPesoResponse response = animalService.registrarPeso(animalId, email, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PUT /api/v1/animales/{id}/historial-pesos/{pesoId}
     * <p>
     * Edita un registro de peso existente de un animal.
     *
     * @param animalId       ID del animal
     * @param pesoId         ID del registro de peso a editar
     * @param request        nuevos datos del registro de peso
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con el registro de peso actualizado, 400 si hay error, 403 si
     *         acceso denegado
     */
    @PutMapping("/{id}/historial-pesos/{pesoId}")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> editarPeso(
            @PathVariable("id") Long animalId,
            @PathVariable("pesoId") Long pesoId,
            @Valid @RequestBody RegistroPesoRequest request,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            RegistroPesoResponse response = animalService.editarPeso(animalId, pesoId, email, request);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * DELETE /api/v1/animales/{id}/historial-pesos/{pesoId}
     * <p>
     * Elimina un registro de peso de un animal.
     *
     * @param animalId       ID del animal
     * @param pesoId         ID del registro de peso a eliminar
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 si se eliminó correctamente, 400 si hay error, 403 si acceso
     *         denegado
     */
    @DeleteMapping("/{id}/historial-pesos/{pesoId}")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> eliminarPeso(
            @PathVariable("id") Long animalId,
            @PathVariable("pesoId") Long pesoId,
            Authentication authentication) {

        try {
            String email = authentication.getName();
            animalService.eliminarPeso(animalId, pesoId, email);
            return ResponseEntity.ok("Registro de peso eliminado exitosamente.");
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Indicadores de salud ────────────────────────────────────────────────────

    /**
     * GET /api/v1/animales/{id}/indicadores-salud
     * <p>
     * Devuelve los indicadores de salud calculados para un animal (movimiento,
     * reproductivo y estado de salud registrado por el veterinario).
     *
     * @param animalId       ID del animal
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con los indicadores de salud, 400 si hay error, 403 si acceso
     *         denegado
     */
    @GetMapping("/{id}/indicadores-salud")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('VETERINARIO')")
    public ResponseEntity<?> getIndicadoresSalud(
            @PathVariable("id") Long animalId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            AnimalIndicadoresSaludResponse response = animalService.getIndicadoresSalud(animalId, email);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ── Línea de tiempo de alertas
    // ────────────────────────────────────────────────────
    /**
     * GET
     * /api/v1/animales/{id}/linea-tiempo-alertas?tipo={tipo}&desde={desde}&hasta={hasta}
     * <p>
     * Devuelve la línea de tiempo unificada de alertas e intervenciones de un
     * animal, ordenada cronológicamente.
     *
     * @param animalId       ID del animal
     * @param tipo           (opcional) filtro por tipo de alerta, con los mismos
     *                       valores que GET /alertas (no filtra las intervenciones);
     *                       un valor desconocido responde 400
     * @param desde          (opcional) inicio del rango de fechas, inclusive
     * @param hasta          (opcional) fin del rango de fechas, inclusive
     * @param authentication contexto de seguridad inyectado por Spring Security
     * @return 200 con la línea de tiempo de eventos, 400 si hay error, 403 si
     *         acceso denegado
     */
    @GetMapping("/{id}/linea-tiempo-alertas")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> getLineaTiempoAlertas(
            @PathVariable("id") Long animalId,
            @RequestParam(name = "tipo", required = false) String tipo,
            @RequestParam(name = "desde", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime desde,
            @RequestParam(name = "hasta", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime hasta,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            List<LineaTiempoEventoResponse> response = alertaService.obtenerLineaTiempoAlertas(email, animalId, tipo,
                    desde, hasta);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/vincular-caravana")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('CAMPERO')")
    public ResponseEntity<?> vincularCaravana(
            @PathVariable("id") Long animalId,
            @Valid @RequestBody VincularCaravanaRequest request,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            AnimalResponse response = animalService.vincularCaravana(animalId, request.getCaravanaId(), email);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Animal no encontrado.".equals(e.getMessage()) || "Caravana no encontrada.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}/desvincular-caravana")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('CAMPERO')")
    public ResponseEntity<?> desvincularCaravana(
            @PathVariable("id") Long animalId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            AnimalResponse response = animalService.desvincularCaravana(animalId, email);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Animal no encontrado.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/animales/asignar-caravanas?establecimientoId={id}[&rodeoLoteId={id}]
     * <p>
     * Asigna al azar las caravanas disponibles del establecimiento a los animales
     * activos que no tienen caravana. Con rodeoLoteId, solo a los de ese rodeo/lote.
     *
     * @return 200 con los animales a los que se les asignó caravana (vacío si no
     *         había nada para asignar), 404 si el establecimiento o el rodeo no
     *         existen, 400 si el rodeo es de otro establecimiento, 403 si no tiene
     *         permisos
     */
    @PostMapping("/asignar-caravanas")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('CAMPERO')")
    public ResponseEntity<?> asignarCaravanasAleatoriamente(
            @RequestParam("establecimientoId") Long establecimientoId,
            @RequestParam(value = "rodeoLoteId", required = false) Long rodeoLoteId,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            List<AnimalResponse> response = animalService.asignarCaravanasAleatoriamente(establecimientoId,
                    rodeoLoteId, email);
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Establecimiento no encontrado.".equals(e.getMessage())
                    || "Rodeo lote no encontrado.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /api/v1/animales/asignaciones-caravanas/excel?establecimientoId={id}
     * <p>
     * Planilla .xlsx animal → sensor para colocar las caravanas en el campo. El body
     * es la lista de ids de animales a incluir (por ejemplo, los que devolvió
     * asignar-caravanas); vacío o ausente, exporta todos los que tienen caravana.
     * Es POST porque la lista puede ser más larga de lo que entra en una URL.
     *
     * @return 200 con el archivo, 404 si el establecimiento no existe, 403 si no
     *         tiene permisos
     */
    @PostMapping("/asignaciones-caravanas/excel")
    @PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('PRODUCTOR') or hasRole('CAMPERO')")
    public ResponseEntity<?> exportarAsignacionesCaravanas(
            @RequestParam("establecimientoId") Long establecimientoId,
            @RequestBody(required = false) List<Long> animalIds,
            Authentication authentication) {
        try {
            byte[] excel = animalService.exportarAsignacionesCaravanas(establecimientoId, animalIds,
                    authentication.getName());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("asignacion-caravanas-" + LocalDate.now() + ".xlsx").build().toString())
                    .body(excel);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Establecimiento no encontrado.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/cohesion")
    @PreAuthorize("hasRole('PRODUCTOR') or hasRole('VETERINARIO') or hasRole('CAMPERO')")
    public ResponseEntity<?> getCohesion(
            @PathVariable("id") Long animalId,
            @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            CohesionResponse response = animalService.obtenerCohesion(email, animalId, aHoraLocal(desde),
                    aHoraLocal(hasta));
            return ResponseEntity.ok(response);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            if ("Animal no encontrado.".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Los registros se guardan en la hora local del servidor: el instante recibido se lleva a esa zona. */
    private static LocalDateTime aHoraLocal(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
