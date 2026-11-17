-- Borra la demo E2E (establecimiento 'Tambo La Esperanza') y todo lo que se creó encima durante un ensayo: lote
-- Cuarentena, vaquillonas, perímetros, alertas, intervenciones, diagnósticos, invitaciones y usuarios que solo
-- pertenecen a la demo (Pedro incluido). Los demás establecimientos y sus datos no se tocan.
--
-- Lo corre DataInitializer con el perfil "demo" antes de sembrar (reintenta si choca con el worker). A mano:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c "SET vigia.demo_emails_extra = 'pedro@gmail.com'" \
--        -c "BEGIN" -f limpiar_demo.sql -c "COMMIT"
-- Prueba en seco: lo mismo con ROLLBACK en vez de COMMIT (muestra qué borraría y deshace todo).
--
-- Guardas: si algo de la demo está mezclado con datos reales, aborta con RAISE EXCEPTION y no borra nada.
--   - Más de un establecimiento con el nombre de la demo (no se sabe cuál es).
--   - Un usuario fijo de la demo (juan, carlos, laura o el de vigia.demo_emails_extra) es empleado de un
--     establecimiento real.
--   - Una caravana VIGIA-* o un animal de la demo pertenece a un establecimiento real.
--   - Al final falta alguna fila real que estaba al principio.
-- Usuarios: se borran los fijos y los que solo son empleados de la demo (los invitados en vivo). Un usuario real
-- que además esté en la demo solo pierde ese vínculo. Un ADMINISTRADOR nunca se borra.
--
-- Corre en READ COMMITTED, la del worker: con REPEATABLE READ, cualquier fila de la demo que el worker actualizara
-- durante la limpieza (telemetría procesada, perfiles) la hacía fallar con "could not serialize access".
--
-- SQL plano sin metacomandos de psql y sin BEGIN/COMMIT: la transacción la abre quien lo corre.

-- estados_salud, diagnosticos, intervenciones y alertas resueltas bloquean el DELETE salvo con este bypass (V12, V15, V16).
SET LOCAL vigia.bypass_immutable_triggers = 'true';

DO $$
BEGIN
    IF (SELECT count(*) FROM establecimientos WHERE nombre = 'Tambo La Esperanza') > 1 THEN
        RAISE EXCEPTION 'limpiar_demo: hay más de un establecimiento "Tambo La Esperanza"; no se sabe cuál es el de la demo';
    END IF;
END $$;

CREATE TEMP TABLE demo_est ON COMMIT DROP AS
SELECT id FROM establecimientos WHERE nombre = 'Tambo La Esperanza';

-- Los fijos del seed más los de vigia.demo_emails_extra (separados por coma): el mail con el que Pedro recibe la
-- invitación en vivo. Ver el README de demo/ para pasarlo.
CREATE TEMP TABLE demo_email_fijo ON COMMIT DROP AS
SELECT email FROM unnest(ARRAY['juan@laesperanza.com', 'carlos@laesperanza.com', 'laura@vet.com']) AS email
UNION
SELECT lower(trim(e)) FROM unnest(string_to_array(coalesce(current_setting('vigia.demo_emails_extra', true), ''), ',')) AS e
WHERE trim(e) <> '';

DO $$
DECLARE
    v_email TEXT;
BEGIN
    SELECT u.email INTO v_email
    FROM usuarios u
    JOIN empleado e ON e.usuario_id = u.id
    WHERE lower(u.email) IN (SELECT email FROM demo_email_fijo)
      AND (e.establecimiento_id IS NULL OR e.establecimiento_id NOT IN (SELECT id FROM demo_est))
    LIMIT 1;
    IF v_email IS NOT NULL THEN
        RAISE EXCEPTION 'limpiar_demo: el usuario de demo % es empleado de un establecimiento real', v_email;
    END IF;
END $$;

-- Usuarios a borrar: los fijos, y los que tienen al menos un vínculo con la demo y ninguno fuera de ella.
CREATE TEMP TABLE demo_usuario ON COMMIT DROP AS
SELECT id FROM usuarios WHERE lower(email) IN (SELECT email FROM demo_email_fijo) AND rol IS DISTINCT FROM 'ADMINISTRADOR'
UNION
SELECT u.id
FROM usuarios u
WHERE u.rol IS DISTINCT FROM 'ADMINISTRADOR'
  AND EXISTS (SELECT 1 FROM empleado e WHERE e.usuario_id = u.id AND e.establecimiento_id IN (SELECT id FROM demo_est))
  AND NOT EXISTS (SELECT 1 FROM empleado e WHERE e.usuario_id = u.id
                    AND (e.establecimiento_id IS NULL OR e.establecimiento_id NOT IN (SELECT id FROM demo_est)));

DO $$
DECLARE
    v_codigo TEXT;
BEGIN
    SELECT codigo INTO v_codigo FROM caravanas
    WHERE (codigo LIKE 'VIGIA-%' OR dev_eui LIKE 'VIGIA-%')
      AND establecimiento_id IS NOT NULL AND establecimiento_id NOT IN (SELECT id FROM demo_est)
    LIMIT 1;
    IF v_codigo IS NOT NULL THEN
        RAISE EXCEPTION 'limpiar_demo: la caravana % es de la demo pero está en un establecimiento real', v_codigo;
    END IF;
END $$;

CREATE TEMP TABLE demo_caravana ON COMMIT DROP AS
SELECT id FROM caravanas
WHERE establecimiento_id IN (SELECT id FROM demo_est)
   OR ((codigo LIKE 'VIGIA-%' OR dev_eui LIKE 'VIGIA-%') AND establecimiento_id IS NULL);

CREATE TEMP TABLE demo_animal ON COMMIT DROP AS
SELECT id FROM animales
WHERE establecimiento_id IN (SELECT id FROM demo_est)
   OR caravana_id IN (SELECT id FROM demo_caravana);

DO $$
DECLARE
    v_id BIGINT;
BEGIN
    SELECT id INTO v_id FROM animales
    WHERE id IN (SELECT id FROM demo_animal)
      AND establecimiento_id IS NOT NULL AND establecimiento_id NOT IN (SELECT id FROM demo_est)
    LIMIT 1;
    IF v_id IS NOT NULL THEN
        RAISE EXCEPTION 'limpiar_demo: el animal % tiene una caravana de la demo pero está en un establecimiento real', v_id;
    END IF;
END $$;

-- Bloquea el establecimiento, las caravanas y los animales de la demo hasta el COMMIT: desde acá, la telemetría,
-- alertas o registros de cohesión que el worker quiera colgar de ellos esperan y después fallan por la FK, en vez de
-- aparecer a mitad de la limpieza y frenar el DELETE de animales o caravanas.
DO $$
BEGIN
    PERFORM 1 FROM establecimientos WHERE id IN (SELECT id FROM demo_est) FOR UPDATE;
    PERFORM 1 FROM caravanas WHERE id IN (SELECT id FROM demo_caravana) FOR UPDATE;
    PERFORM 1 FROM animales WHERE id IN (SELECT id FROM demo_animal) FOR UPDATE;
END $$;

-- Después del bloqueo: ya no se pueden sumar alertas a los animales de la demo.
CREATE TEMP TABLE demo_alerta ON COMMIT DROP AS
SELECT id FROM alertas WHERE animal_id IN (SELECT id FROM demo_animal);

-- Las filas reales que hay ahora, para verificar al final que siguen todas. Por id y no por conteo: la ingesta real
-- sigue escribiendo mientras corre la limpieza.
CREATE TEMP TABLE real_ids ON COMMIT DROP AS
SELECT 'establecimientos' AS tabla, id FROM establecimientos WHERE id NOT IN (SELECT id FROM demo_est)
UNION ALL SELECT 'usuarios', id FROM usuarios WHERE id NOT IN (SELECT id FROM demo_usuario)
UNION ALL SELECT 'empleado', id FROM empleado
          WHERE establecimiento_id IS NULL OR establecimiento_id NOT IN (SELECT id FROM demo_est)
UNION ALL SELECT 'rodeo_lote', id FROM rodeo_lote WHERE establecimiento_id NOT IN (SELECT id FROM demo_est)
UNION ALL SELECT 'perimetro', id FROM perimetro WHERE establecimiento_id NOT IN (SELECT id FROM demo_est)
UNION ALL SELECT 'caravanas', id FROM caravanas WHERE id NOT IN (SELECT id FROM demo_caravana)
UNION ALL SELECT 'animales', id FROM animales WHERE id NOT IN (SELECT id FROM demo_animal)
UNION ALL SELECT 'alertas', id FROM alertas WHERE id NOT IN (SELECT id FROM demo_alerta)
UNION ALL SELECT 'estados_reproductivos', id FROM estados_reproductivos WHERE animal_id NOT IN (SELECT id FROM demo_animal)
UNION ALL SELECT 'intervenciones', id FROM intervenciones WHERE animal_id NOT IN (SELECT id FROM demo_animal)
UNION ALL SELECT 'diagnosticos', id FROM diagnosticos WHERE animal_id NOT IN (SELECT id FROM demo_animal)
UNION ALL SELECT 'registros_peso', id FROM registros_peso WHERE animal_id NOT IN (SELECT id FROM demo_animal);

DO $$
BEGIN
    RAISE NOTICE 'limpiar_demo: se borran % establecimiento(s), % usuario(s), % caravana(s), % animal(es) y % alerta(s)',
        (SELECT count(*) FROM demo_est), (SELECT count(*) FROM demo_usuario), (SELECT count(*) FROM demo_caravana),
        (SELECT count(*) FROM demo_animal), (SELECT count(*) FROM demo_alerta);
END $$;

-- 1. Lo que cuelga de alertas y animales.
DELETE FROM alerta_animales_aislados WHERE alerta_id IN (SELECT id FROM demo_alerta)
                                        OR animal_id IN (SELECT id FROM demo_animal);
DELETE FROM diagnosticos WHERE alerta_id IN (SELECT id FROM demo_alerta) OR animal_id IN (SELECT id FROM demo_animal);
DELETE FROM intervenciones WHERE alerta_id IN (SELECT id FROM demo_alerta) OR animal_id IN (SELECT id FROM demo_animal);
DELETE FROM alertas WHERE id IN (SELECT id FROM demo_alerta);
DELETE FROM estados_reproductivos WHERE animal_id IN (SELECT id FROM demo_animal);
DELETE FROM estados_salud WHERE animal_id IN (SELECT id FROM demo_animal);
DELETE FROM registros_cohesion WHERE animal_id IN (SELECT id FROM demo_animal)
                                  OR establecimiento_id IN (SELECT id FROM demo_est);
DELETE FROM registros_peso WHERE animal_id IN (SELECT id FROM demo_animal);
DELETE FROM perfiles_referencia WHERE animal_id IN (SELECT id FROM demo_animal);
DELETE FROM telemetria WHERE caravana_id IN (SELECT id FROM demo_caravana);

-- 2. Animales y caravanas.
DELETE FROM animales WHERE id IN (SELECT id FROM demo_animal);
DELETE FROM caravanas WHERE id IN (SELECT id FROM demo_caravana);

-- 3. Rodeos y perímetros.
DELETE FROM perimetro_lote WHERE rodeo_lote_id IN (SELECT id FROM rodeo_lote WHERE establecimiento_id IN (SELECT id FROM demo_est));
DELETE FROM perimetro WHERE establecimiento_id IN (SELECT id FROM demo_est);
DELETE FROM rodeo_lote WHERE establecimiento_id IN (SELECT id FROM demo_est);

-- 4. Invitaciones, empleados, establecimiento y usuarios.
DELETE FROM invitaciones WHERE establecimiento_id IN (SELECT id FROM demo_est)
                            OR creador_id IN (SELECT id FROM demo_usuario)
                            OR lower(email_destino) IN (SELECT email FROM demo_email_fijo);
DELETE FROM empleado WHERE establecimiento_id IN (SELECT id FROM demo_est) OR usuario_id IN (SELECT id FROM demo_usuario);
DELETE FROM establecimientos WHERE id IN (SELECT id FROM demo_est);
DELETE FROM usuarios WHERE id IN (SELECT id FROM demo_usuario);

-- 5. Todas las filas reales del principio tienen que seguir estando.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT tabla, count(*) AS faltan
        FROM real_ids x
        WHERE CASE tabla
            WHEN 'establecimientos' THEN NOT EXISTS (SELECT 1 FROM establecimientos t WHERE t.id = x.id)
            WHEN 'usuarios' THEN NOT EXISTS (SELECT 1 FROM usuarios t WHERE t.id = x.id)
            WHEN 'empleado' THEN NOT EXISTS (SELECT 1 FROM empleado t WHERE t.id = x.id)
            WHEN 'rodeo_lote' THEN NOT EXISTS (SELECT 1 FROM rodeo_lote t WHERE t.id = x.id)
            WHEN 'perimetro' THEN NOT EXISTS (SELECT 1 FROM perimetro t WHERE t.id = x.id)
            WHEN 'caravanas' THEN NOT EXISTS (SELECT 1 FROM caravanas t WHERE t.id = x.id)
            WHEN 'animales' THEN NOT EXISTS (SELECT 1 FROM animales t WHERE t.id = x.id)
            WHEN 'alertas' THEN NOT EXISTS (SELECT 1 FROM alertas t WHERE t.id = x.id)
            WHEN 'estados_reproductivos' THEN NOT EXISTS (SELECT 1 FROM estados_reproductivos t WHERE t.id = x.id)
            WHEN 'intervenciones' THEN NOT EXISTS (SELECT 1 FROM intervenciones t WHERE t.id = x.id)
            WHEN 'diagnosticos' THEN NOT EXISTS (SELECT 1 FROM diagnosticos t WHERE t.id = x.id)
            WHEN 'registros_peso' THEN NOT EXISTS (SELECT 1 FROM registros_peso t WHERE t.id = x.id)
        END
        GROUP BY tabla
    LOOP
        RAISE EXCEPTION 'limpiar_demo: faltan % fila(s) reales de %; se deshace todo', r.faltan, r.tabla;
    END LOOP;
END $$;
