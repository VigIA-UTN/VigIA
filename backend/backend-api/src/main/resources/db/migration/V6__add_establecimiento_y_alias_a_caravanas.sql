-- El código de caravana oficial (codigo) todavía no existe para un sensor
-- recién registrado por un Administrador, antes de que se vincule a un
-- animal/caravana física. Deja de ser obligatorio para permitir ese alta.
ALTER TABLE caravanas
    ALTER COLUMN codigo DROP NOT NULL;

ALTER TABLE caravanas
    ADD COLUMN alias VARCHAR(255),
    ADD COLUMN establecimiento_id BIGINT REFERENCES establecimientos(id);

-- Backfill: las caravanas ya vinculadas a un animal heredan el
-- establecimiento de ese animal, para que el listado por establecimiento
-- (GET /api/v1/sensores) también las incluya.
UPDATE caravanas c
SET establecimiento_id = a.establecimiento_id
FROM animales a
WHERE a.caravana_id = c.id
  AND c.establecimiento_id IS NULL;
