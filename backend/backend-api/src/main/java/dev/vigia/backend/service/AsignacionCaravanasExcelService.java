package dev.vigia.backend.service;

import java.util.List;

import dev.vigia.backend.model.Animal;

public interface AsignacionCaravanasExcelService {
    /** Planilla animal → sensor para colocar las caravanas en el campo. Los animales tienen que tener caravana. */
    byte[] generar(String establecimientoNombre, List<Animal> animales);
}
