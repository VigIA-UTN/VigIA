package dev.vigia.backend.repository;

import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.RegistroCohesion;
import dev.vigia.backend.model.RodeoLote;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class RegistroCohesionRepositoryTest {

    @Autowired
    private RegistroCohesionRepository registroCohesionRepository;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private EstablecimientoRepository establecimientoRepository;

    @Autowired
    private RodeoLoteRepository rodeoLoteRepository;

    @Test
    void testGuardarYConsultarRegistroCohesion() {
        Establecimiento est = new Establecimiento();
        est.setNombre("Establecimiento 1");
        est = establecimientoRepository.save(est);

        RodeoLote lote = new RodeoLote();
        lote.setNombre("Lote A");
        lote.setEstablecimiento(est);
        lote = rodeoLoteRepository.save(lote);

        Animal animal = new Animal(est, lote);
        animal = animalRepository.save(animal);

        LocalDateTime ahora = LocalDateTime.now();

        RegistroCohesion registro = RegistroCohesion.builder()
                .animal(animal)
                .establecimiento(est)
                .fecha(ahora)
                .indiceCohesion(0.85)
                .tiempoFueraGrupoS(120L)
                .distanciaCentroideM(45.2)
                .build();

        RegistroCohesion guardado = registroCohesionRepository.save(registro);
        registroCohesionRepository.flush();

        assertThat(guardado.getId()).isNotNull();

        RegistroCohesion obtenido = registroCohesionRepository.findById(guardado.getId()).orElse(null);
        assertThat(obtenido).isNotNull();
        assertThat(obtenido.getAnimal().getId()).isEqualTo(animal.getId());
        assertThat(obtenido.getEstablecimiento().getId()).isEqualTo(est.getId());
        assertThat(obtenido.getIndiceCohesion()).isEqualTo(0.85);
        assertThat(obtenido.getTiempoFueraGrupoS()).isEqualTo(120L);
        assertThat(obtenido.getDistanciaCentroideM()).isEqualTo(45.2);
    }

    @Test
    void testAislamientoPorEstablecimiento() {
        Establecimiento est1 = new Establecimiento();
        est1.setNombre("Establecimiento 1");
        est1 = establecimientoRepository.save(est1);

        Establecimiento est2 = new Establecimiento();
        est2.setNombre("Establecimiento 2");
        est2 = establecimientoRepository.save(est2);

        RodeoLote lote1 = new RodeoLote();
        lote1.setNombre("Lote 1");
        lote1.setEstablecimiento(est1);
        lote1 = rodeoLoteRepository.save(lote1);

        RodeoLote lote2 = new RodeoLote();
        lote2.setNombre("Lote 2");
        lote2.setEstablecimiento(est2);
        lote2 = rodeoLoteRepository.save(lote2);

        Animal animal1 = animalRepository.save(new Animal(est1, lote1));
        Animal animal2 = animalRepository.save(new Animal(est2, lote2));

        LocalDateTime base = LocalDateTime.now();

        registroCohesionRepository.save(RegistroCohesion.builder()
                .animal(animal1)
                .establecimiento(est1)
                .fecha(base.minusMinutes(10))
                .indiceCohesion(0.9)
                .tiempoFueraGrupoS(0L)
                .build());

        registroCohesionRepository.save(RegistroCohesion.builder()
                .animal(animal1)
                .establecimiento(est1)
                .fecha(base)
                .indiceCohesion(0.8)
                .tiempoFueraGrupoS(50L)
                .build());

        registroCohesionRepository.save(RegistroCohesion.builder()
                .animal(animal2)
                .establecimiento(est2)
                .fecha(base)
                .indiceCohesion(0.7)
                .tiempoFueraGrupoS(300L)
                .build());

        registroCohesionRepository.flush();

        // Verificar aislamiento
        final Long est1Id = est1.getId();
        List<RegistroCohesion> registrosEst1 = registroCohesionRepository.findByEstablecimientoId(est1Id);
        assertThat(registrosEst1).hasSize(2);
        assertThat(registrosEst1).allMatch(r -> r.getEstablecimiento().getId().equals(est1Id));

        List<RegistroCohesion> registrosEst2 = registroCohesionRepository.findByEstablecimientoId(est2.getId());
        assertThat(registrosEst2).hasSize(1);
        assertThat(registrosEst2.get(0).getAnimal().getId()).isEqualTo(animal2.getId());

        // Rango de fechas
        List<RegistroCohesion> enRango = registroCohesionRepository
                .findByEstablecimientoIdAndAnimalIdAndFechaBetweenOrderByFechaAsc(
                        est1.getId(), animal1.getId(), base.minusMinutes(15), base.plusMinutes(5));
        assertThat(enRango).hasSize(2);
        assertThat(enRango.get(0).getIndiceCohesion()).isEqualTo(0.9);
        assertThat(enRango.get(1).getIndiceCohesion()).isEqualTo(0.8);
    }
}
