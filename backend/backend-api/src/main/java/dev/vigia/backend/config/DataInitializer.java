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
import dev.vigia.backend.repository.EstadoSaludRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.EstadoSalud;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.model.Intervencion;
import dev.vigia.backend.model.OrigenEstado;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.model.TipoIntervencion;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.util.SerieComparativaBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
    private final EstadoSaludRepository estadoSaludRepository;
    private final AlertaRepository alertaRepository;
    private final IntervencionRepository intervencionRepository;
    private final CaravanaRepository caravanaRepository;
    private final TelemetriaRepository telemetriaRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    @SuppressWarnings("null")
    public void run(String... args) {
        log.info("[DataInitializer] Limpiando la base de datos para entorno de desarrollo...");
        try {
            jdbcTemplate.execute("SET vigia.bypass_immutable_triggers = 'true'");
        } catch (Exception ignored) {
        }
        telemetriaRepository.deleteAll();
        intervencionRepository.deleteAll();
        alertaRepository.deleteAll();
        estadoReproductivoRepository.deleteAll();
        estadoSaludRepository.deleteAll();
        invitacionRepository.deleteAll();
        animalRepository.deleteAll();
        caravanaRepository.deleteAll();
        rodeoLoteRepository.deleteAll();
        establecimientoRepository.deleteAll();
        usuarioRepository.deleteAll();

        // ── Acto 1: Usuarios y roles ──────────────────────────────────────
        Usuario admin = buildUsuario("Admin", "VigIA", "admin@vigia.dev", "admin123", Rol.ADMINISTRADOR);
        Usuario productor = buildUsuario("Juan", "Productor", "productor@vigia.dev", "productor123", Rol.PRODUCTOR);
        Usuario campero = buildUsuario("Carlos", "Campero", "campero@vigia.dev", "campero123", Rol.CAMPERO);
        Usuario vet = buildUsuario("Laura", "Vet", "veterinario@vigia.dev", "vet123", Rol.VETERINARIO);
        Usuario pendiente = buildUsuario("Nuevo", "Usuario", "pendiente@vigia.dev", "pendiente123", Rol.PENDIENTE);

        List<Usuario> usuarios = List.of(admin, productor, campero, vet, pendiente);
        usuarioRepository.saveAll(usuarios);

        Establecimiento est = new Establecimiento();
        est.setNombre("Estancia La Aurora");
        est.setLatitud(-34.943317);
        est.setLongitud(-59.225728);
        est.setActivo(true);
        est.setFechaAlta(LocalDate.now());
        establecimientoRepository.save(est);

        // Vinculamos a los TRES roles operativos con el establecimiento
        // (antes solo se vinculaba al productor — campero y vet quedaban sin acceso al rodeo).
        // ADMINISTRADOR no se vincula: es un rol global sin Empleado asociado.
        Empleado vinculoProductor = new Empleado(est, productor);
        Empleado vinculoCampero = new Empleado(est, campero);
        Empleado vinculoVet = new Empleado(est, vet);
        est.getEmpleados().add(vinculoProductor);
        est.getEmpleados().add(vinculoCampero);
        est.getEmpleados().add(vinculoVet);
        establecimientoRepository.save(est);

        // ── Acto 2: Rodeo ──────────────────────────────────────────────────
        RodeoLote lote1 = new RodeoLote(est);
        lote1.setNombre("Rodeo 01");
        lote1.setActivo(true);
        rodeoLoteRepository.save(lote1);

        // Rodeo 02 intencionalmente vacío → se usa en la demo de H0005 (carga por CSV)
        RodeoLote lote2 = new RodeoLote(est);
        lote2.setNombre("Rodeo 02");
        lote2.setActivo(true);
        rodeoLoteRepository.save(lote2);

        log.info("[DataInitializer] ✅ {} usuarios creados.", usuarios.size());
        log.info("[DataInitializer] ✅ Establecimiento 'Estancia La Aurora' creado con Productor, Campero y Veterinario vinculados.");
        log.info("[DataInitializer] ✅ Rodeos de prueba creados: 'Rodeo 01' y 'Rodeo 02'.");

        Animal vaca1 = crearAnimal(est, lote1, "12345", "La Vaca Lola", "Angus",
                LocalDate.of(2020, 1, 1), "VIGIA-ARG-001", caravanaRepository, animalRepository);

        Animal vaca2 = crearAnimal(est, lote1, "67890", "Margarita", "Hereford",
                LocalDate.of(2021, 5, 10), "VIGIA-ARG-002", caravanaRepository, animalRepository);
        vaca2.setFechaAlta(LocalDate.now().minusDays(7));
        animalRepository.save(vaca2);

        // vaca3: también en lote1; recibirá telemetría vieja (> 15 min) para mostrar
        // el indicador "sin datos recientes" en el mapa (H0012). El alias es neutro
        // porque el nombre se ve en el listado público.
        Animal vaca3 = crearAnimal(est, lote1, "33333", "Renegrida", "Brangus",
                LocalDate.of(2022, 3, 15), "VIGIA-ARG-003", caravanaRepository, animalRepository);

        Animal vaca4 = crearAnimal(est, lote1, "44444", "Blanca", "Holando",
                LocalDate.of(2021, 8, 20), "VIGIA-ARG-004", caravanaRepository, animalRepository);

        Animal vaca5 = crearAnimal(est, lote1, "55555", "Pinta", "Jersey",
                LocalDate.of(2022, 1, 10), "VIGIA-ARG-005", caravanaRepository, animalRepository);

        log.info("[DataInitializer] ✅ 5 animales operativos en Rodeo 01 (+ 3 de demo de alertas sanitarias en el Acto 4) "
                + "— Rodeo 02 vacío (reservado para demo CSV H0005).");

        // ── Acto 3: Dashboard / mapa ──────────────────────────────────────
        // Posiciones recientes para que el mapa se vea poblado desde el arranque.
        // vaca1 (forzada a celo en la demo, ver simulador/simulador_demo.py) recibe
        // acá su historial completo de 24hs con patrón de celo, en vez de un
        // único punto reciente: así el simulador no tiene que inyectarlo al
        // arrancar, y solo necesita mandar un puñado de puntos nuevos en vivo
        // para que el próximo ciclo del job de celo (corre cada 60s, ver
        // BatchScheduler) evalúe la ventana completa —ya con forma de celo—
        // y dispare la alerta casi de inmediato.
        sembrarHistorialCelo(vaca1.getCaravana(), telemetriaRepository);
        crearTelemetria(vaca2.getCaravana(), -34.9448, -59.2241, 76.0, LocalDateTime.now().minusMinutes(6), telemetriaRepository);
        crearTelemetria(vaca4.getCaravana(), -34.9421, -59.2279, 91.0, LocalDateTime.now().minusMinutes(2), telemetriaRepository);
        crearTelemetria(vaca5.getCaravana(), -34.9455, -59.2260, 64.0, LocalDateTime.now().minusMinutes(10), telemetriaRepository);

        // vaca3 se deja con una única transmisión de hace 1 hora (> 15 min)
        // para disparar el indicador "sin datos recientes" en el mapa (H0012).
        crearTelemetria(vaca3.getCaravana(), -34.943317, -59.225728, 45.0, LocalDateTime.now().minusHours(1), telemetriaRepository);

        // Historial de pastoreo de 7 días para el mapa de calor (H0025): sin esto
        // el rango por defecto del mapa (7 días) se alimenta de un solo animal con
        // 24hs y de cuatro puntos sueltos, y tanto el heatmap como el filtro por
        // rodeo quedan vacíos. Cada historial termina un tick ANTES de la posición
        // reciente de arriba, para no alterar los indicadores del Acto 3.
        LocalDateTime ahoraPastoreo = LocalDateTime.now();
        sembrarHistorialPastoreo(vaca2.getCaravana(), -34.9448, -59.2241, 79.0, 7, ahoraPastoreo.minusMinutes(21), telemetriaRepository);
        sembrarHistorialPastoreo(vaca4.getCaravana(), -34.9421, -59.2279, 94.0, 7, ahoraPastoreo.minusMinutes(17), telemetriaRepository);
        sembrarHistorialPastoreo(vaca5.getCaravana(), -34.9455, -59.2260, 67.0, 7, ahoraPastoreo.minusMinutes(25), telemetriaRepository);
        sembrarHistorialPastoreo(vaca3.getCaravana(), -34.943317, -59.225728, 48.0, 7, ahoraPastoreo.minusMinutes(75), telemetriaRepository);
        // vaca1 completa los 6 días previos a su ventana de celo (que arranca en -24hs).
        sembrarHistorialPastoreo(vaca1.getCaravana(), -34.9430, -59.2255, 92.0, 6, ahoraPastoreo.minusHours(24).minusMinutes(15), telemetriaRepository);

        log.info("[DataInitializer] ✅ Telemetría cargada — 3 animales con posición reciente, "
                + "vaca1 con 24hs de historial en patrón de celo, 1 en estado 'sin datos recientes', "
                + "y 7 días de historial de pastoreo en los 5 animales para el mapa de calor.");

        // ── Acto 4: Alertas sanitarias (demo de la vista de detalle sanitario) ──
        // El detalle sanitario del endpoint /api/v1/alertas/{id} devuelve un
        // snapshot congelado de la serie comparativa (alertas.serie_comparativa_json)
        // que en producción arma el worker con SerieComparativaBuilder. Acá se
        // usa el MISMO builder con ventanas/perfiles sintéticos para cubrir los
        // estados que la maqueta del frontend contempla:
        //   6a  serie completa (perfil + telemetría)  -> gráfico con ambas barras
        //   7   sin perfil de referencia               -> solo barra "actual" + aviso
        //   8   sin telemetría en la ventana           -> aviso "sin datos"
        //   6b  además una CONFIRMADA (estado no accionable + lista en la ficha)
        //
        // Estas alertas viven en ANIMALES DEDICADOS (VIGIA-ARG-006/007/008), NO
        // en vaca1..vaca5, para no chocar con el simulador:
        //   - vaca1 (VIGIA-ARG-001) es el animal de la demo de CELO.
        //   - vaca2 (VIGIA-ARG-002) es el FORZAR_SALUD_DEVICE_ID del simulador:
        //     debe quedar limpio para que el worker le genere su alerta real.
        //   - el simulador por defecto solo maneja NUM_ANIMALES=5, así que las
        //     vacas 006/008 no reciben telemetría en vivo.
        // Aún así se fechan > 24 h atrás: si alguien sube NUM_ANIMALES, el
        // cooldown de 24 h del worker (SaludDetectorStrategyImpl.process) no se
        // dispara con datos de demo viejos.
        LocalDateTime ahora = LocalDateTime.now();

        Animal demoConPerfil = crearAnimal(est, lote1, "60006", "Demo Salud — con perfil", "Angus",
                LocalDate.of(2021, 1, 1), "VIGIA-ARG-006", caravanaRepository, animalRepository);
        Animal demoSinPerfil = crearAnimal(est, lote1, "70007", "Demo Salud — sin perfil", "Hereford",
                LocalDate.of(2021, 6, 1), "VIGIA-ARG-007", caravanaRepository, animalRepository);
        Animal demoSinTelemetria = crearAnimal(est, lote1, "80008", "Demo Salud — sin telemetría", "Brangus",
                LocalDate.of(2021, 9, 1), "VIGIA-ARG-008", caravanaRepository, animalRepository);

        Map<String, Object> perfilNormal = perfilDemo(
                new double[] { 6.2, 5.4, 4.1, 5.0 }, new double[] { 0.8, 0.7, 0.6, 0.7 },
                new double[] { 11.5, 9.0, 7.2, 9.5 }, new double[] { 2.1, 1.8, 1.5, 1.9 });

        // 6a — ALTA, PENDIENTE (hace ~2 días): actividad y desplazamiento muy
        // por debajo del perfil (postración) -> barras "actual" rojas.
        crearAlertaSanitaria(demoConPerfil, SeveridadAlerta.ALTA, EstadoAlerta.PENDIENTE, 0.93f,
                "Problema severo postrante o complicación de parto",
                ventanaSanitaria(demoConPerfil.getCaravana(), ahora.minusDays(2), 2, 0.00001),
                perfilNormal, ahora.minusDays(9), null, null);

        // 6b — ALTA, CONFIRMADA hace ~4 días: hiperactividad sostenida por
        // encima del perfil. Estado no accionable + el animal queda con 2 alertas.
        crearAlertaSanitaria(demoConPerfil, SeveridadAlerta.ALTA, EstadoAlerta.CONFIRMADA, 0.88f,
                "Problema severo postrante o complicación de parto",
                ventanaSanitaria(demoConPerfil.getCaravana(), ahora.minusDays(4), 9, 0.00009),
                perfilNormal, ahora.minusDays(9), ahora.minusDays(4).plusHours(2), vet);

        // 7 — MEDIA, PENDIENTE (hace ~2 días), SIN perfil (animal recién
        // calibrado): el gráfico muestra solo el comportamiento actual + nota.
        crearAlertaSanitaria(demoSinPerfil, SeveridadAlerta.MEDIA, EstadoAlerta.PENDIENTE, 0.78f,
                "Enfermedad moderada / Cojera clínica",
                ventanaSanitaria(demoSinPerfil.getCaravana(), ahora.minusDays(2).minusHours(8), 3, 0.00002),
                null, null, null, null);

        // 8 — BAJA, PENDIENTE (hace ~2 días), SIN telemetría en la ventana:
        // el gráfico muestra el aviso "no hay telemetría suficiente".
        crearAlertaSanitaria(demoSinTelemetria, SeveridadAlerta.BAJA, EstadoAlerta.PENDIENTE, 0.61f,
                "Enfermedad leve / Inicio",
                new ArrayList<>(), null, null, null, null);

        log.info("[DataInitializer] ✅ 3 animales de demo (VIGIA-ARG-006/007/008) con 4 alertas sanitarias "
                + "fechadas > 24 h atrás. vaca1..vaca5 quedan libres para el simulador (celo y salud).");

        // ── Acto 5: Historial reproductivo (demo de la sección de la ficha del animal) ──
        // Animal dedicado (VIGIA-ARG-009) para no chocar con el simulador. El historial
        // sale íntegro de estados_reproductivos; la inseminación y la revisión veterinaria
        // se siembran por el mismo camino que produce intervenirAlerta: la intervención
        // (acto) + el EstadoReproductivo / EstadoSalud que ese acto genera.
        Animal demoRepro = crearAnimal(est, lote1, "90009", "Demo Historial Reproductivo", "Angus",
                LocalDate.of(2020, 6, 1), "VIGIA-ARG-009", caravanaRepository, animalRepository);
        LocalDateTime hoyDt = LocalDateTime.now();

        sembrarEstadoReproductivo(demoRepro, TipoReproductivo.PARIDA, OrigenEstado.MANUAL, vet,
                LocalDate.now().minusMonths(8), hoyDt.minusMonths(8));
        sembrarEstadoReproductivo(demoRepro, TipoReproductivo.VACIA, OrigenEstado.CARGA_MASIVA, productor,
                null, hoyDt.minusMonths(6));
        sembrarEstadoReproductivo(demoRepro, TipoReproductivo.POSIBLE_CELO, OrigenEstado.AUTOMATICO, null,
                null, hoyDt.minusDays(42));
        sembrarInseminacionDesdeIntervencion(demoRepro, vet, hoyDt.minusDays(40));
        sembrarRevisionConEstadoSalud(demoRepro, vet, TipoEstadoSalud.EN_OBSERVACION, hoyDt.minusDays(20));
        sembrarEstadoReproductivo(demoRepro, TipoReproductivo.GESTANTE, OrigenEstado.MANUAL, vet,
                null, hoyDt.minusDays(9));

        log.info("[DataInitializer] ✅ Animal VIGIA-ARG-009 con historial reproductivo de demo "
                + "(parto, vacía, celo ml, inseminación, gestante) + una revisión veterinaria con estado de salud.");

        // ── Acto 6: Anestro (demo de la alerta de anestro) ─────────────────────
        // Animales dedicados (VIGIA-ARG-010/011/012). Las alertas se siembran igual que
        // las genera AnestroDetectorService (tipo "Anestro Detectado", disparador CELO,
        // sin ventana de inseminación) con el umbral del establecimiento (45 días por defecto).
        int umbralAnestro = est.getDiasDeteccionAnestro();

        // 010 — PENDIENTE: tarjeta del sidebar, detalle, dashboard, tabla y línea de tiempo.
        Animal anestroPendiente = crearAnimal(est, lote1, "10010", "Demo Anestro — pendiente", "Angus",
                LocalDate.of(2019, 9, 1), "VIGIA-ARG-010", caravanaRepository, animalRepository);
        sembrarEstadoReproductivo(anestroPendiente, TipoReproductivo.PARIDA, OrigenEstado.MANUAL, vet,
                LocalDate.now().minusDays(120), hoyDt.minusDays(120));
        sembrarAlertaAnestro(est, anestroPendiente, EstadoAlerta.PENDIENTE, 119, hoyDt.minusDays(1), null, null);

        // 011 — CONFIRMADA: queda en estado ANESTRO (como al confirmar desde la UI),
        // así el detector no la vuelve a alertar y la línea de tiempo muestra alerta + estado.
        Animal anestroConfirmado = crearAnimal(est, lote1, "11011", "Demo Anestro — confirmado", "Hereford",
                LocalDate.of(2020, 2, 1), "VIGIA-ARG-011", caravanaRepository, animalRepository);
        sembrarEstadoReproductivo(anestroConfirmado, TipoReproductivo.PARIDA, OrigenEstado.MANUAL, vet,
                LocalDate.now().minusDays(150), hoyDt.minusDays(150));
        sembrarAlertaAnestro(est, anestroConfirmado, EstadoAlerta.CONFIRMADA, 138, hoyDt.minusDays(12),
                hoyDt.minusDays(10), vet);
        sembrarEstadoReproductivo(anestroConfirmado, TipoReproductivo.ANESTRO, OrigenEstado.MANUAL, vet,
                LocalDate.now().minusDays(150), hoyDt.minusDays(10));

        // 012 — SIN ALERTA pero califica (parto hace 90 días; la VACIA posterior no reinicia
        // el conteo): la primera corrida del detector le genera la alerta en vivo por SSE.
        // Para no esperar a las 2 AM: correr el worker con --vigia.batch.anestro-cron="0 * * * * *".
        Animal anestroCandidato = crearAnimal(est, lote1, "12012", "Demo Anestro — sin alerta", "Brangus",
                LocalDate.of(2020, 4, 1), "VIGIA-ARG-012", caravanaRepository, animalRepository);
        sembrarEstadoReproductivo(anestroCandidato, TipoReproductivo.PARIDA, OrigenEstado.MANUAL, vet,
                LocalDate.now().minusDays(90), hoyDt.minusDays(90));
        sembrarEstadoReproductivo(anestroCandidato, TipoReproductivo.VACIA, OrigenEstado.CARGA_MASIVA, productor,
                null, hoyDt.minusDays(30));

        log.info("[DataInitializer] ✅ Demo de anestro (umbral {} días): VIGIA-ARG-010 alerta PENDIENTE, "
                + "VIGIA-ARG-011 CONFIRMADA + estado ANESTRO, VIGIA-ARG-012 sin alerta (la genera el detector).",
                umbralAnestro);

        log.info("[DataInitializer] Usuarios disponibles para pruebas:");
        log.info("  admin@vigia.dev        / admin123      → ADMINISTRADOR");
        log.info("  productor@vigia.dev    / productor123  → PRODUCTOR");
        log.info("  campero@vigia.dev      / campero123    → CAMPERO");
        log.info("  veterinario@vigia.dev  / vet123        → VETERINARIO");
        log.info("  pendiente@vigia.dev    / pendiente123  → PENDIENTE");
    }

    // ---------------------------------------------------------------------------
    // Helpers del Acto 5 — historial reproductivo / de salud de demo.
    // ---------------------------------------------------------------------------

    private void sembrarEstadoReproductivo(Animal animal, TipoReproductivo estado, OrigenEstado origen,
                                           Usuario usuario, LocalDate fechaUltParto, LocalDateTime fechaRegistro) {
        EstadoReproductivo e = new EstadoReproductivo();
        e.setAnimal(animal);
        e.setEstado(estado);
        e.setOrigen(origen);
        e.setUsuarioRegistrador(usuario);
        e.setFechaUltParto(fechaUltParto);
        e.setFechaRegistro(fechaRegistro);
        estadoReproductivoRepository.save(e);
    }

    private void sembrarInseminacionDesdeIntervencion(Animal animal, Usuario interventor, LocalDateTime fechaHora) {
        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setTipo("Celo Detectado");
        alerta.setDisparador(DisparadorAlerta.CELO);
        alerta.setSeveridad(SeveridadAlerta.ALTA);
        alerta.setEstado(EstadoAlerta.CONFIRMADA);
        alerta.setTsGeneracion(fechaHora.minusHours(8));
        alerta.setTsConfirmacion(fechaHora.minusHours(6));
        alerta.setUsuarioConfirmacion(interventor);
        alerta.setValorUmbral(0.5);
        alerta.setValorDetectado(0.9);
        alerta.setConfianzaModelo(0.9f);
        alerta.setVentanaInicioIns(fechaHora.minusHours(2));
        alerta.setVentanaFinIns(fechaHora.plusHours(10));
        alertaRepository.save(alerta);

        Intervencion intervencion = new Intervencion();
        intervencion.setAlerta(alerta);
        intervencion.setAnimal(animal);
        intervencion.setInterventor(interventor);
        intervencion.setDescripcion("IATF — inseminación a tiempo fijo");
        intervencion.setFechaHora(fechaHora);
        intervencion.setTipo(TipoIntervencion.INSEMINACION);
        intervencionRepository.save(intervencion);

        EstadoReproductivo estado = new EstadoReproductivo();
        estado.setAnimal(animal);
        estado.setUsuarioRegistrador(interventor);
        estado.setEstado(TipoReproductivo.INSEMINADA);
        estado.setFechaUltInseminacion(fechaHora.toLocalDate());
        estado.setOrigen(OrigenEstado.MANUAL);
        estado.setFechaRegistro(fechaHora);
        estadoReproductivoRepository.save(estado);
    }

    /** Alerta de anestro con los mismos campos y textos que AnestroDetectorService.generarAlertaAnestro. */
    private void sembrarAlertaAnestro(Establecimiento est, Animal animal, EstadoAlerta estado, long diasSinCiclo,
                                      LocalDateTime tsGeneracion, LocalDateTime tsConfirmacion, Usuario usuario) {
        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setTipo(Alerta.TIPO_ANESTRO);
        alerta.setDisparador(DisparadorAlerta.CELO);
        alerta.setSeveridad(est.getSeveridadAnestro());
        alerta.setEstado(estado);
        alerta.setTsGeneracion(tsGeneracion);
        alerta.setValorUmbral((double) est.getDiasDeteccionAnestro());
        alerta.setValorDetectado((double) diasSinCiclo);
        alerta.setAccionRecomendada("Se requiere revisión veterinaria inmediata. El animal lleva "
                + diasSinCiclo + " días sin ciclo registrado.");
        alerta.setPosibleCausa("Anestro prolongado (>" + est.getDiasDeteccionAnestro() + " días)");
        alerta.setTsConfirmacion(tsConfirmacion);
        alerta.setUsuarioConfirmacion(usuario);
        alertaRepository.save(alerta);
    }

    private void sembrarRevisionConEstadoSalud(Animal animal, Usuario vet, TipoEstadoSalud estadoSalud,
                                               LocalDateTime fechaHora) {
        String notas = "Revisión clínica: leve cojera en miembro posterior, se deja en observación.";

        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setTipo("SALUD");
        alerta.setDisparador(DisparadorAlerta.SALUD);
        alerta.setSeveridad(SeveridadAlerta.MEDIA);
        alerta.setEstado(EstadoAlerta.CONFIRMADA);
        alerta.setTsGeneracion(fechaHora.minusDays(1));
        alerta.setTsConfirmacion(fechaHora.minusHours(3));
        alerta.setUsuarioConfirmacion(vet);
        alerta.setValorUmbral(0.5);
        alerta.setValorDetectado(0.7);
        alerta.setConfianzaModelo(0.7f);
        alertaRepository.save(alerta);

        Intervencion intervencion = new Intervencion();
        intervencion.setAlerta(alerta);
        intervencion.setAnimal(animal);
        intervencion.setInterventor(vet);
        intervencion.setDescripcion(notas);
        intervencion.setFechaHora(fechaHora);
        intervencion.setTipo(TipoIntervencion.REVISION_VETERINARIA);
        intervencionRepository.save(intervencion);

        EstadoSalud es = new EstadoSalud();
        es.setAnimal(animal);
        es.setUsuarioVeterinario(vet);
        es.setFechaRevision(fechaHora.toLocalDate());
        es.setEstado(estadoSalud);
        es.setDiagnosticoNotas(notas);
        es.setOrigen(OrigenEstado.MANUAL);
        es.setFechaRegistro(fechaHora);
        estadoSaludRepository.save(es);
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

    private Animal crearAnimal(Establecimiento est, RodeoLote lote, String idOficial, String alias, String raza,
                                LocalDate fechaNacimiento, String codigoCaravana,
                                CaravanaRepository caravanaRepository, AnimalRepository animalRepository) {
        Animal a = new Animal(est, lote);
        a.setIdCaravanaOficial(idOficial);
        a.setAlias(alias);
        a.setEstadoActual(EstadoAnimal.ACTIVO);
        a.setFechaNacimiento(fechaNacimiento);
        // OJO: confirmá que el setter se llama así en tu modelo Animal —
        // si no existe (o se llama distinto), comentá esta línea.
        // a.setRaza(raza);

        Caravana caravana = new Caravana();
        caravana.setCodigo(codigoCaravana);
        caravana.setDevEui(codigoCaravana);
        caravana.setActivo(true);
        caravana.setEstablecimiento(est);
        caravana.setEstadoSensor(EstadoSensor.VINCULADO);
        caravanaRepository.save(caravana);

        a.setCaravana(caravana);
        animalRepository.save(a);
        return a;
    }

    // ---------------------------------------------------------------------------
    // Siembra las 288 filas (24hs, 1 cada 5 min) de historial de celo para la
    // vaca forzada de la demo (VIGIA-ARG-001 por defecto en el simulador),
    // con el mismo patron de "alta actividad sostenida y erratica" que antes
    // generaba simulador_demo.py al arrancar (ver el docstring de ese
    // archivo). Al persistirse aca, en el arranque del backend, el
    // simulador de la demo ya no necesita mandar esos ~288 requests: solo
    // arranca su loop en vivo y, apenas manda un puñado de puntos nuevos
    // (quedan en estado PENDIENTE), el proximo ciclo del job de celo ya
    // evalua una ventana de 24hs con forma de celo de punta a punta.
    //
    // Se marca EstadoInferencia.COMPLETADO (no PENDIENTE) porque esta
    // telemetria sembrada no debe disparar por si sola el job de celo: el
    // que la dispara es la primera telemetria PENDIENTE que llegue por
    // /api/v1/ingesta/uplink (ver CeloBatchConfig.celoProcessor()).
    //
    // Escala fisica alineada con ml-training/celo/generate_dataset.py y con
    // el simulador (simulador/simulador.py): SPEED_BASE = 0.000018 grados
    // por tick de 5 min en pastoreo normal; en celo, generate_dataset.py
    // multiplica la velocidad por 1.0 + intensidad*(2 a 3) -> hasta 4x.
    // ---------------------------------------------------------------------------
    private void sembrarHistorialCelo(Caravana caravana, TelemetriaRepository telemetriaRepository) {
        final int SAMPLES_PER_DIA = 288;
        final int TICK_HISTORIAL_SEGUNDOS = 300;
        final double SPEED_BASE_DEG_POR_TICK_5MIN = 0.000018;
        final double SPEED_MULT_CELO_MIN = 2.5;
        final double SPEED_MULT_CELO_MAX = 4.0;
        final double TURN_STD_CELO = 1.1;
        final int MOVESTATE_ALTA_ACTIVIDAD_MIN = 7;
        final int MOVESTATE_ALTA_ACTIVIDAD_MAX = 9;

        Random rnd = new Random();
        double lat = -34.9430;
        double lon = -59.2255;
        double heading = rnd.nextDouble() * 2 * Math.PI;
        double bateria = 88.0;

        // La ultima fila queda a unos minutos de "ahora" (igual que el resto
        // de los animales del Acto 3), para que el mapa la siga mostrando
        // como posicion reciente.
        LocalDateTime ultimaFecha = LocalDateTime.now().minusMinutes(3);

        for (int i = SAMPLES_PER_DIA; i >= 1; i--) {
            LocalDateTime fecha = ultimaFecha.minusSeconds((long) (i - 1) * TICK_HISTORIAL_SEGUNDOS);
            double speedMult = SPEED_MULT_CELO_MIN + rnd.nextDouble() * (SPEED_MULT_CELO_MAX - SPEED_MULT_CELO_MIN);
            heading += rnd.nextGaussian() * TURN_STD_CELO;
            double speed = SPEED_BASE_DEG_POR_TICK_5MIN * speedMult;
            lat += speed * Math.cos(heading);
            lon += speed * Math.sin(heading);
            bateria -= 0.01;

            int moveState = MOVESTATE_ALTA_ACTIVIDAD_MIN
                    + rnd.nextInt(MOVESTATE_ALTA_ACTIVIDAD_MAX - MOVESTATE_ALTA_ACTIVIDAD_MIN + 1);

            Telemetria t = new Telemetria();
            t.setCaravana(caravana);
            t.setFechaHora(fecha);
            t.setLatitud(lat);
            t.setLongitud(lon);
            t.setMoveState(moveState);
            t.setBateria(bateria);
            t.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO);
            t.setEstadoInferenciaSalud(EstadoInferencia.COMPLETADO);
            telemetriaRepository.save(t);
        }
    }

    // ---------------------------------------------------------------------------
    // Historial de pastoreo para el mapa de calor (H0025).
    //
    // A diferencia de sembrarHistorialCelo, que reproduce el patrón de actividad
    // que el modelo de celo tiene que detectar, acá solo importa DÓNDE estuvo el
    // animal: una caminata aleatoria con atracción a la aguada, para que el mapa
    // muestre zonas calientes (donde abreva el rodeo), zonas de uso medio y
    // rincones poco visitados, que es lo que pide el criterio de aceptación.
    //
    // Un punto cada 15 min (no cada 5 como el historial de celo) alcanza de sobra
    // para una grilla de 20x20 y mantiene el arranque liviano: ~672 filas por
    // animal para 7 días. Se marca COMPLETADO para no disparar los jobs de ML.
    // ---------------------------------------------------------------------------
    private void sembrarHistorialPastoreo(Caravana caravana, double latInicial, double lonInicial,
                                          double bateriaInicial, int dias, LocalDateTime ultimaFecha,
                                          TelemetriaRepository telemetriaRepository) {
        final int TICK_SEGUNDOS = 900;
        final double PASO_DEG_POR_TICK = 0.000054;   // pastoreo normal en un tick de 15 min
        final double TURN_STD = 0.9;
        final double PROB_IR_A_LA_AGUADA = 0.12;
        final double RADIO_POTRERO_DEG = 0.004;      // ~450 m alrededor de la aguada
        final double AGUADA_LAT = -34.9440;
        final double AGUADA_LON = -59.2250;
        final int MOVESTATE_PASTOREO_MIN = 2;
        final int MOVESTATE_PASTOREO_MAX = 5;

        Random rnd = new Random();
        double lat = latInicial;
        double lon = lonInicial;
        double heading = rnd.nextDouble() * 2 * Math.PI;
        double bateria = bateriaInicial;

        int muestras = (int) ((long) dias * 24 * 3600 / TICK_SEGUNDOS);
        List<Telemetria> historial = new ArrayList<>(muestras);

        for (int i = muestras; i >= 1; i--) {
            LocalDateTime fecha = ultimaFecha.minusSeconds((long) (i - 1) * TICK_SEGUNDOS);

            double dLat = AGUADA_LAT - lat;
            double dLon = AGUADA_LON - lon;
            boolean fueraDelPotrero = Math.hypot(dLat, dLon) > RADIO_POTRERO_DEG;
            if (fueraDelPotrero || rnd.nextDouble() < PROB_IR_A_LA_AGUADA) {
                heading = Math.atan2(dLon, dLat);    // rumbo hacia la aguada
            } else {
                heading += rnd.nextGaussian() * TURN_STD;
            }

            lat += PASO_DEG_POR_TICK * Math.cos(heading);
            lon += PASO_DEG_POR_TICK * Math.sin(heading);
            bateria = Math.max(5.0, bateria - 0.005);

            Telemetria t = new Telemetria();
            t.setCaravana(caravana);
            t.setFechaHora(fecha);
            t.setLatitud(lat);
            t.setLongitud(lon);
            t.setMoveState(MOVESTATE_PASTOREO_MIN
                    + rnd.nextInt(MOVESTATE_PASTOREO_MAX - MOVESTATE_PASTOREO_MIN + 1));
            t.setBateria(bateria);
            t.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO);
            t.setEstadoInferenciaSalud(EstadoInferencia.COMPLETADO);
            historial.add(t);
        }

        telemetriaRepository.saveAll(historial);
    }

    private void crearTelemetria(Caravana caravana, double lat, double lon, double bateria,
                                  LocalDateTime fechaHora, TelemetriaRepository telemetriaRepository) {
        Telemetria t = new Telemetria();
        t.setCaravana(caravana);
        t.setFechaHora(fechaHora);
        t.setLatitud(lat);
        t.setLongitud(lon);
        t.setBateria(bateria);
        t.setMoveState(1);
        t.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO);
        t.setEstadoInferenciaSalud(EstadoInferencia.COMPLETADO);
        telemetriaRepository.save(t);
    }

    // ---------------------------------------------------------------------------
    // Helpers para las alertas sanitarias de demo (Acto 4).
    // ---------------------------------------------------------------------------

    private Map<String, Object> perfilDemo(double[] moveStateMean, double[] moveStateStd,
                                           double[] distanciaMean, double[] distanciaStd) {
        Map<String, Object> perfil = new LinkedHashMap<>();
        perfil.put("quarter_moveState_mean", toDoubleList(moveStateMean));
        perfil.put("quarter_moveState_std", toDoubleList(moveStateStd));
        perfil.put("quarter_distancia_mean", toDoubleList(distanciaMean));
        perfil.put("quarter_distancia_std", toDoubleList(distanciaStd));
        perfil.put("feature_version", "v1");
        return perfil;
    }

    private List<Double> toDoubleList(double[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (double d : arr) {
            list.add(d);
        }
        return list;
    }

    private List<Telemetria> ventanaSanitaria(Caravana caravana, LocalDateTime hasta, int moveState, double pasoLat) {
        final int muestras = 288;
        final int tickSegundos = 300;
        List<Telemetria> puntos = new ArrayList<>(muestras);
        double lat = -34.9430;
        double lon = -59.2255;
        for (int i = muestras - 1; i >= 0; i--) {
            Telemetria t = new Telemetria();
            t.setCaravana(caravana);
            t.setFechaHora(hasta.minusSeconds((long) i * tickSegundos));
            t.setMoveState(moveState);
            t.setLatitud(lat);
            t.setLongitud(lon);
            t.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO);
            t.setEstadoInferenciaSalud(EstadoInferencia.COMPLETADO);
            puntos.add(t);
            lat += pasoLat;
        }
        return puntos;
    }

    private void crearAlertaSanitaria(Animal animal, SeveridadAlerta severidad, EstadoAlerta estado,
                                      float confianza, String posibleCausa, List<Telemetria> ventana,
                                      Map<String, Object> perfil, LocalDateTime perfilCalculadoEn,
                                      LocalDateTime tsConfirmacion, Usuario usuarioConfirmacion) {

        LocalDateTime tsGeneracion = ventana.isEmpty()
                ? LocalDateTime.now().minusHours(3)
                : ventana.get(ventana.size() - 1).getFechaHora();

        SerieComparativaSnapshot snapshot = SerieComparativaBuilder.build(
                ventana, perfil, perfilCalculadoEn, severidad.name(), posibleCausa);

        String serieJson;
        try {
            serieJson = objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar la serie comparativa de demo", e);
        }

        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setTipo("SALUD");
        alerta.setDisparador(DisparadorAlerta.SALUD);
        alerta.setSeveridad(severidad);
        alerta.setEstado(estado);
        alerta.setTsGeneracion(tsGeneracion);
        alerta.setValorUmbral(0.5);
        alerta.setValorDetectado((double) confianza);
        alerta.setConfianzaModelo(confianza);
        alerta.setDesvioDetectado(true);
        alerta.setPosibleCausa(posibleCausa);
        alerta.setSerieComparativaJson(serieJson);
        if (tsConfirmacion != null) {
            alerta.setTsConfirmacion(tsConfirmacion);
            alerta.setUsuarioConfirmacion(usuarioConfirmacion);
        }
        alertaRepository.save(alerta);
    }
}