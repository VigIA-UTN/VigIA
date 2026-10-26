ALTER TABLE establecimientos
    ADD COLUMN dias_deteccion_anestro INTEGER NOT NULL DEFAULT 45,
    ADD COLUMN severidad_anestro VARCHAR(10) NOT NULL DEFAULT 'MEDIA';
