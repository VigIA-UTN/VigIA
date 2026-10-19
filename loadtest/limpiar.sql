-- Borra lo que creó seed.sql (prefijos carga- y LT-) y todo lo que la carga generó sobre esos animales.
-- Uso: docker exec -i vigia-db psql -U vigia_user -d vigia < loadtest/limpiar.sql
\set ON_ERROR_STOP on
\timing on

BEGIN;
-- estados_salud, diagnosticos e intervenciones bloquean el DELETE con un trigger salvo con este bypass (V12).
SET LOCAL vigia.bypass_immutable_triggers = 'true';

CREATE TEMP TABLE carga_est ON COMMIT DROP AS
SELECT id FROM establecimientos WHERE nombre LIKE 'Carga %';
CREATE TEMP TABLE carga_caravana ON COMMIT DROP AS
SELECT id FROM caravanas WHERE codigo LIKE 'LT-%';
CREATE TEMP TABLE carga_animal ON COMMIT DROP AS
SELECT id FROM animales WHERE caravana_id IN (SELECT id FROM carga_caravana)
                           OR establecimiento_id IN (SELECT id FROM carga_est);
CREATE TEMP TABLE carga_alerta ON COMMIT DROP AS
SELECT id FROM alertas WHERE animal_id IN (SELECT id FROM carga_animal);

-- 1. Lo que cuelga de alertas y animales.
DELETE FROM alerta_animales_aislados WHERE alerta_id IN (SELECT id FROM carga_alerta)
                                        OR animal_id IN (SELECT id FROM carga_animal);
DELETE FROM diagnosticos WHERE alerta_id IN (SELECT id FROM carga_alerta) OR animal_id IN (SELECT id FROM carga_animal);
DELETE FROM intervenciones WHERE alerta_id IN (SELECT id FROM carga_alerta) OR animal_id IN (SELECT id FROM carga_animal);
DELETE FROM alertas WHERE id IN (SELECT id FROM carga_alerta);
DELETE FROM estados_reproductivos WHERE animal_id IN (SELECT id FROM carga_animal);
DELETE FROM estados_salud WHERE animal_id IN (SELECT id FROM carga_animal);
DELETE FROM registros_cohesion WHERE animal_id IN (SELECT id FROM carga_animal)
                                  OR establecimiento_id IN (SELECT id FROM carga_est);
DELETE FROM registros_peso WHERE animal_id IN (SELECT id FROM carga_animal);
DELETE FROM perfiles_referencia WHERE animal_id IN (SELECT id FROM carga_animal);
DELETE FROM telemetria WHERE caravana_id IN (SELECT id FROM carga_caravana);

-- 2. Animales y caravanas.
DELETE FROM animales WHERE id IN (SELECT id FROM carga_animal);
DELETE FROM caravanas WHERE id IN (SELECT id FROM carga_caravana);

-- 3. Rodeos y perímetros.
DELETE FROM perimetro_lote WHERE rodeo_lote_id IN (SELECT id FROM rodeo_lote WHERE establecimiento_id IN (SELECT id FROM carga_est));
DELETE FROM perimetro WHERE establecimiento_id IN (SELECT id FROM carga_est);
DELETE FROM rodeo_lote WHERE establecimiento_id IN (SELECT id FROM carga_est);

-- 4. Empleados, establecimientos y usuarios.
DELETE FROM invitaciones WHERE establecimiento_id IN (SELECT id FROM carga_est);
DELETE FROM empleado WHERE establecimiento_id IN (SELECT id FROM carga_est);
DELETE FROM establecimientos WHERE id IN (SELECT id FROM carga_est);
DELETE FROM usuarios WHERE email LIKE 'carga-%@vigia.dev';

-- 5. Corridas de jobs que quedaron STARTED porque el worker se paró o se reinició a mitad: con el worker parado ya no
-- corren, y si no, observar.py las mostraría en curso para siempre.
UPDATE batch_job_execution SET status = 'ABANDONED', exit_code = 'ABANDONED',
       end_time = now() AT TIME ZONE 'America/Argentina/Buenos_Aires'
WHERE end_time IS NULL;
COMMIT;
