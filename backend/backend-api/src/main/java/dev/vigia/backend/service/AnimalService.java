package dev.vigia.backend.service;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import dev.vigia.backend.dto.AnimalIndicadoresSaludResponse;
import dev.vigia.backend.dto.AnimalMapaResponse;
import dev.vigia.backend.dto.AnimalMetricaResponse;
import dev.vigia.backend.dto.AnimalRequest;
import dev.vigia.backend.dto.AnimalResponse;
import dev.vigia.backend.dto.EstadoReproductivoRequest;
import dev.vigia.backend.dto.EstadoReproductivoResponse;
import dev.vigia.backend.dto.CargaMasivaResponse;
import dev.vigia.backend.dto.CargaMasivaEstadoResponse;
import dev.vigia.backend.dto.IntervencionResponse;
import dev.vigia.backend.dto.RegistroPesoRequest;
import dev.vigia.backend.dto.RegistroPesoResponse;
import dev.vigia.backend.dto.EstadoSaludRequest;
import dev.vigia.backend.dto.EstadoSaludResponse;

public interface AnimalService {
        AnimalResponse cargarAnimal(String emailProductor, AnimalRequest request);

        AnimalResponse editarAnimal(Long animalId, String emailProductor, AnimalRequest request);

        void desactivarAnimal(Long animalId, String emailProductor);

        AnimalResponse getAnimalById(Long animalId, String emailProductor);

        List<AnimalResponse> getAllAnimales();

        List<AnimalResponse> listarAnimales(String emailUsuario, Long establecimientoId);

        List<AnimalMapaResponse> getPosicionesMapa(String emailUsuario, Long establecimientoId);

        EstadoReproductivoResponse registrarEstadoReproductivo(Long animalId, String emailProductor,
                        EstadoReproductivoRequest request);

        EstadoReproductivoResponse editarEstadoReproductivo(Long animalId, Long estadoId, String emailProductor,
                        EstadoReproductivoRequest request);

        List<EstadoReproductivoResponse> listarEstadosReproductivos(Long animalId, String emailUsuario);

        List<IntervencionResponse> listarIntervenciones(Long animalId, String emailUsuario);

        EstadoSaludResponse registrarEstadoSalud(Long animalId, String emailProductor,
                        EstadoSaludRequest request);

        List<EstadoSaludResponse> listarEstadosSalud(Long animalId, String emailUsuario);

        CargaMasivaEstadoResponse cargarEstadosMasivo(MultipartFile file, Long establecimientoId, String emailProductor)
                        throws IOException;

        CargaMasivaResponse cargarAnimalesExcel(MultipartFile file, Long establecimientoId, String emailProductor)
                        throws IOException;

        List<AnimalMetricaResponse> getMetricasAnimal(Long animalId, String emailUsuario);

        RegistroPesoResponse registrarPeso(Long animalId, String email, RegistroPesoRequest request);

        RegistroPesoResponse editarPeso(Long animalId, Long pesoId, String email, RegistroPesoRequest request);

        void eliminarPeso(Long animalId, Long pesoId, String email);

        List<RegistroPesoResponse> listarHistorialPesos(Long animalId, String email);

        AnimalIndicadoresSaludResponse getIndicadoresSalud(Long animalId, String emailProductor);

        AnimalResponse vincularCaravana(Long animalId, Long caravanaId, String emailUsuario);

        AnimalResponse desvincularCaravana(Long animalId, String emailUsuario);
}
