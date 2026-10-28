-- Borra el escenario de escenario.sql (establecimientos 'Fixture %', usuarios *@fixture.vigia.test, caravanas FX-*) y
-- todo lo que los tests o los jobs crearon encima (alertas, estados, cohesión, telemetría, invitaciones). Lo demás de
-- la base queda como estaba. Idempotente: sin escenario cargado no borra nada.
-- Uso (ver README): docker exec -i vigia-db psql -U vigia_user -d vigia -v ON_ERROR_STOP=1 < fixtures/escenario/limpiar.sql
-- SQL plano, sin metacomandos de psql: EscenarioFixture lo corre igual desde los IT.

BEGIN;
-- estados_salud, diagnosticos e intervenciones bloquean el DELETE con un trigger salvo con este bypass (V12, V15).
SET LOCAL vigia.bypass_immutable_triggers = 'true';

CREATE TEMP TABLE fx_est ON COMMIT DROP AS
SELECT id FROM establecimientos WHERE nombre LIKE 'Fixture %';
CREATE TEMP TABLE fx_usuario ON COMMIT DROP AS
SELECT id FROM usuarios WHERE email LIKE '%@fixture.vigia.test';
CREATE TEMP TABLE fx_caravana ON COMMIT DROP AS
SELECT id FROM caravanas WHERE codigo LIKE 'FX-%' OR establecimiento_id IN (SELECT id FROM fx_est);
CREATE TEMP TABLE fx_animal ON COMMIT DROP AS
SELECT id FROM animales WHERE caravana_id IN (SELECT id FROM fx_caravana)
                           OR establecimiento_id IN (SELECT id FROM fx_est);
CREATE TEMP TABLE fx_alerta ON COMMIT DROP AS
SELECT id FROM alertas WHERE animal_id IN (SELECT id FROM fx_animal);

-- 1. Lo que cuelga de alertas y animales.
DELETE FROM alerta_animales_aislados WHERE alerta_id IN (SELECT id FROM fx_alerta)
                                        OR animal_id IN (SELECT id FROM fx_animal);
DELETE FROM diagnosticos WHERE alerta_id IN (SELECT id FROM fx_alerta) OR animal_id IN (SELECT id FROM fx_animal);
DELETE FROM intervenciones WHERE alerta_id IN (SELECT id FROM fx_alerta) OR animal_id IN (SELECT id FROM fx_animal);
DELETE FROM alertas WHERE id IN (SELECT id FROM fx_alerta);
DELETE FROM estados_reproductivos WHERE animal_id IN (SELECT id FROM fx_animal);
DELETE FROM estados_salud WHERE animal_id IN (SELECT id FROM fx_animal);
DELETE FROM registros_cohesion WHERE animal_id IN (SELECT id FROM fx_animal)
                                  OR establecimiento_id IN (SELECT id FROM fx_est);
DELETE FROM registros_peso WHERE animal_id IN (SELECT id FROM fx_animal);
DELETE FROM perfiles_referencia WHERE animal_id IN (SELECT id FROM fx_animal);
DELETE FROM telemetria WHERE caravana_id IN (SELECT id FROM fx_caravana);

-- 2. Animales y caravanas.
DELETE FROM animales WHERE id IN (SELECT id FROM fx_animal);
DELETE FROM caravanas WHERE id IN (SELECT id FROM fx_caravana);

-- 3. Rodeos y perímetros.
DELETE FROM perimetro_lote WHERE rodeo_lote_id IN (SELECT id FROM rodeo_lote WHERE establecimiento_id IN (SELECT id FROM fx_est));
DELETE FROM perimetro WHERE establecimiento_id IN (SELECT id FROM fx_est);
DELETE FROM rodeo_lote WHERE establecimiento_id IN (SELECT id FROM fx_est);

-- 4. Invitaciones, empleados, establecimientos y usuarios.
DELETE FROM invitaciones WHERE establecimiento_id IN (SELECT id FROM fx_est)
                            OR creador_id IN (SELECT id FROM fx_usuario)
                            OR email_destino LIKE '%@fixture.vigia.test';
DELETE FROM empleado WHERE establecimiento_id IN (SELECT id FROM fx_est) OR usuario_id IN (SELECT id FROM fx_usuario);
DELETE FROM establecimientos WHERE id IN (SELECT id FROM fx_est);
DELETE FROM usuarios WHERE id IN (SELECT id FROM fx_usuario);
COMMIT;
