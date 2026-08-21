package dev.vigia.backend.config;

import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoInferencia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final InvitacionRepository invitacionRepository;
    private final AnimalRepository animalRepository;
    private final RodeoLoteRepository rodeoLoteRepository;
    private final EstadoReproductivoRepository estadoReproductivoRepository;
    private final AlertaRepository alertaRepository;
    private final IntervencionRepository intervencionRepository;
    private final CaravanaRepository caravanaRepository;
    private final TelemetriaRepository telemetriaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @SuppressWarnings("null")
    public void run(String... args) {
        log.info("[DataInitializer] Limpiando la base de datos para entorno de desarrollo...");
        telemetriaRepository.deleteAll();
        intervencionRepository.deleteAll();
        alertaRepository.deleteAll();
        estadoReproductivoRepository.deleteAll();
        invitacionRepository.deleteAll();
        animalRepository.deleteAll();
        caravanaRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario admin = buildUsuario("Admin", "VigIA", "admin@vigia.dev", "admin123", Rol.ADMINISTRADOR);
        Usuario productor = buildUsuario("Juan", "Productor", "productor@vigia.dev", "productor123", Rol.PRODUCTOR);
        Usuario campero = buildUsuario("Carlos", "Campero", "campero@vigia.dev", "campero123", Rol.CAMPERO);
        Usuario vet = buildUsuario("Laura", "Vet", "veterinario@vigia.dev", "vet123", Rol.VETERINARIO);
        Usuario pendiente = buildUsuario("Nuevo", "Usuario", "pendiente@vigia.dev", "pendiente123", Rol.PENDIENTE);

        List<Usuario> usuarios = List.of(admin, productor, campero, vet, pendiente);
        usuarioRepository.saveAll(usuarios);

        Establecimiento est = new Establecimiento();
        est.setNombre("Establecimiento de Prueba");
        est.setLatitud(-34.943317);
        est.setLongitud(-59.225728);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());

        Empleado vinculo = new Empleado(est, productor);
        est.getEmpleados().add(vinculo);
        establecimientoRepository.save(est);

        RodeoLote lote1 = new RodeoLote(est);
        lote1.setNombre("Rodeo 01");
        lote1.setActivo(true);
        rodeoLoteRepository.save(lote1);

        RodeoLote lote2 = new RodeoLote(est);
        lote2.setNombre("Rodeo 02");
        lote2.setActivo(true);
        rodeoLoteRepository.save(lote2);

        log.info("[DataInitializer] ✅ Seed completado — {} usuarios creados.", usuarios.size());
        log.info("[DataInitializer] ✅ Establecimiento 'Establecimiento de Prueba' creado y asignado al Productor.");
        log.info("[DataInitializer] ✅ Rodeos de prueba creados: 'Rodeo 01' y 'Rodeo 02'.");

        Animal vaca1 = new Animal(est, lote1);
        vaca1.setIdCaravanaOficial("12345");
        vaca1.setAlias("La Vaca Lola");
        vaca1.setEstadoActual(EstadoAnimal.ACTIVO);
        vaca1.setFechaNacimiento(LocalDate.of(2020, 1, 1));
        Caravana caravana1 = new Caravana();
        caravana1.setCodigo("VIGIA-ARG-001");
        caravana1.setActivo(true);
        caravanaRepository.save(caravana1);

        vaca1.setCaravana(caravana1);
        animalRepository.save(vaca1);

        Animal vaca2 = new Animal(est, lote1);
        vaca2.setIdCaravanaOficial("67890");
        vaca2.setAlias("Margarita");
        vaca2.setEstadoActual(EstadoAnimal.ACTIVO);
        vaca2.setFechaNacimiento(LocalDate.of(2021, 5, 10));
        vaca2.setFechaAlta(LocalDate.now().minusDays(7));

        Caravana caravana2 = new Caravana();
        caravana2.setCodigo("VIGIA-ARG-002");
        caravana2.setActivo(true);
        caravanaRepository.save(caravana2);

        vaca2.setCaravana(caravana2);
        animalRepository.save(vaca2);

        Animal vaca3 = new Animal(est, lote2);
        vaca3.setIdCaravanaOficial("33333");
        vaca3.setAlias("Sin Datos Recientes");
        vaca3.setEstadoActual(EstadoAnimal.ACTIVO);
        vaca3.setFechaNacimiento(LocalDate.of(2022, 3, 15));

        Caravana caravana3 = new Caravana();
        caravana3.setCodigo("VIGIA-ARG-003");
        caravana3.setActivo(true);
        caravanaRepository.save(caravana3);

        vaca3.setCaravana(caravana3);
        animalRepository.save(vaca3);

        Animal vaca4 = new Animal(est, lote2);
        vaca4.setIdCaravanaOficial("44444");
        vaca4.setAlias("Blanca");
        vaca4.setEstadoActual(EstadoAnimal.ACTIVO);
        vaca4.setFechaNacimiento(LocalDate.of(2021, 8, 20));

        Caravana caravana4 = new Caravana();
        caravana4.setCodigo("VIGIA-ARG-004");
        caravana4.setActivo(true);
        caravanaRepository.save(caravana4);

        vaca4.setCaravana(caravana4);
        animalRepository.save(vaca4);

        Animal vaca5 = new Animal(est, lote1);
        vaca5.setIdCaravanaOficial("55555");
        vaca5.setAlias("Pinta");
        vaca5.setEstadoActual(EstadoAnimal.ACTIVO);
        vaca5.setFechaNacimiento(LocalDate.of(2022, 1, 10));

        Caravana caravana5 = new Caravana();
        caravana5.setCodigo("VIGIA-ARG-005");
        caravana5.setActivo(true);
        caravanaRepository.save(caravana5);

        vaca5.setCaravana(caravana5);
        animalRepository.save(vaca5);

        Telemetria telemetriaStale = new Telemetria();
        telemetriaStale.setCaravana(caravana3);
        telemetriaStale.setFechaHora(LocalDateTime.now().minusHours(1));
        telemetriaStale.setLatitud(-34.943317);
        telemetriaStale.setLongitud(-59.225728);
        telemetriaStale.setBateria(45.0);
        telemetriaStale.setNivelRumia(50.0);
        telemetriaStale.setMoveState(1);
        telemetriaStale.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO);
        telemetriaStale.setEstadoInferenciaSalud(EstadoInferencia.COMPLETADO);
        telemetriaRepository.save(telemetriaStale);

        log.info("[DataInitializer] ✅ Animales de prueba generados.");
        log.info("[DataInitializer] Usuarios disponibles para pruebas:");
        log.info("  admin@vigia.dev        / admin123      → ADMINISTRADOR");
        log.info("  productor@vigia.dev    / productor123  → PRODUCTOR");
        log.info("  campero@vigia.dev      / campero123    → CAMPERO");
        log.info("  veterinario@vigia.dev  / vet123        → VETERINARIO");
        log.info("  pendiente@vigia.dev    / pendiente123  → PENDIENTE");
    }

    private Usuario buildUsuario(String nombre, String apellido, String email, String password, Rol rol) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setApellido(apellido);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRol(rol);
        u.setActivo(true);
        u.setFechaAlta(LocalDate.now());
        return u;
    }
}
