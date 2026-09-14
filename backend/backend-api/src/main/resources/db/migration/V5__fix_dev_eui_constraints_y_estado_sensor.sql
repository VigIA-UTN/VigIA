-- Backfill de dev_eui para filas existentes sin valor, con un placeholder único por fila,
-- de forma que se puedan aplicar los constraints NOT NULL y UNIQUE sin fallar.
UPDATE caravanas
SET dev_eui = 'LEGACY-' || id
WHERE dev_eui IS NULL;

ALTER TABLE caravanas
    ALTER COLUMN dev_eui SET NOT NULL;

ALTER TABLE caravanas
    ADD CONSTRAINT uq_caravanas_dev_eui UNIQUE (dev_eui);

-- V4 dejaba en DISPONIBLE a las caravanas ya vinculadas a un animal; deben quedar en VINCULADO.
UPDATE caravanas
SET estado_sensor = 'VINCULADO'
WHERE id IN (SELECT caravana_id FROM animales WHERE caravana_id IS NOT NULL);
