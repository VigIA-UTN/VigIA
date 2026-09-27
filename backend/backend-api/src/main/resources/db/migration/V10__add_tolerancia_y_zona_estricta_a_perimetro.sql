ALTER TABLE perimetro
    ADD COLUMN distancia_tolerancia_metros NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN zona_estricta BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT chk_perimetro_tolerancia_no_negativa CHECK (distancia_tolerancia_metros >= 0);
