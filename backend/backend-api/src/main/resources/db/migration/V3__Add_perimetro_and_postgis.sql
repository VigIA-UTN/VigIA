CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE perimetro (
    id BIGSERIAL PRIMARY KEY,
    establecimiento_id BIGINT NOT NULL REFERENCES establecimientos(id) ON DELETE CASCADE,
    nombre VARCHAR(255) NOT NULL,
    descripcion VARCHAR(500),
    activo BOOLEAN NOT NULL DEFAULT true,
    area GEOMETRY(POLYGON, 4326) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_perimetro_area ON perimetro USING GIST (area);
CREATE INDEX idx_perimetro_establecimiento ON perimetro (establecimiento_id);

CREATE TABLE perimetro_lote (
    perimetro_id BIGINT NOT NULL REFERENCES perimetro(id) ON DELETE CASCADE,
    rodeo_lote_id BIGINT NOT NULL REFERENCES rodeo_lote(id) ON DELETE CASCADE,
    PRIMARY KEY (perimetro_id, rodeo_lote_id)
);
