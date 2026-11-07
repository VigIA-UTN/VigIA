-- Una caída de un microservicio ML ya no deja la telemetría en INFERENCIA_PENDIENTE (que ningún batch volvía a
-- leer) sino en PENDIENTE, que el próximo ciclo reprocesa. Las filas que quedaron en el estado viejo vuelven a
-- PENDIENTE. Un solo UPDATE, para recorrer la tabla una vez.
UPDATE telemetria
SET estado_inferencia_celo  = CASE WHEN estado_inferencia_celo  = 'INFERENCIA_PENDIENTE' THEN 'PENDIENTE' ELSE estado_inferencia_celo  END,
    estado_inferencia_salud = CASE WHEN estado_inferencia_salud = 'INFERENCIA_PENDIENTE' THEN 'PENDIENTE' ELSE estado_inferencia_salud END,
    estado_inferencia_geo   = CASE WHEN estado_inferencia_geo   = 'INFERENCIA_PENDIENTE' THEN 'PENDIENTE' ELSE estado_inferencia_geo   END
WHERE 'INFERENCIA_PENDIENTE' IN (estado_inferencia_celo, estado_inferencia_salud, estado_inferencia_geo);
