-- Toda lectura de telemetría es por caravana y rango de tiempo: la última fila (webhook, detalle del
-- animal), las ventanas de 24 h (celo, salud, geo) y de 7 días (calibración de salud, mapa de calor).
-- Sin este índice cada una recorre la tabla entera, que crece ~7M filas por día con 25.000 sensores.
CREATE INDEX idx_telemetria_caravana_fecha ON telemetria (caravana_id, fecha_hora DESC);

-- El webhook busca el animal de la caravana en cada uplink (findByCaravanaId).
CREATE INDEX idx_animales_caravana ON animales (caravana_id);
