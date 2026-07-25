package dev.vigia.backend.service.ml;

import dev.vigia.backend.model.Telemetria;
import java.util.List;

public interface DetectorStrategy {
    void process(List<Telemetria> telemetriaList, String correlationId);
}
