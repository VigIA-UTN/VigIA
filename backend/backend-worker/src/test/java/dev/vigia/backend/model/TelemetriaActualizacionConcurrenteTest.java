package dev.vigia.backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Celo, salud y geo corren en hilos distintos y cada chunk guarda las filas de telemetría que leyó. Si el UPDATE
 * escribe la fila entera, el job que confirma último pisa el estado que el otro acaba de completar con el valor
 * viejo que había leído, y esa fila se vuelve a procesar en el ciclo siguiente.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED) // cada EntityManager confirma por su cuenta, como un chunk
class TelemetriaActualizacionConcurrenteTest {

    @Autowired
    private EntityManagerFactory emf;

    @Test
    void dosJobsQueCompletanSuEstadoEnLaMismaFila_noSePisan() {
        Long id = enTransaccion(em -> {
            Caravana caravana = new Caravana();
            caravana.setCodigo("C-CONC-1");
            caravana.setDevEui("DEV-CONC-1");
            em.persist(caravana);
            Telemetria telemetria = new Telemetria();
            telemetria.setCaravana(caravana);
            telemetria.setFechaHora(LocalDateTime.now());
            em.persist(telemetria);
            return telemetria.getId();
        });

        // Los dos chunks leen la fila antes de que alguno confirme.
        EntityManager celo = emf.createEntityManager();
        EntityManager salud = emf.createEntityManager();
        celo.getTransaction().begin();
        salud.getTransaction().begin();
        Telemetria leidaPorCelo = celo.find(Telemetria.class, id);
        Telemetria leidaPorSalud = salud.find(Telemetria.class, id);

        leidaPorCelo.setEstadoInferenciaCelo(EstadoInferencia.COMPLETADO);
        celo.getTransaction().commit();
        leidaPorSalud.setEstadoInferenciaSalud(EstadoInferencia.COMPLETADO);
        salud.getTransaction().commit();
        celo.close();
        salud.close();

        Telemetria fila = enTransaccion(em -> em.find(Telemetria.class, id));
        assertEquals(EstadoInferencia.COMPLETADO, fila.getEstadoInferenciaCelo());
        assertEquals(EstadoInferencia.COMPLETADO, fila.getEstadoInferenciaSalud());
        assertEquals(EstadoInferencia.PENDIENTE, fila.getEstadoInferenciaGeo());
    }

    private <T> T enTransaccion(Function<EntityManager, T> trabajo) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            T resultado = trabajo.apply(em);
            em.getTransaction().commit();
            return resultado;
        } finally {
            em.close();
        }
    }
}
