ALTER TABLE caravanas
    ADD COLUMN dev_eui VARCHAR(255),
    ADD COLUMN ultimo_frame_counter INTEGER,
    ADD COLUMN estado_sensor VARCHAR(50) DEFAULT 'DISPONIBLE';

-- Actualizar caravanas existentes que ya estén asociadas a un animal
UPDATE caravanas
SET estado_sensor = 'DISPONIBLE'
WHERE id IN (SELECT caravana_id FROM animales WHERE caravana_id IS NOT NULL);
