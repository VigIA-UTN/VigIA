CREATE TABLE registros_cohesion (
    id                   BIGSERIAL PRIMARY KEY,
    animal_id            BIGINT NOT NULL REFERENCES animales(id),
    establecimiento_id   BIGINT NOT NULL REFERENCES establecimientos(id),
    fecha                TIMESTAMP NOT NULL,
    indice_cohesion      DOUBLE PRECISION NOT NULL,
    tiempo_fuera_grupo_s BIGINT NOT NULL DEFAULT 0,
    distancia_centroide_m DOUBLE PRECISION
);

CREATE INDEX idx_registros_cohesion_establecimiento ON registros_cohesion (establecimiento_id);
CREATE INDEX idx_registros_cohesion_animal_fecha ON registros_cohesion (animal_id, fecha);
