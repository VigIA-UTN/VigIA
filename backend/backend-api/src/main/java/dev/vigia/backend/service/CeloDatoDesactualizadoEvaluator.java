package dev.vigia.backend.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.vigia.backend.model.Alerta;
import dev.vigia.backend.model.DisparadorAlerta;
import dev.vigia.backend.model.EstadoAlerta;

@Component
public class CeloDatoDesactualizadoEvaluator {

    private final long horasUmbral;

    @Autowired
    public CeloDatoDesactualizadoEvaluator(@Value("${vigia.celo.horas-dato-desactualizado:2}") long horasUmbral) {
        this.horasUmbral = horasUmbral;
    }

    public CeloDatoDesactualizadoEvaluator() {
        this(2);
    }

    public long getHorasUmbral() {
        return horasUmbral;
    }

    public boolean aplica(DisparadorAlerta disparador, String tipo, EstadoAlerta estado) {
        return disparador == DisparadorAlerta.CELO
                && !Alerta.TIPO_ANESTRO.equals(tipo)
                && estado == EstadoAlerta.PENDIENTE;
    }

    public boolean esDatoDesactualizado(DisparadorAlerta disparador, String tipo, EstadoAlerta estado,
            LocalDateTime ultimaSenial) {
        if (!aplica(disparador, tipo, estado)) {
            return false;
        }
        if (ultimaSenial == null) {
            return true;
        }
        return Duration.between(ultimaSenial, LocalDateTime.now()).compareTo(Duration.ofHours(horasUmbral)) > 0;
    }
}
