-- Agrega estado_inferencia_geo a la tabla telemetria para tracking del batch de ML-GEO
ALTER TABLE telemetria ADD COLUMN IF NOT EXISTS estado_inferencia_geo VARCHAR(50) DEFAULT 'PENDIENTE';
