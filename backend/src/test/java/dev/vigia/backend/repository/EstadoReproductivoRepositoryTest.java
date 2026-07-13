package dev.vigia.backend.repository;

import dev.vigia.backend.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class EstadoReproductivoRepositoryTest {

    @Autowired
    private EstadoReproductivoRepository repository;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EstablecimientoRepository establecimientoRepository;

    @Autowired
    private RodeoLoteRepository rodeoLoteRepository;

    @Test
    @SuppressWarnings("null")
    public void testSaveAndRetrieve() {
        Establecimiento est = new Establecimiento();
        est.setNombre("Establecimiento de Prueba");
        est = establecimientoRepository.save(est);

        RodeoLote lote = new RodeoLote();
        lote.setNombre("Lote 1");
        lote.setEstablecimiento(est);
        lote = rodeoLoteRepository.save(lote);

        Animal animal = new Animal(est, lote);
        animal = animalRepository.save(animal);

        Usuario user = new Usuario();
        user.setEmail("test@test.com");
        user.setNombre("Test");
        user = usuarioRepository.save(user);

        EstadoReproductivo estado = new EstadoReproductivo();
        estado.setAnimal(animal);
        estado.setUsuarioRegistrador(user);
        estado.setEstado(TipoReproductivo.GESTANTE);
        estado.setOrigen(OrigenEstado.MANUAL);
        estado.setFechaRegistro(LocalDateTime.now());
        estado.setFechaUltParto(LocalDate.now().minusMonths(2));
        estado.setFechaUltInseminacion(LocalDate.now().minusDays(10));

        EstadoReproductivo saved = repository.save(estado);
        repository.flush(); // To ensure it hits the DB

        EstadoReproductivo retrieved = repository.findById(saved.getId()).orElse(null);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getEstado()).isEqualTo(TipoReproductivo.GESTANTE);
        assertThat(retrieved.getOrigen()).isEqualTo(OrigenEstado.MANUAL);
        assertThat(retrieved.getAnimal().getId()).isEqualTo(animal.getId());
    }
}
