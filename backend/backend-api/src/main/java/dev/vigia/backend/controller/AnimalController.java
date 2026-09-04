package dev.vigia.backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
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

import dev.vigia.backend.dto.AnimalMapaResponse;
import dev.vigia.backend.dto.AnimalMetricaResponse;
import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.AnimalResponse;
import dev.vigia.backend.dto.CargaMasivaEstadoResponse;
import dev.vigia.backend.dto.CargaMasivaResponse;
import dev.vigia.backend.dto.EstadoReproductivoRequest;
import dev.vigia.backend.dto.EstadoReproductivoResponse;
import dev.vigia.backend.dto.EstadoSaludRequest;
import dev.vigia.backend.dto.EstadoSaludResponse;
import dev.vigia.backend.dto.IntervencionResponse;
import dev.vigia.backend.service.AnimalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/animales")
@RequiredArgsConstructor
public class AnimalController {

    private final AnimalService animalService;
    private final dev.vigia.backend.service.SseTelemetriaPublisher sseTelemetriaPublisher;

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

    @GetMapping(value = "/mapa/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamPosicionesMapa(
            @RequestParam(name = "establecimientoId") Long establecimientoId) {
        // En una app real, aquí también se debería verificar que el usuario actual
        // (authentication)
        // tiene acceso a ese establecimiento. Se asume validado por simplicidad en esta
        // demo.
        return sseTelemetriaPublisher.registrarClienteSse(establecimientoId);
    }

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
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

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
}
