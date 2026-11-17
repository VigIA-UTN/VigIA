-- Borra las alertas FUERA_DE_PERIMETRO de las últimas 24 hs de un establecimiento (el de los sensores reales),
-- con sus intervenciones y diagnósticos. Sirve para que la alerta en vivo salga aunque se haya ensayado ese mismo
-- día: el worker no genera una alerta de perímetro nueva si el animal tuvo otra en las últimas 6 hs, aunque esté
-- resuelta (vigia.geofence.cooldown-hours). La ventana es de 24 hs y no de 6 para no depender de la zona horaria
-- en que se guardó ts_generacion.
--
-- Uso (el nombre exacto del establecimiento, entre comillas simples):
--   psql "$DATABASE_URL" -1 -v ON_ERROR_STOP=1 -v est='UTN' -f demo/limpiar_alertas_perimetro.sql
-- Si el nombre no coincide con exactamente un establecimiento, aborta sin borrar nada.

SET LOCAL vigia.bypass_immutable_triggers = 'true';

CREATE TEMP TABLE est_sensores ON COMMIT DROP AS
SELECT id FROM establecimientos WHERE nombre = :'est';

DO $$
BEGIN
    IF (SELECT count(*) FROM est_sensores) <> 1 THEN
        RAISE EXCEPTION 'limpiar_alertas_perimetro: el nombre coincide con % establecimientos, tiene que ser 1',
            (SELECT count(*) FROM est_sensores);
    END IF;
END $$;

CREATE TEMP TABLE alerta_borrar ON COMMIT DROP AS
SELECT a.id
FROM alertas a
JOIN animales an ON an.id = a.animal_id
WHERE an.establecimiento_id IN (SELECT id FROM est_sensores)
  AND a.disparador = 'FUERA_DE_PERIMETRO'
  AND a.ts_generacion > now() - interval '24 hours';

DO $$
BEGIN
    RAISE NOTICE 'limpiar_alertas_perimetro: se borran % alerta(s)', (SELECT count(*) FROM alerta_borrar);
END $$;

DELETE FROM alerta_animales_aislados WHERE alerta_id IN (SELECT id FROM alerta_borrar);
DELETE FROM diagnosticos WHERE alerta_id IN (SELECT id FROM alerta_borrar);
DELETE FROM intervenciones WHERE alerta_id IN (SELECT id FROM alerta_borrar);
DELETE FROM alertas WHERE id IN (SELECT id FROM alerta_borrar);
