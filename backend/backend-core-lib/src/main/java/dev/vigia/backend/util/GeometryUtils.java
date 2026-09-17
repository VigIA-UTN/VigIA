package dev.vigia.backend.util;

import dev.vigia.backend.dto.CoordenadaDTO;
import dev.vigia.backend.exception.PoligonoInvalidoException;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;

import java.util.ArrayList;
import java.util.List;

public final class GeometryUtils {

    public static final int SRID_WGS84 = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), SRID_WGS84);

    private GeometryUtils() {
    }

    public static GeometryFactory getGeometryFactory() {
        return GEOMETRY_FACTORY;
    }

    public static Polygon crearPoligono(List<CoordenadaDTO> coordenadas) {
        if (coordenadas == null || coordenadas.size() < 3) {
            throw new PoligonoInvalidoException("Un perímetro requiere al menos 3 coordenadas para formar un área.");
        }

        List<Coordinate> coordsList = new ArrayList<>();
        for (CoordenadaDTO dto : coordenadas) {
            if (dto == null || dto.getLatitud() == null || dto.getLongitud() == null) {
                throw new PoligonoInvalidoException("Las coordenadas no pueden contener valores nulos.");
            }
            // NOTA CRÍTICA: JTS usa (x, y) = (lon, lat)
            coordsList.add(new Coordinate(dto.getLongitud(), dto.getLatitud()));
        }

        // Asegurar cierre del anillo exterior: el primer punto debe ser idéntico al último
        Coordinate primerPunto = coordsList.get(0);
        Coordinate ultimoPunto = coordsList.get(coordsList.size() - 1);
        if (!primerPunto.equals2D(ultimoPunto)) {
            coordsList.add(new Coordinate(primerPunto.x, primerPunto.y));
        }

        if (coordsList.size() < 4) {
            throw new PoligonoInvalidoException("Un polígono cerrado requiere al menos 4 puntos (3 vértices únicos + 1 de cierre).");
        }

        Coordinate[] coordsArray = coordsList.toArray(new Coordinate[0]);
        LinearRing shell;
        try {
            shell = GEOMETRY_FACTORY.createLinearRing(coordsArray);
        } catch (IllegalArgumentException e) {
            throw new PoligonoInvalidoException("Error construyendo el anillo exterior del perímetro: " + e.getMessage(), e);
        }

        Polygon polygon = GEOMETRY_FACTORY.createPolygon(shell);
        validarPoligono(polygon);

        return polygon;
    }

    public static void validarPoligono(Polygon polygon) {
        if (polygon == null) {
            throw new PoligonoInvalidoException("El área del perímetro no puede ser nula.");
        }

        IsValidOp validOp = new IsValidOp(polygon);
        if (!validOp.isValid()) {
            TopologyValidationError err = validOp.getValidationError();
            String errorMsg = (err != null)
                    ? err.getMessage() + " en la coordenada (" + err.getCoordinate().y + ", " + err.getCoordinate().x + ")"
                    : "Polígono inválido o autointersectante";
            throw new PoligonoInvalidoException("El área del perímetro no es válida: " + errorMsg);
        }
    }

    public static List<CoordenadaDTO> extraerCoordenadas(Polygon polygon) {
        if (polygon == null) {
            return new ArrayList<>();
        }

        Coordinate[] coordinates = polygon.getCoordinates();
        List<CoordenadaDTO> dtoList = new ArrayList<>();
        for (Coordinate c : coordinates) {
            // y = latitud, x = longitud
            dtoList.add(new CoordenadaDTO(c.y, c.x));
        }
        return dtoList;
    }

    public static Point crearPunto(double latitud, double longitud) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitud, latitud));
    }
}
