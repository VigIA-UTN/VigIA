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
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.SeveridadAlerta;
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

/**
 * Siembra datos de prueba en cada arranque, LIMPIANDO antes toda la base.
 *
 * <p>Solo se activa con el perfil {@code dev}. El default de la aplicación es
 * {@code prod} (ver {@code application.yaml}), así que un despliegue sin
 * {@code SPRING_PROFILES_ACTIVE} nunca ejecuta este runner ni borra datos. El
 * entorno de <b>develop</b> setea {@code SPRING_PROFILES_ACTIVE=dev} a propósito;
 * <b>producción</b> nunca debe hacerlo. Para poblar producción, usar el script
 * manual {@code infra/prod/seed_prod.sql}.
 */
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
    private final ObjectMapper objectMapper;

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
        // (antes solo se vinculaba al productor — campero y vet quedaban sin acceso al rodeo)
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

        log.info("[DataInitializer] ✅ Telemetría cargada — 3 animales con posición reciente, "
                + "vaca1 con 24hs de historial en patrón de celo, 1 en estado 'sin datos recientes'.");

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
        caravana.setActivo(true);
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

    /** Perfil de referencia individual sintético (arrays de 4 "quarters" de 6 h). */
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

    /**
     * Ventana de 288 puntos (24 h, 1 cada 5 min) terminando en {@code hasta},
     * con {@code moveState} constante y un paso de latitud fijo por tick (para
     * que la distancia GPS agregada sea determinística). NO se persiste: solo
     * alimenta a {@link SerieComparativaBuilder}.
     */
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

    /**
     * Crea y persiste una alerta sanitaria de demo con su snapshot de serie
     * comparativa ya calculado (mismo camino que el worker en producción).
     *
     * @param ventana            puntos evaluados; lista vacía => alerta sin serie
     * @param perfil             perfil de referencia; null => serie sin referencia
     * @param perfilCalculadoEn  fecha del perfil (null si no hay perfil)
     * @param tsConfirmacion     null => alerta PENDIENTE recién generada
     * @param usuarioConfirmacion quién resolvió la alerta (si aplica)
     */
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