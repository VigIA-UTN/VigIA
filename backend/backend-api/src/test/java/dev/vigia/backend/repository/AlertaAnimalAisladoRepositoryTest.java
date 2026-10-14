package dev.vigia.backend.repository;

import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.AlertaAnimalAislado;
import dev.vigia.backend.model.Animal;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.Establecimiento;
import dev.vigia.backend.model.EstadoAlerta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AlertaAnimalAisladoRepositoryTest {

    @Autowired
    private AlertaAnimalAisladoRepository repository;

    @Autowired
    private AlertaRepository alertaRepository;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private EstablecimientoRepository establecimientoRepository;

    @Test
    @SuppressWarnings("null")
    void episodiosAbiertosDelEstablecimiento_yAnimalesDeUnaAlertaPorTiempoFuera() {
        Establecimiento est = establecimiento("Est 1");
        Establecimiento otro = establecimiento("Est 2");
        Animal a1 = animalRepository.save(new Animal(est, null));
        Animal a2 = animalRepository.save(new Animal(est, null));
        Animal a3 = animalRepository.save(new Animal(otro, null));
        Alerta alerta = alerta(a1);

        AlertaAnimalAislado abierto = repository.save(episodio(alerta, a1, 600L, null));
        AlertaAnimalAislado cerrado = repository.save(episodio(alerta, a2, 1200L, LocalDateTime.now()));
        repository.save(episodio(alerta(a3), a3, 300L, null));
        repository.flush();

        assertThat(repository.findByAnimal_Establecimiento_IdAndTsRegresoIsNull(est.getId()))
                .extracting(AlertaAnimalAislado::getId)
                .containsExactly(abierto.getId());
        assertThat(repository.findByAlerta_IdOrderBySegundosFueraDesc(alerta.getId()))
                .extracting(AlertaAnimalAislado::getId)
                .containsExactly(cerrado.getId(), abierto.getId());
    }

    private Establecimiento establecimiento(String nombre) {
        Establecimiento est = new Establecimiento();
        est.setNombre(nombre);
        return establecimientoRepository.save(est);
    }

    private Alerta alerta(Animal animal) {
        Alerta alerta = new Alerta();
        alerta.setAnimal(animal);
        alerta.setTipo("AISLAMIENTO_SOCIAL");
        alerta.setDisparador(DisparadorAlerta.AISLAMIENTO_SOCIAL);
        alerta.setEstado(EstadoAlerta.PENDIENTE);
        alerta.setTsGeneracion(LocalDateTime.now());
        alerta.setValorUmbral(100.0);
        return alertaRepository.save(alerta);
    }

    private static AlertaAnimalAislado episodio(Alerta alerta, Animal animal, Long segundosFuera,
            LocalDateTime tsRegreso) {
        return AlertaAnimalAislado.builder()
                .alerta(alerta)
                .animal(animal)
                .segundosFuera(segundosFuera)
                .distanciaGrupoM(250.0)
                .tsInicio(LocalDateTime.now())
                .tsRegreso(tsRegreso)
                .build();
    }
}
