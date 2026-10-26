package dev.vigia.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vigia.backend.dto.SerieComparativaSnapshot;
import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Caravana;
import dev.vigia.backend.model.Diagnostico;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Empleado;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import dev.vigia.backend.model.EstadoAnimal;
import dev.vigia.backend.model.EstadoInferencia;
import dev.vigia.backend.model.EstadoReproductivo;
import dev.vigia.backend.model.EstadoSalud;
import dev.vigia.backend.model.EstadoSensor;
import dev.vigia.backend.model.Intervencion;
import dev.vigia.backend.model.OrigenEstado;
import dev.vigia.backend.model.Perimetro;
import dev.vigia.backend.model.RegistroCohesion;
import dev.vigia.backend.model.RegistroPeso;
import dev.vigia.backend.model.RodeoLote;
import dev.vigia.backend.model.Rol;
import dev.vigia.backend.model.SeveridadAlerta;
import dev.vigia.backend.model.Telemetria;
import dev.vigia.backend.model.TipoEstadoSalud;
import dev.vigia.backend.model.TipoIntervencion;
import dev.vigia.backend.model.TipoReproductivo;
import dev.vigia.backend.model.Usuario;
import dev.vigia.backend.repository.AlertaAnimalAisladoRepository;
import dev.vigia.backend.repository.AlertaRepository;
import dev.vigia.backend.repository.AnimalRepository;
import dev.vigia.backend.repository.CaravanaRepository;
import dev.vigia.backend.repository.DiagnosticoRepository;
import dev.vigia.backend.repository.EstablecimientoRepository;
import dev.vigia.backend.repository.EstadoReproductivoRepository;
import dev.vigia.backend.repository.EstadoSaludRepository;
import dev.vigia.backend.repository.IntervencionRepository;
import dev.vigia.backend.repository.InvitacionRepository;
import dev.vigia.backend.repository.PerfilReferenciaRepository;
import dev.vigia.backend.repository.PerimetroRepository;
import dev.vigia.backend.repository.RegistroCohesionRepository;
import dev.vigia.backend.repository.RegistroPesoRepository;
import dev.vigia.backend.repository.RodeoLoteRepository;
import dev.vigia.backend.repository.TelemetriaRepository;
import dev.vigia.backend.repository.UsuarioRepository;
import dev.vigia.backend.util.GeometryUtils;
import dev.vigia.backend.util.SerieComparativaBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Datos de prueba para desarrollo y para la demo E2E (Sprint 10): limpia la base
 * y la vuelve a sembrar en cada arranque. Solo corre con el perfil "dev"; nunca
 * con "prod" (el default de la app).
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
        private final EstadoSaludRepository estadoSaludRepository;
        private final AlertaRepository alertaRepository;
        private final IntervencionRepository intervencionRepository;
        private final DiagnosticoRepository diagnosticoRepository;
        private final CaravanaRepository caravanaRepository;
        private final TelemetriaRepository telemetriaRepository;
        private final RegistroCohesionRepository registroCohesionRepository;
        private final RegistroPesoRepository registroPesoRepository;
        private final PerfilReferenciaRepository perfilReferenciaRepository;
        private final AlertaAnimalAisladoRepository alertaAnimalAisladoRepository;
        private final PerimetroRepository perimetroRepository;
        private final PasswordEncoder passwordEncoder;
        private final ObjectMapper objectMapper;
        private final JdbcTemplate jdbcTemplate;

        private static final double LAT_CENTRO = -34.943317;
        private static final double LON_CENTRO = -59.225728;

        // Donde pasta el rodeo del Lote Principal al arrancar. La ultima posicion de cada vaca
        // con datos recientes cae dentro de este circulo (~40 m de radio): ml-geo une a los
        // animales a <= 100 m y, si quedaran dispersos, marcaria aislados a todos (H0026).
        // simulador.py continua desde estas posiciones y mantiene al rodeo junto.
        private static final double LAT_RODEO = LAT_CENTRO - 0.0004;
        private static final double LON_RODEO = LON_CENTRO + 0.0004;
        private static final double RADIO_RODEO = 0.00035;

        // Acto 4: la #208 termina su historial apartada del rodeo y sin transmitir hace un rato. ml-geo
        // solo mira animales con posicion de los ultimos 30 min (contra el dato mas nuevo, que el seed
        // deja ~3 min antes del arranque): con 35 min no la ve hasta que simulador_salud.py vuelve a
        // mandar, y en ese momento ya cuenta como "fuera del grupo" el tramo sembrado.
        private static final int MINUTOS_SIN_DATOS_MARGARITA = 35;
        private static final double METROS_MARGARITA_AL_RODEO = 180;

        // Dias de historial de la #104: menos que vigia.salud.calibracion-dias (7), asi el job de salud no
        // le arma perfil. Con perfil, sus 24 hs de celo dan alerta de salud en el Acto 2 (validado contra
        // ml-salud: 40 de 40).
        private static final int DIAS_HISTORIAL_104 = 6;

        // Historial del resto del rodeo: 8 dias a 5 min, los 7 primeros son la calibracion del perfil de
        // salud (RF03, ver SaludDetectorStrategyImpl) y el ultimo, la ventana que evalua.
        private static final int DIAS_HISTORIAL_RODEO = 8;
        private static final int MUESTRAS_POR_DIA = 288;

        // Pastoreo de simulador.py (generar_payload): grados por paso de 5 min, giro y radio de vuelta al centro.
        private static final double PASO_SIMULADOR = 0.000018;
        private static final double GIRO_SIMULADOR = 0.35;
        private static final double RADIO_COHESION_M = 35.0;

        @Override
        @SuppressWarnings("null")
        public void run(String... args) {
                log.info("[DataInitializer] Iniciando seed de DEMO (Sprint 10 E2E)...");
                try {
                        jdbcTemplate.execute("SET vigia.bypass_immutable_triggers = 'true'");
                } catch (Exception ignored) {
                }

                telemetriaRepository.deleteAllInBatch();
                intervencionRepository.deleteAll();
                diagnosticoRepository.deleteAll();
                alertaAnimalAisladoRepository.deleteAll();
                alertaRepository.deleteAll();
                estadoReproductivoRepository.deleteAll();
                estadoSaludRepository.deleteAll();
                registroCohesionRepository.deleteAll();
                perfilReferenciaRepository.deleteAll();
                registroPesoRepository.deleteAll();
                invitacionRepository.deleteAll();
                animalRepository.deleteAll();
                caravanaRepository.deleteAll();
                rodeoLoteRepository.deleteAll();
                establecimientoRepository.deleteAll();
                usuarioRepository.deleteAll();

                // Usuarios
                Usuario admin = buildUsuario("Admin", "VigIA", "admin@vigia.dev", "admin123", Rol.ADMINISTRADOR);
                Usuario productor = buildUsuario("Juan", "Martínez", "juan@laesperanza.com", "productor123",
                                Rol.PRODUCTOR);
                Usuario campero = buildUsuario("Carlos", "Gómez", "carlos@laesperanza.com", "campero123", Rol.CAMPERO);
                Usuario vet = buildUsuario("Laura", "Benítez", "laura@vet.com", "vet123", Rol.VETERINARIO);
                usuarioRepository.saveAll(List.of(admin, productor, campero, vet));

                // Establecimiento principal (coords = simulador.py)
                Establecimiento estP = new Establecimiento();
                estP.setNombre("Tambo La Esperanza");
                estP.setLatitud(LAT_CENTRO);
                estP.setLongitud(LON_CENTRO);
                estP.setActivo(true);
                estP.setFechaAlta(LocalDate.now());
                establecimientoRepository.save(estP);
                estP.getEmpleados().add(new Empleado(estP, productor));
                estP.getEmpleados().add(new Empleado(estP, campero));
                estP.getEmpleados().add(new Empleado(estP, vet));
                establecimientoRepository.save(estP);

                // Segundo establecimiento (selector RNF03 -- productor en AMBOS)
                Establecimiento estAurora = new Establecimiento();
                estAurora.setNombre("Estancia La Aurora");
                estAurora.setLatitud(-34.920000);
                estAurora.setLongitud(-59.210000);
                estAurora.setActivo(true);
                estAurora.setFechaAlta(LocalDate.now());
                establecimientoRepository.save(estAurora);
                estAurora.getEmpleados().add(new Empleado(estAurora, productor));
                establecimientoRepository.save(estAurora);

                log.info("[DataInitializer] 2 establecimientos + 4 usuarios. Productor vinculado a ambos (RNF03).");

                // Rodeos
                RodeoLote lotePrincipal = new RodeoLote(estP);
                lotePrincipal.setNombre("Lote Principal");
                lotePrincipal.setActivo(true);
                rodeoLoteRepository.save(lotePrincipal);

                // Perimetro activo (H0027)
                crearPerimetroPrincipal(estP, lotePrincipal);

                LocalDateTime ahora = LocalDateTime.now();
                // Semilla fija: mismas posiciones en el rodeo en cada arranque.
                Random rndRodeo = new Random(7L);

                // Los estados reproductivos (y las alertas de anestro sembradas) van justo despues de crearAnimal: en
                // local el detector de anestro corre cada minuto y, si pasa a mitad del seed, alerta a toda vaca que
                // todavia no tenga evidencia de ciclo.

                // Vaca 1: id oficial 104, La Vaca Lola, compatible simulador
                Animal vaca1 = crearAnimal(estP, lotePrincipal, "104", "La Vaca Lola", "Holando",
                                LocalDate.of(2020, 1, 1), "VIGIA-ARG-001", LocalDate.now().minusDays(45));
                sembrarEstadoReproductivo(vaca1, TipoReproductivo.PARIDA, OrigenEstado.MANUAL, vet,
                                LocalDate.now().minusDays(15), ahora.minusDays(15));
                sembrarHistorialCelo(vaca1.getCaravana(), posicionEnRodeo(rndRodeo));

                // Vaca 2: id oficial 208, Margarita. Acto 4: 8 dias a 5 min (calibracion de salud
                // completa) con la caida de actividad en las ultimas 24 hs, apartada al oeste del rodeo;
                // ver sembrarHistorialMargarita y MINUTOS_SIN_DATOS_MARGARITA.
                Animal vaca2 = crearAnimal(estP, lotePrincipal, "208", "Margarita", "Jersey",
                                LocalDate.of(2021, 5, 10), "VIGIA-ARG-002", LocalDate.now().minusDays(45));
                sembrarEstadoReproductivo(vaca2, TipoReproductivo.PARIDA, OrigenEstado.MANUAL, vet,
                                LocalDate.now().minusDays(15), ahora.minusDays(15));
                double[] apartada = { LAT_RODEO, LON_RODEO - METROS_MARGARITA_AL_RODEO
                                / (111_320 * Math.cos(Math.toRadians(LAT_RODEO))) };
                sembrarHistorialMargarita(vaca2.getCaravana(), apartada,
                                ahora.minusMinutes(MINUTOS_SIN_DATOS_MARGARITA));

                // Vacas 3 a 8: paridas hace unas semanas.
                Animal vaca3 = crearAnimal(estP, lotePrincipal, numeroManejo(3), "Renegrida", "Holando",
                                LocalDate.of(2022, 3, 15), "VIGIA-ARG-003", LocalDate.now().minusDays(45));
                sembrarPartoReciente(vaca3, vet, ahora, 20);
                Animal vaca4 = crearAnimal(estP, lotePrincipal, numeroManejo(4), "Doradita", "Holando",
                                LocalDate.of(2021, 8, 20), "VIGIA-ARG-004", LocalDate.now().minusDays(45));
                sembrarPartoReciente(vaca4, vet, ahora, 25);
                // La unica caravana con bateria baja (14 %): transmite normal, para el bloque "¿Puedo confiar en
                // el monitoreo?" del dashboard (H0043).
                Animal vaca5 = crearAnimal(estP, lotePrincipal, numeroManejo(5), "Pinta", "Jersey",
                                LocalDate.of(2022, 1, 10), "VIGIA-ARG-005", LocalDate.now().minusDays(45));
                sembrarPartoReciente(vaca5, vet, ahora, 30);
                // Tuvo una neumonia hace 4 dias, ya diagnosticada y tratada (ver alertas sanitarias).
                Animal vaca6 = crearAnimal(estP, lotePrincipal, numeroManejo(6), "Bonita", "Holando",
                                LocalDate.of(2021, 1, 1), "VIGIA-ARG-006", LocalDate.now().minusDays(45));
                sembrarPartoReciente(vaca6, vet, ahora, 18);
                Animal vaca7 = crearAnimal(estP, lotePrincipal, numeroManejo(7), "Chola", "Jersey",
                                LocalDate.of(2021, 6, 1), "VIGIA-ARG-007", LocalDate.now().minusDays(45));
                sembrarPartoReciente(vaca7, vet, ahora, 22);
                Animal vaca8 = crearAnimal(estP, lotePrincipal, numeroManejo(8), "Manchada", "Holando",
                                LocalDate.of(2021, 9, 1), "VIGIA-ARG-008", LocalDate.now().minusDays(45));
                sembrarPartoReciente(vaca8, vet, ahora, 28);

                // Vaca 9: historial reproductivo completo (parto, celo, inseminacion, revision y prenez)
                Animal vaca9 = crearAnimal(estP, lotePrincipal, numeroManejo(9), "Perla", "Holando",
                                LocalDate.of(2020, 6, 1), "VIGIA-ARG-009", LocalDate.now().minusDays(45));
                sembrarEstadoReproductivo(vaca9, TipoReproductivo.PARIDA, OrigenEstado.MANUAL, vet,
                                LocalDate.now().minusMonths(8), ahora.minusMonths(8));
                sembrarEstadoReproductivo(vaca9, TipoReproductivo.VACIA, OrigenEstado.CARGA_MASIVA, productor,
                                null, ahora.minusMonths(6));
                sembrarEstadoReproductivo(vaca9, TipoReproductivo.POSIBLE_CELO, OrigenEstado.AUTOMATICO, null,
                                null, ahora.minusDays(42));
                sembrarInseminacionDesdeIntervencion(vaca9, vet, ahora.minusDays(40));
                sembrarRevisionConEstadoSalud(vaca9, vet, TipoEstadoSalud.EN_OBSERVACION, ahora.minusDays(20));
                sembrarGestante(vaca9, vet, LocalDate.now().minusDays(40), ahora.minusDays(9));

                // Vaca 10: la sugerencia de anestro del guion (H0030), pendiente de revision
                Animal anestroPendiente = crearAnimal(estP, lotePrincipal, numeroManejo(10), "Negrita", "Holando",
                                LocalDate.of(2019, 9, 1), "VIGIA-ARG-010", LocalDate.now().minusDays(45));
                sembrarEstadoReproductivo(anestroPendiente, TipoReproductivo.PARIDA, OrigenEstado.MANUAL, vet,
                                LocalDate.now().minusDays(120), ahora.minusDays(120));
                sembrarAlertaAnestro(estP, anestroPendiente, EstadoAlerta.PENDIENTE, 119, ahora.minusDays(1), null,
                                null);
                log.info("[DataInitializer] Anestro (umbral {} d): sugerencia pendiente para #{}.",
                                estP.getDiasDeteccionAnestro(), anestroPendiente.getIdCaravanaOficial());

                // Vacas 11 y 12: paridas hace unas semanas.
                Animal vaca11 = crearAnimal(estP, lotePrincipal, numeroManejo(11), "Blanca", "Jersey",
                                LocalDate.of(2020, 2, 1), "VIGIA-ARG-011", LocalDate.now().minusDays(45));
                sembrarPartoReciente(vaca11, vet, ahora, 26);
                Animal vaca12 = crearAnimal(estP, lotePrincipal, numeroManejo(12), "Tosca", "Holando",
                                LocalDate.of(2020, 4, 1), "VIGIA-ARG-012", LocalDate.now().minusDays(45));
                sembrarPartoReciente(vaca12, vet, ahora, 33);

                // Vacas 13-27: GESTANTE (15 vacas, partos en proximas 12 semanas)
                String[] nombresGestantes = {
                                "Aurora", "Canela", "Estrella", "Luna", "Morena",
                                "Paloma", "Nube", "Frutilla", "Rosa", "Violeta",
                                "Celeste", "Magnolia", "Clavel", "Dalia", "Iris"
                };
                int[] diasDesdeIa = { 250, 250, 250, 247, 240, 235, 230, 225, 220, 215, 210, 205, 200, 200, 199 };
                List<Animal> gestantes = new ArrayList<>();
                for (int i = 0; i < diasDesdeIa.length; i++) {
                        int num = 13 + i;
                        Animal g = crearAnimal(estP, lotePrincipal,
                                        numeroManejo(num), nombresGestantes[i], "Holando",
                                        LocalDate.of(2019, 10, 1),
                                        String.format("VIGIA-ARG-%03d", num),
                                        LocalDate.now().minusDays(45));
                        LocalDate ia = LocalDate.now().minusDays(diasDesdeIa[i]);
                        sembrarGestante(g, vet, ia, ia.plusDays(35).atTime(10, 0));
                        gestantes.add(g);
                }

                // 2 gestantes sin fecha de IA (casos edge H0044)
                Animal gestanteSinIa1 = crearAnimal(estP, lotePrincipal, numeroManejo(28), "Pampa", "Holando",
                                LocalDate.of(2020, 3, 1), "VIGIA-ARG-028", LocalDate.now().minusDays(45));
                sembrarGestanteSinIa(gestanteSinIa1, vet, ahora.minusDays(60));
                Animal gestanteSinIa2 = crearAnimal(estP, lotePrincipal, numeroManejo(29), "Serena", "Jersey",
                                LocalDate.of(2020, 5, 1), "VIGIA-ARG-029", LocalDate.now().minusDays(45));
                sembrarGestanteSinIa(gestanteSinIa2, vet, ahora.minusDays(45));

                // Vacas 30-35: INSEMINADA (6 vacas)
                String[] nombresIns = { "Brisa", "Coral", "Duna", "Espiga", "Flora", "Gema" };
                int[] diasIns = { 5, 10, 15, 20, 25, 3 };
                List<Animal> inseminadas = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                        int num = 30 + i;
                        Animal ins = crearAnimal(estP, lotePrincipal,
                                        numeroManejo(num), nombresIns[i], "Holando",
                                        LocalDate.of(2020, 7, 1),
                                        String.format("VIGIA-ARG-%03d", num),
                                        LocalDate.now().minusDays(45));
                        sembrarInseminada(ins, vet, LocalDate.now().minusDays(diasIns[i]).atTime(9, 0));
                        inseminadas.add(ins);
                }

                // Vacas 36-39: VACIA con >95 dias parto abierto (alerta H0044)
                String[] nombresVacias = { "Habana", "Ibiza", "Jade", "Kenya" };
                int[] diasParto = { 110, 130, 100, 120 };
                List<Animal> vacias = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                        int num = 36 + i;
                        Animal vacia = crearAnimal(estP, lotePrincipal,
                                        numeroManejo(num), nombresVacias[i], "Holando",
                                        LocalDate.of(2019, 12, 1),
                                        String.format("VIGIA-ARG-%03d", num),
                                        LocalDate.now().minusDays(45));
                        sembrarEstadoReproductivo(vacia, TipoReproductivo.PARIDA, OrigenEstado.MANUAL, vet,
                                        LocalDate.now().minusDays(diasParto[i]), ahora.minusDays(diasParto[i]));
                        sembrarEstadoReproductivo(vacia, TipoReproductivo.VACIA, OrigenEstado.CARGA_MASIVA, productor,
                                        null, ahora.minusDays(diasParto[i] - 14));
                        vacias.add(vacia);
                }
                // Los celos de sembrarAlertasHistoricas son su evidencia de ciclo, como en el flujo real (alerta de
                // celo -> POSIBLE_CELO, confirmacion -> CELO); sin ellos alertarian anestro. 038 no tiene alertas de
                // celo: celo visto en el campo. Despues siguen VACIA (no se inseminaron), ahora con la fecha del parto.
                sembrarEstadoReproductivo(vacias.get(0), TipoReproductivo.POSIBLE_CELO, OrigenEstado.AUTOMATICO, null,
                                null, ahora.minusDays(12));
                sembrarEstadoReproductivo(vacias.get(1), TipoReproductivo.CELO, OrigenEstado.MANUAL, campero,
                                null, ahora.minusDays(6).plusHours(2));
                sembrarEstadoReproductivo(vacias.get(2), TipoReproductivo.CELO, OrigenEstado.MANUAL, campero,
                                null, ahora.minusDays(18));
                sembrarEstadoReproductivo(vacias.get(3), TipoReproductivo.CELO, OrigenEstado.MANUAL, campero,
                                null, ahora.minusDays(7).plusHours(20));
                for (int i = 0; i < vacias.size(); i++) {
                        sembrarEstadoReproductivo(vacias.get(i), TipoReproductivo.VACIA, OrigenEstado.CARGA_MASIVA,
                                        productor, LocalDate.now().minusDays(diasParto[i]), ahora.minusDays(3));
                }

                Animal vaca40 = crearAnimal(estP, lotePrincipal, numeroManejo(40), "Ramona", "Holando",
                                LocalDate.of(2023, 1, 15), "VIGIA-ARG-040", LocalDate.now().minusDays(45));
                sembrarPartoReciente(vaca40, vet, ahora, 15);

                log.info("[DataInitializer] 40 vacas creadas en Lote Principal.");

                // 5 caravanas DISPONIBLE para Lote Cuarentena (H0040/H0041)
                for (int i = 301; i <= 305; i++) {
                        Caravana libre = new Caravana();
                        libre.setCodigo("VIGIA-LIBRE-" + i);
                        libre.setDevEui("VIGIA-LIBRE-" + i);
                        libre.setActivo(true);
                        libre.setEstablecimiento(estP);
                        libre.setEstadoSensor(EstadoSensor.DISPONIBLE);
                        caravanaRepository.save(libre);
                }
                log.info("[DataInitializer] 5 caravanas DISPONIBLE (VIGIA-LIBRE-301..305).");

                List<Animal> todos = new ArrayList<>();
                todos.addAll(List.of(vaca1, vaca2, vaca3, vaca4, vaca5, vaca6, vaca7, vaca8, vaca9,
                                anestroPendiente, vaca11, vaca12));
                todos.addAll(gestantes);
                todos.add(gestanteSinIa1);
                todos.add(gestanteSinIa2);
                todos.addAll(inseminadas);
                todos.addAll(vacias);
                todos.add(vaca40);

                // Telemetria: el rodeo pasta junto, con el mismo movimiento que simulador.py, y termina en el
                // rodeo de hoy (ver sembrarHistorialRodeo). #104 y #208 tienen su propia historia para los
                // actos 2 y 4.
                // #104: dias de pastoreo y las ultimas 24 hs con patron de celo. Menos de 7 dias en total (ver
                // DIAS_HISTORIAL_104): sin perfil de salud, que leeria esas 24 hs como un desvio.
                sembrarHistorialPastoreo(vaca1.getCaravana(), LAT_CENTRO - 0.001, LON_CENTRO - 0.001, 92.0,
                                DIAS_HISTORIAL_104 - 1, ahora.minusHours(24).minusMinutes(15), null);
                for (Animal animal : todos) {
                        if (animal == vaca1 || animal == vaca2) {
                                continue;
                        }
                        int n = Integer.parseInt(animal.getCaravana().getCodigo().substring("VIGIA-ARG-".length()));
                        double bateria = animal == vaca5 ? 14.0 : 62.0 + (n * 7) % 33;
                        sembrarHistorialRodeo(animal.getCaravana(), n, bateria, ahora.minusSeconds(60 + (n * 13) % 180));
                }
                log.info("[DataInitializer] Telemetria y pastoreo cargados (H0025).");

                // Cohesion Social: 7 dias para las 40 vacas (H0026)
                Random rndCohesion = new Random(1234L);
                for (Animal animal : todos) {
                        sembrarCohesion(animal, estP, 7, animal == vaca2, rndCohesion);
                }
                log.info("[DataInitializer] Cohesion social 7 dias sembrada (H0026).");

                // Alertas sanitarias con SerieComparativaBuilder
                Map<String, Object> perfilNormal = perfilDemo(
                                new double[] { 6.2, 5.4, 4.1, 5.0 }, new double[] { 0.8, 0.7, 0.6, 0.7 },
                                new double[] { 11.5, 9.0, 7.2, 9.5 }, new double[] { 2.1, 1.8, 1.5, 1.9 });

                Alerta saludConfirmada = crearAlertaSanitaria(vaca6, SeveridadAlerta.ALTA, EstadoAlerta.CONFIRMADA,
                                0.88f,
                                "Problema severo postrante o complicacion de parto",
                                ventanaSanitaria(vaca6.getCaravana(), ahora.minusDays(4), 9, 0.00009),
                                perfilNormal, ahora.minusDays(9), ahora.minusDays(4).plusHours(2), vet);
                sembrarDiagnostico(saludConfirmada, vet, "Cuadro febril compatible con neumonia",
                                "Oxitetraciclina LA 20 mg/kg IM, dosis unica. Controlar temperatura en 48 h.",
                                ahora.minusDays(4).plusHours(3));
                sembrarDiagnostico(saludConfirmada, vet, "Control a las 48 h: evolucion favorable",
                                "Sin nueva medicacion. Mantener en potrero cercano hasta el alta.",
                                ahora.minusDays(2).plusHours(3));

                log.info("[DataInitializer] Alerta sanitaria historica sembrada (#{}, atendida).",
                                vaca6.getIdCaravanaOficial());

                // Pesadas (150 dias)
                sembrarPesadas(vaca1, new double[][] { { 150, 400 }, { 95, 412 }, { 60, 431 }, { 25, 452 } });
                sembrarPesadas(vaca2, new double[][] { { 150, 362 }, { 100, 371 }, { 60, 380 }, { 20, 405 } });
                sembrarPesadas(vaca3, new double[][] { { 75, 445 }, { 35, 447 } });
                sembrarPesadas(vaca4, new double[][] { { 140, 440 }, { 90, 449 }, { 50, 455 }, { 20, 448 } });
                sembrarPesadas(vaca5, new double[][] { { 145, 318 }, { 95, 325 }, { 60, 330 }, { 22, 352 } });
                sembrarPesadas(vaca6, new double[][] { { 40, 468 }, { 3, 449 } });
                sembrarPesadas(vaca7, new double[][] { { 45, 410 }, { 10, 396 } });
                sembrarPesadas(vaca8, new double[][] { { 50, 400 }, { 12, 420 }, { 5, 418 } });
                sembrarPesadas(vaca9, new double[][] { { 140, 455 }, { 100, 462 }, { 50, 470 }, { 15, 488 } });
                sembrarPesadas(anestroPendiente, new double[][] { { 90, 432 }, { 45, 434 } });
                sembrarPesadas(vaca11, new double[][] { { 70, 420 }, { 30, 422 } });
                sembrarPesadas(vaca12,
                                new double[][] { { 150, 428 }, { 95, 433 }, { 80, 440 }, { 40, 460 } });
                sembrarPesadas(gestantes.get(0),
                                new double[][] { { 145, 430 }, { 95, 441 }, { 55, 453 }, { 20, 469 } });
                sembrarPesadas(gestantes.get(1),
                                new double[][] { { 150, 382 }, { 100, 390 }, { 60, 398 }, { 18, 411 } });
                sembrarPesadas(gestantes.get(2),
                                new double[][] { { 140, 415 }, { 90, 425 }, { 50, 436 }, { 16, 448 } });
                sembrarPesadas(gestantes.get(3),
                                new double[][] { { 155, 398 }, { 105, 405 }, { 65, 413 }, { 22, 428 } });
                sembrarPesadas(inseminadas.get(0), new double[][] { { 80, 365 }, { 40, 373 }, { 15, 388 } });
                sembrarPesadas(inseminadas.get(1), new double[][] { { 90, 420 }, { 45, 427 }, { 12, 441 } });
                sembrarPesadas(vacias.get(0), new double[][] { { 100, 455 }, { 55, 461 }, { 20, 475 } });
                sembrarPesadas(vacias.get(1), new double[][] { { 110, 480 }, { 60, 487 }, { 25, 501 } });

                log.info("[DataInitializer] Pesadas historicas cargadas.");

                // Alertas historicas 8 semanas (H0044/H0045) - vaca1 y vaca2 limpias para
                // simulador en vivo
                sembrarAlertasHistoricas(estP, vaca3, vaca4, vaca5,
                                gestantes, inseminadas, vacias, vet, campero, ahora);

                log.info("[DataInitializer] Alertas historicas 8 semanas sembradas (H0044/H0045).");
                log.info("[DataInitializer] SEED DEMO completado.");
                log.info("  admin@vigia.dev        / admin123      -> ADMINISTRADOR");
                log.info("  juan@laesperanza.com   / productor123  -> PRODUCTOR (ambos establecimientos)");
                log.info("  carlos@laesperanza.com / campero123    -> CAMPERO");
                log.info("  laura@vet.com          / vet123        -> VETERINARIO");
        }

        // =========================================================================
        // Alertas historicas 8 semanas
        // =========================================================================

        private void sembrarAlertasHistoricas(Establecimiento est,
                        Animal vaca3, Animal vaca4, Animal vaca5,
                        List<Animal> gestantes, List<Animal> inseminadas, List<Animal> vacias,
                        Usuario vet, Usuario campero, LocalDateTime ahora) {

                // 3 alertas de celo con ventana VENCIDA (H0044 ventanas perdidas)
                crearAlertaCelo(vaca3, ahora.minusDays(25), EstadoAlerta.VENCIDA, null, 6, 18, null);
                crearAlertaCelo(vaca4, ahora.minusDays(20), EstadoAlerta.VENCIDA, null, 6, 18, null);
                crearAlertaCelo(vacias.get(0), ahora.minusDays(22), EstadoAlerta.VENCIDA, null, 6, 18, null);

                // Reincidencia celo en vacias.get(0) y vacias.get(1) (>=2 alertas ultimos 30
                // dias, sin PENDIENTE ni vaca1/2)
                crearAlertaCelo(vacias.get(0), ahora.minusDays(12), EstadoAlerta.VENCIDA, null, 6, 18, null);
                crearAlertaCelo(vacias.get(1), ahora.minusDays(26), EstadoAlerta.CONFIRMADA,
                                ahora.minusDays(25).plusHours(4), 6, 18, vet);
                crearAlertaCelo(vacias.get(1), ahora.minusDays(7), EstadoAlerta.CONFIRMADA,
                                ahora.minusDays(6).plusHours(2), 6, 18, campero);

                // Reincidencia sanitaria en gestantes.get(0): 2 alertas en los ultimos 30 dias
                // (atendidas, sin PENDIENTE > 24h)
                crearAlertaSalud(gestantes.get(0), ahora.minusDays(29), EstadoAlerta.CONFIRMADA,
                                ahora.minusDays(28).plusHours(5), SeveridadAlerta.MEDIA, vet);
                crearAlertaSalud(gestantes.get(0), ahora.minusDays(10), EstadoAlerta.CONFIRMADA,
                                ahora.minusDays(9).plusHours(3), SeveridadAlerta.BAJA, vet);

                // ~23 alertas distribuidas en 8 semanas con variacion de tiempo de atencion
                crearAlertaCeloConAtencion(vaca5, ahora.minusDays(56), EstadoAlerta.CONFIRMADA, 3, vet);
                crearAlertaSaludConAtencion(inseminadas.get(0), ahora.minusDays(52), EstadoAlerta.DESCARTADA, 12,
                                campero);
                crearAlertaPerimetro(vaca3, ahora.minusDays(49), EstadoAlerta.CONFIRMADA,
                                ahora.minusDays(49).plusDays(2), vet);
                crearAlertaCeloConAtencion(vaca4, ahora.minusDays(48), EstadoAlerta.CONFIRMADA, 5, vet);
                crearAlertaSaludConAtencion(gestantes.get(1), ahora.minusDays(44), EstadoAlerta.CONFIRMADA, 10, vet);
                crearAlertaPerimetro(vaca5, ahora.minusDays(40), EstadoAlerta.DESCARTADA, ahora.minusDays(36), campero);
                crearAlertaCeloConAtencion(inseminadas.get(0), ahora.minusDays(35), EstadoAlerta.CONFIRMADA, 2,
                                campero);
                crearAlertaSaludConAtencion(gestantes.get(2), ahora.minusDays(34), EstadoAlerta.DESCARTADA, 18,
                                campero);
                crearAlertaPerimetro(vacias.get(2), ahora.minusDays(30), EstadoAlerta.CONFIRMADA, ahora.minusDays(29),
                                vet);
                crearAlertaCeloConAtencion(inseminadas.get(1), ahora.minusDays(28), EstadoAlerta.CONFIRMADA, 4, vet);
                crearAlertaSaludConAtencion(vacias.get(2), ahora.minusDays(25), EstadoAlerta.DESCARTADA, 72, campero);
                crearAlertaCeloConAtencion(gestantes.get(3), ahora.minusDays(21), EstadoAlerta.CONFIRMADA, 8, vet);
                crearAlertaSaludConAtencion(gestantes.get(4), ahora.minusDays(20), EstadoAlerta.CONFIRMADA, 5, vet);
                crearAlertaPerimetro(inseminadas.get(0), ahora.minusDays(18), EstadoAlerta.DESCARTADA,
                                ahora.minusDays(14), campero);
                crearAlertaCeloConAtencion(inseminadas.get(2), ahora.minusDays(16), EstadoAlerta.CONFIRMADA, 14, vet);
                crearAlertaSaludConAtencion(vacias.get(3), ahora.minusDays(14), EstadoAlerta.DESCARTADA, 50, campero);
                crearAlertaPerimetro(vaca3, ahora.minusDays(12), EstadoAlerta.CONFIRMADA, ahora.minusDays(11), vet);
                crearAlertaCeloConAtencion(gestantes.get(5), ahora.minusDays(10), EstadoAlerta.CONFIRMADA, 2, vet);
                crearAlertaSaludConAtencion(inseminadas.get(3), ahora.minusDays(8), EstadoAlerta.CONFIRMADA, 6, vet);
                crearAlertaCeloConAtencion(vacias.get(3), ahora.minusDays(7), EstadoAlerta.CONFIRMADA, 20, campero);
                crearAlertaPerimetro(vaca4, ahora.minusDays(45), EstadoAlerta.CONFIRMADA, ahora.minusDays(44), vet);
                crearAlertaPerimetro(vaca5, ahora.minusDays(32), EstadoAlerta.DESCARTADA, ahora.minusDays(30), campero);
                crearAlertaPerimetro(gestantes.get(0), ahora.minusDays(15), EstadoAlerta.CONFIRMADA,
                                ahora.minusDays(14), vet);
        }

        // =========================================================================
        // Perimetro geografico (H0027)
        // =========================================================================

        private void crearPerimetroPrincipal(Establecimiento est, RodeoLote lote) {
                double delta = 0.0045;
                GeometryFactory gf = GeometryUtils.getGeometryFactory();
                Coordinate[] coords = {
                                new Coordinate(LON_CENTRO - delta, LAT_CENTRO - delta),
                                new Coordinate(LON_CENTRO + delta, LAT_CENTRO - delta),
                                new Coordinate(LON_CENTRO + delta, LAT_CENTRO + delta),
                                new Coordinate(LON_CENTRO - delta, LAT_CENTRO + delta),
                                new Coordinate(LON_CENTRO - delta, LAT_CENTRO - delta)
                };
                LinearRing shell = gf.createLinearRing(coords);
                Polygon area = gf.createPolygon(shell);
                Perimetro p = new Perimetro(est, "Potrero Principal",
                                "Perimetro activo del lote de pastoreo principal (~500 m)", area);
                p.configurarUmbral(BigDecimal.valueOf(50), false);
                p.asignarLote(lote);
                perimetroRepository.save(p);
        }

        // =========================================================================
        // Cohesion Social (H0026)
        // =========================================================================

        private void sembrarCohesion(Animal animal, Establecimiento est, int dias,
                        boolean curvaDescendente, Random rnd) {
                int total = dias * 4; // 1 registro cada 6 horas
                LocalDateTime inicio = LocalDateTime.now().minusDays(dias);
                List<RegistroCohesion> registros = new ArrayList<>(total);
                for (int i = 0; i < total; i++) {
                        LocalDateTime fecha = inicio.plusHours((long) i * 6);
                        double indice;
                        if (curvaDescendente) {
                                double progreso = (double) i / total;
                                indice = 0.85 - progreso * 0.45 + rnd.nextGaussian() * 0.04;
                        } else {
                                indice = 0.75 + rnd.nextDouble() * 0.20 + rnd.nextGaussian() * 0.03;
                        }
                        indice = Math.max(0.0, Math.min(1.0, indice));
                        long tiempoFuera = curvaDescendente ? (long) (i * 120 + rnd.nextInt(300))
                                        : (long) rnd.nextInt(600);
                        registros.add(RegistroCohesion.builder()
                                        .animal(animal)
                                        .establecimiento(est)
                                        .fecha(fecha)
                                        .indiceCohesion(indice)
                                        .tiempoFueraGrupoS(tiempoFuera)
                                        .distanciaCentroideM(5.0 + rnd.nextDouble() * 30)
                                        .build());
                }
                registroCohesionRepository.saveAll(registros);
        }

        // =========================================================================
        // Helpers de alertas
        // =========================================================================

        private void crearAlertaCelo(Animal animal, LocalDateTime tsGeneracion, EstadoAlerta estado,
                        LocalDateTime tsConfirmacion, int horasInicioIns, int horasDuracionIns, Usuario confirmador) {
                Alerta a = new Alerta();
                a.setAnimal(animal);
                a.setTipo("Celo Detectado");
                a.setDisparador(DisparadorAlerta.CELO);
                a.setSeveridad(SeveridadAlerta.ALTA);
                a.setEstado(estado);
                a.setTsGeneracion(tsGeneracion);
                a.setValorUmbral(0.5);
                a.setValorDetectado(0.87);
                a.setConfianzaModelo(0.87f);
                a.setVentanaInicioIns(tsGeneracion.plusHours(horasInicioIns));
                a.setVentanaFinIns(tsGeneracion.plusHours(horasInicioIns + horasDuracionIns));
                a.setTsConfirmacion(tsConfirmacion);
                a.setUsuarioConfirmacion(confirmador);
                alertaRepository.save(a);
        }

        private void crearAlertaSalud(Animal animal, LocalDateTime tsGeneracion, EstadoAlerta estado,
                        LocalDateTime tsConfirmacion, SeveridadAlerta severidad, Usuario confirmador) {
                Alerta a = new Alerta();
                a.setAnimal(animal);
                a.setTipo("SALUD");
                a.setDisparador(DisparadorAlerta.SALUD);
                a.setSeveridad(severidad);
                a.setEstado(estado);
                a.setTsGeneracion(tsGeneracion);
                a.setValorUmbral(0.5);
                a.setValorDetectado(0.72);
                a.setConfianzaModelo(0.72f);
                a.setTsConfirmacion(tsConfirmacion);
                a.setUsuarioConfirmacion(confirmador);
                a.setDesvioDetectado(true);
                alertaRepository.save(a);
        }

        private void crearAlertaCeloConAtencion(Animal animal, LocalDateTime tsGeneracion,
                        EstadoAlerta estado, int horasAtencion, Usuario confirmador) {
                LocalDateTime tsConf = horasAtencion > 0 ? tsGeneracion.plusHours(horasAtencion) : null;
                crearAlertaCelo(animal, tsGeneracion, estado, tsConf, 6, 18, confirmador);
        }

        private void crearAlertaSaludConAtencion(Animal animal, LocalDateTime tsGeneracion,
                        EstadoAlerta estado, int horasAtencion, Usuario confirmador) {
                LocalDateTime tsConf = horasAtencion > 0 ? tsGeneracion.plusHours(horasAtencion) : null;
                crearAlertaSalud(animal, tsGeneracion, estado, tsConf, SeveridadAlerta.MEDIA, confirmador);
        }

        private void crearAlertaPerimetro(Animal animal, LocalDateTime tsGeneracion,
                        EstadoAlerta estado, LocalDateTime tsConfirmacion, Usuario confirmador) {
                Alerta a = new Alerta();
                a.setAnimal(animal);
                a.setTipo("Fuera de Perímetro");
                a.setDisparador(DisparadorAlerta.FUERA_DE_PERIMETRO);
                a.setSeveridad(SeveridadAlerta.ALTA);
                a.setEstado(estado);
                a.setTsGeneracion(tsGeneracion);
                a.setLatitud(LAT_CENTRO + 0.006);
                a.setLongitud(LON_CENTRO + 0.006);
                a.setValorUmbral(1.0);
                a.setValorDetectado(0.0);
                a.setAccionRecomendada(
                                "Verificar ubicación del animal en el mapa y retornarlo al perímetro autorizado.");
                a.setPosibleCausa("Animal fuera del perímetro asignado");
                a.setTsConfirmacion(tsConfirmacion);
                a.setUsuarioConfirmacion(confirmador);
                alertaRepository.save(a);
        }

        // =========================================================================
        // Helpers reproductivos
        // =========================================================================

        private void sembrarEstadoReproductivo(Animal animal, TipoReproductivo estado,
                        OrigenEstado origen, Usuario usuario,
                        LocalDate fechaUltParto, LocalDateTime fechaRegistro) {
                EstadoReproductivo e = new EstadoReproductivo();
                e.setAnimal(animal);
                e.setEstado(estado);
                e.setOrigen(origen);
                e.setUsuarioRegistrador(usuario);
                e.setFechaUltParto(fechaUltParto);
                e.setFechaRegistro(fechaRegistro);
                estadoReproductivoRepository.save(e);
        }

        /**
         * PARIDA hace {@code dias} dias, para las vacas de escenarios no reproductivos (sin senal, bateria, salud,
         * calibracion): sin ningun estado el detector de anestro cuenta desde nacimiento + 1 anio y las alerta.
         */
        private void sembrarPartoReciente(Animal animal, Usuario vet, LocalDateTime ahora, int dias) {
                sembrarEstadoReproductivo(animal, TipoReproductivo.PARIDA, OrigenEstado.MANUAL, vet,
                                LocalDate.now().minusDays(dias), ahora.minusDays(dias));
        }

        private void sembrarGestante(Animal animal, Usuario vet,
                        LocalDate fechaInseminacion, LocalDateTime fechaRegistro) {
                EstadoReproductivo e = new EstadoReproductivo();
                e.setAnimal(animal);
                e.setEstado(TipoReproductivo.GESTANTE);
                e.setOrigen(OrigenEstado.MANUAL);
                e.setUsuarioRegistrador(vet);
                e.setFechaUltInseminacion(fechaInseminacion);
                e.setFechaRegistro(fechaRegistro);
                estadoReproductivoRepository.save(e);
        }

        private void sembrarGestanteSinIa(Animal animal, Usuario vet, LocalDateTime fechaRegistro) {
                EstadoReproductivo e = new EstadoReproductivo();
                e.setAnimal(animal);
                e.setEstado(TipoReproductivo.GESTANTE);
                e.setOrigen(OrigenEstado.MANUAL);
                e.setUsuarioRegistrador(vet);
                e.setFechaRegistro(fechaRegistro);
                estadoReproductivoRepository.save(e);
        }

        private void sembrarInseminada(Animal animal, Usuario interventor, LocalDateTime fechaHora) {
                EstadoReproductivo e = new EstadoReproductivo();
                e.setAnimal(animal);
                e.setEstado(TipoReproductivo.INSEMINADA);
                e.setOrigen(OrigenEstado.MANUAL);
                e.setUsuarioRegistrador(interventor);
                e.setFechaUltInseminacion(fechaHora.toLocalDate());
                e.setFechaRegistro(fechaHora);
                estadoReproductivoRepository.save(e);
        }

        private void sembrarInseminacionDesdeIntervencion(Animal animal, Usuario interventor,
                        LocalDateTime fechaHora) {
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
                intervencion.setDescripcion("IATF - inseminacion a tiempo fijo");
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

        private void sembrarRevisionConEstadoSalud(Animal animal, Usuario vet,
                        TipoEstadoSalud estadoSalud, LocalDateTime fechaHora) {
                String notas = "Revision clinica: leve cojera en miembro posterior, se deja en observacion.";
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

        private void sembrarAlertaAnestro(Establecimiento est, Animal animal, EstadoAlerta estado,
                        long diasSinCiclo, LocalDateTime tsGeneracion, LocalDateTime tsConfirmacion, Usuario usuario) {
                Alerta alerta = new Alerta();
                alerta.setAnimal(animal);
                alerta.setTipo(Alerta.TIPO_ANESTRO);
                alerta.setDisparador(DisparadorAlerta.CELO);
                alerta.setSeveridad(est.getSeveridadAnestro());
                alerta.setEstado(estado);
                alerta.setTsGeneracion(tsGeneracion);
                alerta.setValorUmbral((double) est.getDiasDeteccionAnestro());
                alerta.setValorDetectado((double) diasSinCiclo);
                alerta.setAccionRecomendada("Se recomienda revision veterinaria. El animal lleva "
                                + diasSinCiclo + " dias sin ciclo registrado.");
                alerta.setPosibleCausa("Anestro prolongado (>" + est.getDiasDeteccionAnestro() + " dias)");
                alerta.setTsConfirmacion(tsConfirmacion);
                alerta.setUsuarioConfirmacion(usuario);
                alertaRepository.save(alerta);
        }

        // =========================================================================
        // Helpers de pesadas
        // =========================================================================

        private void sembrarPesadas(Animal animal, double[][] pesadas) {
                for (double[] p : pesadas) {
                        RegistroPeso r = new RegistroPeso();
                        r.setAnimal(animal);
                        r.setFecha(LocalDate.now().minusDays((long) p[0]));
                        r.setValorKg(p[1]);
                        r.setUnidad("kg");
                        r.setFechaRegistro(r.getFecha().atTime(9, 0));
                        registroPesoRepository.save(r);
                }
        }

        // =========================================================================
        // Helpers de alertas sanitarias (SerieComparativaBuilder)
        // =========================================================================

        private Alerta crearAlertaSanitaria(Animal animal, SeveridadAlerta severidad, EstadoAlerta estado,
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
                return alertaRepository.save(alerta);
        }

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
                for (double d : arr)
                        list.add(d);
                return list;
        }

        private List<Telemetria> ventanaSanitaria(Caravana caravana, LocalDateTime hasta,
                        int moveState, double pasoLat) {
                final int muestras = 288;
                final int tickSegundos = 300;
                List<Telemetria> puntos = new ArrayList<>(muestras);
                double lat = LAT_CENTRO;
                double lon = LON_CENTRO;
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

        private void sembrarDiagnostico(Alerta alerta, Usuario vet, String diagnostico,
                        String indicaciones, LocalDateTime fecha) {
                Diagnostico d = new Diagnostico();
                d.setAlerta(alerta);
                d.setAnimal(alerta.getAnimal());
                d.setVeterinario(vet);
                d.setDiagnostico(diagnostico);
                d.setIndicaciones(indicaciones);
                d.setFecha(fecha);
                diagnosticoRepository.save(d);
        }

        // =========================================================================
        // Historial de celo (24 h, patron de alta actividad -- compatible simulador)
        // =========================================================================

        /** destino: donde termina el historial (en el rodeo), sin saltos; ver anclarFinal. */
        private void sembrarHistorialCelo(Caravana caravana, double[] destino) {
                final int SAMPLES = 288;
                final int TICK_S = 300;
                final double SPEED_BASE = 0.000018;
                final double MULT_MIN = 2.5;
                final double MULT_MAX = 4.0;
                final double TURN_STD = 1.1;
                final int MS_MIN = 7;
                final int MS_MAX = 9;

                Random rnd = new Random();
                double lat = LAT_CENTRO - 0.001;
                double lon = LON_CENTRO - 0.001;
                double heading = rnd.nextDouble() * 2 * Math.PI;
                double[][] posiciones = new double[SAMPLES][];
                for (int i = 0; i < SAMPLES; i++) {
                        double speedMult = MULT_MIN + rnd.nextDouble() * (MULT_MAX - MULT_MIN);
                        heading += rnd.nextGaussian() * TURN_STD;
                        double speed = SPEED_BASE * speedMult;
                        lat += speed * Math.cos(heading);
                        lon += speed * Math.sin(heading);
                        posiciones[i] = new double[] { lat, lon };
                }
                anclarFinal(posiciones, destino);

                double bateria = 88.0;
                LocalDateTime ultimaFecha = LocalDateTime.now().minusMinutes(3);
                for (int i = 0; i < SAMPLES; i++) {
                        LocalDateTime fecha = ultimaFecha.minusSeconds((long) (SAMPLES - 1 - i) * TICK_S);
                        bateria -= 0.01;
                        int moveState = MS_MIN + rnd.nextInt(MS_MAX - MS_MIN + 1);
                        Telemetria t = new Telemetria();
                        t.setCaravana(caravana);
                        t.setFechaHora(fecha);
                        t.setLatitud(posiciones[i][0]);
                        t.setLongitud(posiciones[i][1]);
                        t.setMoveState(moveState);
                        t.setBateria(bateria);
                        t.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO);
                        t.setEstadoInferenciaSalud(EstadoInferencia.COMPLETADO);
                        telemetriaRepository.save(t);
                }
        }

        // =========================================================================
        // Historial de #208 Margarita (Acto 4: desvio sanitario)
        // =========================================================================

        /**
         * 8 dias a 5 min (2304 filas), la cadencia que espera ml-salud: los primeros 7 son la
         * calibracion del perfil (RF03, >= 2016 puntos; SaludDetectorStrategyImpl calibra con los 7
         * dias desde el primer dato) y las ultimas 24 hs (288 puntos, la ventana que evalua) traen la
         * caida de ~40 % de actividad: moveState 1-3 en vez de 2-5 (media 2.0 contra 3.5). Validado
         * contra ml-salud con estas mismas filas: 24 hs normales no alertan y estas alertan con severidad
         * MEDIA; contra ml-celo, P(celo) ~0.1. Todo queda COMPLETADO: la alerta sale recien cuando
         * simulador_salud.py manda los primeros puntos PENDIENTES (Acto 4, Paso 4.1).
         * El historial termina en destino, apartada del rodeo: su ultima hora ya cuenta como "fuera del
         * grupo" para ml-geo, que alerta a los 15 min seguidos afuera. No hay caminata al final de la
         * ventana: alejarse ~150 m dentro de las 24 hs evaluadas le suma tanto desplazamiento que
         * ml-salud deja de ver la caida (verificado en Docker).
         * Semilla fija para que la prediccion sea la misma en cada arranque.
         */
        private void sembrarHistorialMargarita(Caravana caravana, double[] destino, LocalDateTime ultimaFecha) {
                final int TICK_S = 300;
                final int MUESTRAS = 8 * 288;
                final int MUESTRAS_ENFERMA = 288;

                Random rnd = new Random(208L);
                double[][] posiciones = caminataPastoreo(rnd, LAT_CENTRO + 0.001, LON_CENTRO - 0.001, MUESTRAS,
                                PASO_PASTOREO_15_MIN * TICK_S / 900);
                anclarFinal(posiciones, destino);

                double bateria = 79.0;
                List<Telemetria> historial = new ArrayList<>(MUESTRAS);
                for (int i = 0; i < MUESTRAS; i++) {
                        boolean enferma = i >= MUESTRAS - MUESTRAS_ENFERMA;
                        bateria = Math.max(5.0, bateria - 0.002);
                        Telemetria t = new Telemetria();
                        t.setCaravana(caravana);
                        t.setFechaHora(ultimaFecha.minusSeconds((long) (MUESTRAS - 1 - i) * TICK_S));
                        t.setLatitud(posiciones[i][0]);
                        t.setLongitud(posiciones[i][1]);
                        t.setMoveState(enferma ? 1 + rnd.nextInt(3) : 2 + rnd.nextInt(4));
                        t.setBateria(bateria);
                        t.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO);
                        t.setEstadoInferenciaSalud(EstadoInferencia.COMPLETADO);
                        historial.add(t);
                }
                telemetriaRepository.saveAll(historial);
        }

        // =========================================================================
        // Historial de pastoreo (mapa de calor H0025)
        // =========================================================================

        private static final double PASO_PASTOREO_15_MIN = 0.000054;

        /** destino: donde termina el historial (en el rodeo); null = donde lo deje la caminata. */
        private void sembrarHistorialPastoreo(Caravana caravana, double latInicial, double lonInicial,
                        double bateriaInicial, int dias, LocalDateTime ultimaFecha, double[] destino) {
                final int TICK_S = 900;
                final int MS_MIN = 2;
                final int MS_MAX = 5;

                Random rnd = new Random();
                int muestras = (int) ((long) dias * 24 * 3600 / TICK_S);
                double[][] posiciones = caminataPastoreo(rnd, latInicial, lonInicial, muestras, PASO_PASTOREO_15_MIN);
                if (destino != null) {
                        anclarFinal(posiciones, destino);
                }

                double bateria = bateriaInicial;
                List<Telemetria> historial = new ArrayList<>(muestras);
                for (int i = 0; i < muestras; i++) {
                        bateria = Math.max(5.0, bateria - 0.005);
                        Telemetria t = new Telemetria();
                        t.setCaravana(caravana);
                        t.setFechaHora(ultimaFecha.minusSeconds((long) (muestras - 1 - i) * TICK_S));
                        t.setLatitud(posiciones[i][0]);
                        t.setLongitud(posiciones[i][1]);
                        t.setMoveState(MS_MIN + rnd.nextInt(MS_MAX - MS_MIN + 1));
                        t.setBateria(bateria);
                        t.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO);
                        t.setEstadoInferenciaSalud(EstadoInferencia.COMPLETADO);
                        historial.add(t);
                }
                telemetriaRepository.saveAll(historial);
        }

        /**
         * Historia de una vaca del rodeo: DIAS_HISTORIAL_RODEO dias a 5 min con el mismo pastoreo que
         * simulador.py (reparto de moveState, paso fijo, giro y vuelta al centro a mas de 35 m), para que el
         * perfil de salud que arma el worker con estos datos no vea como desvio el pastoreo en vivo. Validado
         * offline contra ml-salud: 0 alertas en 1200 ventanas (con el pastoreo anterior, moveState 2-5 y
         * vuelta a la aguada, daba ~1 %). La actividad varia +-15 % entre dias, como en un animal real, y el
         * centro del rodeo recorre el potrero en la semana (mapa de calor) hasta terminar en LAT_RODEO.
         * El ultimo punto queda PENDIENTE de salud: en el primer ciclo despues del seed el worker arma el
         * perfil y la vaca deja de figurar "en calibracion". Semilla por vaca: misma historia en cada arranque.
         */
        private void sembrarHistorialRodeo(Caravana caravana, int numero, double bateriaFinal,
                        LocalDateTime ultimaFecha) {
                final int tickSegundos = 300;
                final int muestras = DIAS_HISTORIAL_RODEO * MUESTRAS_POR_DIA;

                Random rnd = new Random(1000L + numero);
                double[] actividadDia = new double[DIAS_HISTORIAL_RODEO];
                for (int d = 0; d < actividadDia.length; d++) {
                        actividadDia[d] = 0.85 + rnd.nextDouble() * 0.30;
                }
                double[] centro = centroDelRodeo(0, muestras);
                double lat = centro[0];
                double lon = centro[1];
                double rumbo = rnd.nextDouble() * 2 * Math.PI;
                double bateria = Math.min(100.0, bateriaFinal + muestras * 0.002);

                List<Telemetria> historial = new ArrayList<>(muestras);
                for (int i = 0; i < muestras; i++) {
                        centro = centroDelRodeo(i, muestras);
                        if (metros(lat, lon, centro[0], centro[1]) > RADIO_COHESION_M) {
                                rumbo = Math.atan2(centro[1] - lon, centro[0] - lat) + rnd.nextGaussian() * GIRO_SIMULADOR;
                        } else {
                                rumbo += rnd.nextGaussian() * GIRO_SIMULADOR;
                        }
                        lat += PASO_SIMULADOR * Math.cos(rumbo);
                        lon += PASO_SIMULADOR * Math.sin(rumbo);
                        bateria = Math.max(bateriaFinal, bateria - 0.002);

                        Telemetria t = new Telemetria();
                        t.setCaravana(caravana);
                        t.setFechaHora(ultimaFecha.minusSeconds((long) (muestras - 1 - i) * tickSegundos));
                        t.setLatitud(lat);
                        t.setLongitud(lon);
                        t.setMoveState(moveStateSimulador(rnd, actividadDia[i / MUESTRAS_POR_DIA]));
                        t.setBateria(bateria);
                        t.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO);
                        t.setEstadoInferenciaSalud(i == muestras - 1 ? EstadoInferencia.PENDIENTE
                                        : EstadoInferencia.COMPLETADO);
                        historial.add(t);
                }
                telemetriaRepository.saveAll(historial);
        }

        /**
         * Como simulador.py: 35 % reposo (0-1), 60 % normal (2-6) y 5 % picos (7-9). actividad mueve la parte
         * de reposo para que un dia sea mas o menos activo que otro.
         */
        private static int moveStateSimulador(Random rnd, double actividad) {
                double r = rnd.nextDouble();
                if (r < Math.min(0.9, 0.35 / actividad)) {
                        return rnd.nextInt(2);
                }
                if (r < 0.95) {
                        return 2 + rnd.nextInt(5);
                }
                return 7 + rnd.nextInt(3);
        }

        /** Centro del rodeo en el paso i de n: una vuelta y media en espiral por el potrero hasta LAT_RODEO. */
        private static double[] centroDelRodeo(int i, int n) {
                double f = i / (double) n;
                double angulo = 2 * Math.PI * f * 1.5;
                double radio = 0.0025 * (1 - f);
                return new double[] { LAT_RODEO + radio * Math.cos(angulo), LON_RODEO + radio * Math.sin(angulo) };
        }

        private static double metros(double lat1, double lon1, double lat2, double lon2) {
                return Math.hypot((lat2 - lat1) * 111_320, (lon2 - lon1) * 111_320 * Math.cos(Math.toRadians(lat1)));
        }

        /** Pastoreo alrededor de la aguada: paso fijo, rumbo aleatorio y vuelta a la aguada si se aleja. */
        private static double[][] caminataPastoreo(Random rnd, double latInicial, double lonInicial, int muestras,
                        double paso) {
                final double TURN_STD = 0.9;
                final double PROB_AGUADA = 0.12;
                final double RADIO = 0.004;
                final double AGUADA_LAT = -34.9440;
                final double AGUADA_LON = -59.2250;

                double lat = latInicial;
                double lon = lonInicial;
                double heading = rnd.nextDouble() * 2 * Math.PI;
                double[][] posiciones = new double[muestras][];
                for (int i = 0; i < muestras; i++) {
                        double dLat = AGUADA_LAT - lat;
                        double dLon = AGUADA_LON - lon;
                        if (Math.hypot(dLat, dLon) > RADIO || rnd.nextDouble() < PROB_AGUADA) {
                                heading = Math.atan2(dLon, dLat);
                        } else {
                                heading += rnd.nextGaussian() * TURN_STD;
                        }
                        lat += paso * Math.cos(heading);
                        lon += paso * Math.sin(heading);
                        posiciones[i] = new double[] { lat, lon };
                }
                return posiciones;
        }

        /**
         * Corre la caminata para que termine exactamente en destino, repartiendo la diferencia en
         * partes iguales entre todos los pasos: la forma del recorrido (y el mapa de calor) casi no
         * cambia y no aparece ningun salto entre filas consecutivas, que ml-celo leeria como un
         * desplazamiento anomalo (celo falso).
         */
        private static void anclarFinal(double[][] posiciones, double[] destino) {
                int n = posiciones.length;
                double dLat = destino[0] - posiciones[n - 1][0];
                double dLon = destino[1] - posiciones[n - 1][1];
                for (int i = 0; i < n; i++) {
                        double f = (i + 1) / (double) n;
                        posiciones[i][0] += dLat * f;
                        posiciones[i][1] += dLon * f;
                }
        }

        /** Punto al azar dentro del circulo del rodeo (LAT_RODEO, RADIO_RODEO), uniforme en area. */
        private static double[] posicionEnRodeo(Random rnd) {
                double r = RADIO_RODEO * Math.sqrt(rnd.nextDouble());
                double ang = rnd.nextDouble() * 2 * Math.PI;
                return new double[] { LAT_RODEO + r * Math.cos(ang), LON_RODEO + r * Math.sin(ang) };
        }

        // =========================================================================
        // Helper: crear animal con caravana
        // =========================================================================

        /**
         * Numero de caravana oficial de 3 cifras para la vaca VIGIA-ARG-{n}: salteado para que no parezca
         * correlativo y sin pisar los del guion (#104, #208 y las vaquillonas #301 a #305).
         */
        private static String numeroManejo(int n) {
                return String.valueOf(100 + (n * 37) % 900);
        }

        private Animal crearAnimal(Establecimiento est, RodeoLote lote,
                        String idOficial, String alias, String raza,
                        LocalDate fechaNacimiento, String codigoCaravana, LocalDate fechaAlta) {

                Caravana caravana = new Caravana();
                caravana.setCodigo(codigoCaravana);
                caravana.setDevEui(codigoCaravana);
                caravana.setActivo(true);
                caravana.setEstablecimiento(est);
                caravana.setEstadoSensor(EstadoSensor.VINCULADO);
                caravanaRepository.save(caravana);

                Animal a = new Animal(est, lote);
                a.setIdCaravanaOficial(idOficial);
                a.setAlias(alias);
                a.setRaza(raza);
                a.setEstadoActual(EstadoAnimal.ACTIVO);
                a.setFechaNacimiento(fechaNacimiento);
                a.setFechaAlta(fechaAlta);
                a.setCaravana(caravana);
                animalRepository.save(a);
                return a;
        }

        // =========================================================================
        // Helper: crear usuario
        // =========================================================================

        private Usuario buildUsuario(String nombre, String apellido, String email,
                        String password, Rol rol) {
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
