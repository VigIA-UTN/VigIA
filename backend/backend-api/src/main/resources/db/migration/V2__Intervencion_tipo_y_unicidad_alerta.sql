ALTER TABLE intervenciones
    ADD COLUMN tipo VARCHAR(50) NOT NULL DEFAULT 'OTRO';

ALTER TABLE intervenciones
    ALTER COLUMN tipo DROP DEFAULT;

ALTER TABLE intervenciones
    ADD CONSTRAINT uq_intervenciones_alerta_id UNIQUE (alerta_id);
