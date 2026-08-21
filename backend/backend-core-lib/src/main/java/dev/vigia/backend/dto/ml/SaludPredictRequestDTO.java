package dev.vigia.backend.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request para POST /api/internal/ml/salud/predict. A diferencia de
 * celo, no manda población/evento puntual: manda la ventana a evaluar
 * MÁS el perfil de referencia individual ya calculado (no telemetría
 * cruda de calibración -- ver PerfilCalibracionRequestDTO, que es un
 * endpoint aparte).
 *
 * `perfil` se modela como Map<String, Object> a propósito, no como una
 * clase con un campo por cada estadística: la forma exacta del perfil
 * vive en ml-salud (features_salud.py), no acá. El backend lo trata
 * como un blob opaco que recibe de /perfil, persiste, y reenvía tal
 * cual -- así evitamos mantener una copia en Java de una estructura que
 * puede cambiar del lado de Python sin que el backend necesite saber
 * qué significa cada campo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaludPredictRequestDTO {
    private List<PuntoTelemetriaDTO> serie;
    private Map<String, Object> perfil;
}
