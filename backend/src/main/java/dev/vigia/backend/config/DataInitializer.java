package dev.vigia.backend.config;

import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.SeveridadAlerta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
    private final PasswordEncoder passwordEncoder;

    @Override
    @SuppressWarnings("null")
    public void run(String... args) {
        log.info("[DataInitializer] Limpiando la base de datos para entorno de desarrollo...");
        alertaRepository.deleteAll();
        estadoReproductivoRepository.deleteAll();
        invitacionRepository.deleteAll();
        animalRepository.deleteAll();
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
        est.setLatitud(-34.6037);
        est.setLongitud(-58.3816);
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
        animalRepository.save(vaca1);

        Animal vaca2 = new Animal(est, lote1);
        vaca2.setIdCaravanaOficial("67890");
        vaca2.setAlias("Margarita");
        vaca2.setEstadoActual(EstadoAnimal.ACTIVO);
        vaca2.setFechaNacimiento(LocalDate.of(2021, 5, 10));
        animalRepository.save(vaca2);

        Alerta alerta1 = new Alerta();
        alerta1.setAnimal(vaca1);
        alerta1.setTipo("CELO");
        alerta1.setSeveridad(SeveridadAlerta.ALTA);
        alerta1.setTsGeneracion(java.time.LocalDateTime.now().minusMinutes(2));
        alerta1.setDisparador(DisparadorAlerta.CELO);
        alerta1.setValorUmbral(0.8);
        alerta1.setValorDetectado(0.85);
        alerta1.setEstado(EstadoAlerta.PENDIENTE);
        alertaRepository.save(alerta1);

        Alerta alerta2 = new Alerta();
        alerta2.setAnimal(vaca2);
        alerta2.setTipo("CELO");
        alerta2.setSeveridad(SeveridadAlerta.MEDIA);
        alerta2.setTsGeneracion(java.time.LocalDateTime.now().minusHours(1));
        alerta2.setDisparador(DisparadorAlerta.CELO);
        alerta2.setValorUmbral(0.8);
        alerta2.setValorDetectado(0.9);
        alerta2.setEstado(EstadoAlerta.CONFIRMADA);
        alertaRepository.save(alerta2);

        log.info("[DataInitializer] ✅ Animales y alertas de prueba generados.");
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
