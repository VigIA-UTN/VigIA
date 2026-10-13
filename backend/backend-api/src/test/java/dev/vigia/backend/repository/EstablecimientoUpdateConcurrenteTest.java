package dev.vigia.backend.repository;

import dev.vigia.backend.model.Establecimiento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La pantalla de configuración guarda anestro y aislamiento con dos PUT en paralelo.
 * Cada request lee el establecimiento antes de que el otro escriba; el UPDATE de uno
 * no debe pisar la columna que cambió el otro.
 */
@SpringBootTest
@DisplayName("Establecimiento — updates concurrentes sobre columnas distintas")
class EstablecimientoUpdateConcurrenteTest {

    @MockBean
    private ConnectionFactory connectionFactory;

    @Autowired private EntityManagerFactory emf;
    @Autowired private EstablecimientoRepository establecimientoRepository;

    @Test
    @DisplayName("PUT /anestro y PUT /aislamiento simultáneos conservan ambos cambios")
    void anestroYAislamientoSimultaneos_noSePisan() {
        Establecimiento nuevo = new Establecimiento();
        nuevo.setNombre("Concurrencia");
        nuevo.setFechaAlta(LocalDate.now());
        Long id = establecimientoRepository.save(nuevo).getId();

        EntityManager reqAnestro = emf.createEntityManager();
        EntityManager reqAislamiento = emf.createEntityManager();
        try {
            // Ambos requests leen antes de que cualquiera escriba.
            Establecimiento enAnestro = reqAnestro.find(Establecimiento.class, id);
            Establecimiento enAislamiento = reqAislamiento.find(Establecimiento.class, id);

            reqAnestro.getTransaction().begin();
            enAnestro.setDiasDeteccionAnestro(60);
            reqAnestro.getTransaction().commit();

            reqAislamiento.getTransaction().begin();
            enAislamiento.setUmbralAislamientoM(150.0);
            reqAislamiento.getTransaction().commit();

            Establecimiento guardado = establecimientoRepository.findById(id).orElseThrow();
            assertEquals(60, guardado.getDiasDeteccionAnestro());
            assertEquals(150.0, guardado.getUmbralAislamientoM());
        } finally {
            reqAnestro.close();
            reqAislamiento.close();
            establecimientoRepository.deleteById(id);
        }
    }
}
