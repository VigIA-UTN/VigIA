package dev.vigia.backend;

import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.TimeZone;

/**
 * Base de los tests de integración ({@code *IT}, los corre failsafe en {@code mvn verify}): levanta TimescaleDB y
 * RabbitMQ reales, con las mismas imágenes que {@code infra/docker-compose.yml}, y Flyway aplica las migraciones.
 * <p>
 * Los contenedores arrancan una sola vez por JVM y los comparten todos los IT del módulo (Ryuk los baja al final);
 * como las propiedades no cambian, Spring también reutiliza el mismo contexto. Por eso los IT no pueden asumir una
 * base vacía: cada uno crea los datos que necesita.
 * <p>
 * Las tareas programadas (jobs de celo, salud y geo, anestro y archivador) no corren solas: cada IT lanza el job que
 * prueba. Si no, un contexto que quedó en la caché de Spring seguiría corriendo los jobs sobre la base compartida,
 * en medio de los datos de otro test.
 */
@SpringBootTest
@ActiveProfiles("it")
@Import(IntegracionBase.SinTareasProgramadas.class)
public abstract class IntegracionBase {

    static {
        // Como el worker en los contenedores (TZ de infra/docker-compose.yml y Railway): la telemetría se guarda en
        // hora local y el escenario de fixtures se ubica en hora argentina.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
    }

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb-ha:pg16").asCompatibleSubstituteFor("postgres"));

    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3-management").asCompatibleSubstituteFor("rabbitmq"));

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void propiedadesDeContenedores(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    /** Un TaskScheduler que no ejecuta nada: @EnableScheduling registra las tareas, pero nunca se disparan. */
    @TestConfiguration
    static class SinTareasProgramadas {

        @Bean
        TaskScheduler taskScheduler() {
            return Mockito.mock(TaskScheduler.class);
        }
    }
}
