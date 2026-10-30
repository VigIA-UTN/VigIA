package dev.vigia.backend.service.ml;

import dev.vigia.backend.dto.ml.PuntoTelemetriaDTO;
import dev.vigia.backend.model.Telemetria;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Armado de la ventana de telemetría que se manda a ml-celo y ml-salud: los dos modelos se entrenaron con
 * ventanas de 24 hs a 5 min (SAMPLES_PER_DAY puntos) y los dos servicios rechazan con 422 otro largo.
 */
final class VentanaTelemetria {

    /** 24hs a 5 min: el largo de ventana que exigen ml-celo y ml-salud. */
    static final int SAMPLES_PER_DAY = 288;
    static final int MINUTOS_POR_SLOT = 5;

    private VentanaTelemetria() {
    }

    /** Lecturas con posición y moveState: las demás los ml-* las rechazan con 422. */
    static List<Telemetria> conDatos(List<Telemetria> filas) {
        return filas.stream()
                .filter(t -> t.getLatitud() != null && t.getLongitud() != null && t.getMoveState() != null)
                .collect(Collectors.toList());
    }

    static PuntoTelemetriaDTO punto(Telemetria t) {
        return PuntoTelemetriaDTO.builder()
                .moveState(t.getMoveState())
                .latitud(t.getLatitud())
                .longitud(t.getLongitud())
                .build();
    }

    /**
     * Los puntos a mandar para la ventana [desde, desde + slots × 5 min), o vacío si no alcanzan.
     * Con al menos `slots` lecturas van todas, como siempre. Con menos (uplinks perdidos), una lectura por slot
     * de 5 min (la última si hay varias) y los slots vacíos se rellenan: moveState repite la lectura anterior,
     * como el tag, que solo lo cambia con el heartbeat; la posición va en línea recta entre las lecturas de cada
     * lado del hueco, para no congelar al animal (moverse menos es señal de enfermedad); en los bordes, la
     * lectura más cercana. Vacío si menos de `coberturaMinima` de los slots tiene lectura o si algún hueco,
     * bordes incluidos, dura más de `huecoMaximoMinutos`. Validado contra el modelo de salud con
     * ml-training/salud/check_perdida.py: con 90 % y 1 h, rellenar agrega a lo sumo 0,32 % de falsas alertas.
     */
    static List<PuntoTelemetriaDTO> puntosParaMl(List<Telemetria> filas, LocalDateTime desde, int slots,
            double coberturaMinima, int huecoMaximoMinutos) {
        List<Telemetria> validas = conDatos(filas);
        if (validas.size() >= slots) {
            return validas.stream().map(VentanaTelemetria::punto).collect(Collectors.toList());
        }

        Telemetria[] porSlot = new Telemetria[slots];
        int conLectura = 0;
        for (Telemetria t : validas) { // ascendentes por fechaHora: queda la última de cada slot
            long i = Math.floorDiv(Duration.between(desde, t.getFechaHora()).getSeconds(), MINUTOS_POR_SLOT * 60L);
            if (i >= 0 && i < slots) {
                if (porSlot[(int) i] == null) {
                    conLectura++;
                }
                porSlot[(int) i] = t;
            }
        }
        if (conLectura == 0 || conLectura < coberturaMinima * slots) {
            return List.of();
        }

        List<PuntoTelemetriaDTO> puntos = new ArrayList<>(slots);
        int previo = -1;
        for (int i = 0; i < slots; i++) {
            if (porSlot[i] != null) {
                previo = i;
                puntos.add(punto(porSlot[i]));
                continue;
            }
            int siguiente = i + 1;
            while (siguiente < slots && porSlot[siguiente] == null) {
                siguiente++;
            }
            if ((siguiente - previo - 1) * MINUTOS_POR_SLOT > huecoMaximoMinutos) {
                return List.of();
            }
            Telemetria a = previo >= 0 ? porSlot[previo] : null;
            Telemetria b = siguiente < slots ? porSlot[siguiente] : null;
            if (a == null || b == null) {
                puntos.add(punto(a != null ? a : b));
                continue;
            }
            double f = (double) (i - previo) / (siguiente - previo);
            puntos.add(PuntoTelemetriaDTO.builder()
                    .moveState(a.getMoveState())
                    .latitud(a.getLatitud() + f * (b.getLatitud() - a.getLatitud()))
                    .longitud(a.getLongitud() + f * (b.getLongitud() - a.getLongitud()))
                    .build());
        }
        return puntos;
    }
}
