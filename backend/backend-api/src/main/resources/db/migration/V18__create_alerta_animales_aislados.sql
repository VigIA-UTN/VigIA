-- Episodios de aislamiento social: un animal fuera de su rodeo, listado en una alerta AISLAMIENTO_SOCIAL.
-- El episodio está abierto mientras ts_regreso es NULL.
CREATE TABLE alerta_animales_aislados (
    id                BIGSERIAL PRIMARY KEY,
    alerta_id         BIGINT NOT NULL REFERENCES alertas(id),
    animal_id         BIGINT NOT NULL REFERENCES animales(id),
    segundos_fuera    BIGINT NOT NULL DEFAULT 0,
    distancia_grupo_m DOUBLE PRECISION,
    ts_inicio         TIMESTAMP NOT NULL,
    ts_regreso        TIMESTAMP,
    CONSTRAINT uq_alerta_animal_aislado UNIQUE (alerta_id, animal_id)
);

CREATE INDEX idx_alerta_animales_aislados_alerta ON alerta_animales_aislados (alerta_id);

-- Un solo aislamiento abierto por animal: evita alertas duplicadas por el mismo aislamiento.
CREATE UNIQUE INDEX uq_aislamiento_abierto_por_animal ON alerta_animales_aislados (animal_id)
    WHERE ts_regreso IS NULL;
