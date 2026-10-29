-- Semilla de carga: :clientes productores con :sensores animales cada uno, ya en estado estable:
-- caravana asignada hace 30 días, perfil de salud calculado ayer y 24 h de historial.
-- Las últimas :pendientes_h horas quedan pendientes para celo y salud: lo que procesa un ciclo de Railway (cada 6 h),
-- que arranca al reiniciar el worker (ver README). Con -v pendientes_h=0 todo queda procesado. Se borra con limpiar.sql.
-- Uso (ver README): cat loadtest/trayectoria.sql loadtest/seed.sql | docker exec -i vigia-db psql ... -v perfil=...
\if :{?clientes}
\else
\set clientes 500
\endif
\if :{?sensores}
\else
\set sensores 50
\endif
\if :{?pendientes_h}
\else
\set pendientes_h 6
\endif
-- Para medir salud con uplinks perdidos: días de historial, fracción de filas de telemetría
-- que se pierden al azar y fracción de animales sin perfil de salud (con -v dias=8 les toca su primera calibración).
\if :{?dias}
\else
\set dias 1
\endif
\if :{?perdida}
\else
\set perdida 0
\endif
\if :{?sin_perfil}
\else
\set sin_perfil 0
\endif
\set ON_ERROR_STOP on
\timing on

BEGIN;
-- fecha_hora y demás timestamps son hora local, como LocalDateTime.now() en la API y el worker.
SET LOCAL TIME ZONE 'America/Argentina/Buenos_Aires';

-- Contraseña productor123: el mismo hash que el productor de DataInitializer.
INSERT INTO usuarios (nombre, apellido, email, password_hash, rol, activo, fecha_alta)
SELECT 'Carga', lpad(n::text, 3, '0'), format('carga-%s@vigia.dev', lpad(n::text, 3, '0')),
       '$2a$10$Cko9bG5PYgfx86cRolZk1.BRfBnhb5bNMy.AhKYuYUz1eTK9g63p2', 'PRODUCTOR', 1, current_date
FROM generate_series(1, :clientes) n;

-- Una grilla de 25 columnas cada 0,05° (~5 km), en la pampa.
INSERT INTO establecimientos (nombre, latitud, longitud, activo, fecha_alta)
SELECT format('Carga %s', lpad(n::text, 3, '0')), -35 - (n - 1) / 25 * 0.05, -60 - (n - 1) % 25 * 0.05, 1, current_date
FROM generate_series(1, :clientes) n;

CREATE TEMP TABLE carga ON COMMIT DROP AS
SELECT n, u.id AS usuario_id, e.id AS establecimiento_id, e.latitud, e.longitud
FROM generate_series(1, :clientes) n
JOIN usuarios u ON u.email = format('carga-%s@vigia.dev', lpad(n::text, 3, '0'))
JOIN establecimientos e ON e.nombre = format('Carga %s', lpad(n::text, 3, '0'));

INSERT INTO empleado (establecimiento_id, usuario_id, activo, fecha_vinculacion)
SELECT establecimiento_id, usuario_id, 1, now() FROM carga;

INSERT INTO rodeo_lote (establecimiento_id, nombre, activo, fecha_alta)
SELECT establecimiento_id, 'Rodeo carga', 1, now() FROM carga;

-- Cuadrado de ~1 km alrededor del establecimiento: el rodeo (trayectoria.sql) queda bien adentro.
INSERT INTO perimetro (establecimiento_id, nombre, activo, area)
SELECT establecimiento_id, 'Perímetro carga', true,
       ST_MakeEnvelope(longitud - 0.0045, latitud - 0.0045, longitud + 0.0045, latitud + 0.0045, 4326)
FROM carga;

INSERT INTO perimetro_lote (perimetro_id, rodeo_lote_id)
SELECT p.id, r.id
FROM carga c
JOIN perimetro p ON p.establecimiento_id = c.establecimiento_id
JOIN rodeo_lote r ON r.establecimiento_id = c.establecimiento_id;

-- LT-00001..LT-25000, :sensores por establecimiento. ultimo_frame_counter NULL: acepta el f_cnt de k6.
INSERT INTO caravanas (codigo, dev_eui, fecha_asignacion, fecha_alta, ultima_senial, activo, estado_sensor,
                       establecimiento_id, bateria)
SELECT x.codigo, x.codigo, current_date - 30, current_date - 30, now(), 1, 'VINCULADO', c.establecimiento_id, 90
FROM carga c
CROSS JOIN generate_series(0, :sensores - 1) d
CROSS JOIN LATERAL (SELECT format('LT-%s', lpad(((c.n - 1) * :sensores + d + 1)::text, 5, '0')) AS codigo) x;

-- d: índice del sensor en su establecimiento, el de trayectoria.sql.
CREATE TEMP TABLE carga_sensor ON COMMIT DROP AS
SELECT k.id AS caravana_id, k.codigo, (substr(k.codigo, 4)::int - 1) % :sensores AS d, r.id AS rodeo_id, c.*
FROM caravanas k
JOIN carga c ON c.establecimiento_id = k.establecimiento_id
JOIN rodeo_lote r ON r.establecimiento_id = c.establecimiento_id
WHERE k.codigo LIKE 'LT-%';

INSERT INTO animales (establecimiento_id, rodeo_lote_id, id_caravana_oficial, alias, fecha_nacimiento,
                      estado_actual, fecha_alta, caravana_id)
SELECT establecimiento_id, rodeo_id, codigo, format('Vaca %s', d + 1), date '2021-01-01', 'ACTIVO',
       current_date - 30, caravana_id
FROM carga_sensor;

-- Preñadas, inseminadas hace 60 días: el job de anestro las recorre igual, pero no les genera alertas (sin esto,
-- con nacimiento en 2021, alertaría a las 25.000).
INSERT INTO estados_reproductivos (animal_id, estado, fecha_ultima_inseminacion, origen, fecha_registro)
SELECT a.id, 'GESTANTE', current_date - 60, 'MANUAL', now() - interval '30 days'
FROM animales a JOIN carga_sensor s ON s.caravana_id = a.caravana_id;

-- El perfil de perfil.json para todos (ver README): ninguno queda en calibración ni recalibra durante la corrida.
INSERT INTO perfiles_referencia (animal_id, perfil_json, fecha_calculo)
SELECT a.id, :'perfil', now() - interval '1 day'
FROM animales a JOIN carga_sensor s ON s.caravana_id = a.caravana_id
WHERE random() >= :sin_perfil;

-- :dias días (24 h por defecto) cada 5 minutos (ticks k) hasta ahora, sobre la misma trayectoria, menos la fracción
-- :perdida de las filas. Geo, que corre cada 15 min, ya procesó todo; celo y salud tienen pendientes las últimas
-- :pendientes_h horas (12 ticks por hora).
INSERT INTO telemetria (fecha_hora, caravana_id, latitud, longitud, move_state, bateria,
                        estado_inferencia_celo, estado_inferencia_salud, estado_inferencia_geo)
SELECT to_timestamp(k * 300)::timestamp, s.caravana_id,
       s.latitud + pg_temp.carga_dlat(s.d, k), s.longitud + pg_temp.carga_dlon(s.d, k),
       pg_temp.carga_move_state(), 90, x.estado, x.estado, 'COMPLETADO'
FROM carga_sensor s
CROSS JOIN (SELECT floor(extract(epoch FROM now()) / 300)::int AS ahora) a
CROSS JOIN generate_series(a.ahora - (:dias * 288 - 1), a.ahora) k
CROSS JOIN LATERAL (SELECT CASE WHEN k > a.ahora - :pendientes_h * 12 THEN 'PENDIENTE' ELSE 'COMPLETADO' END
                    AS estado) x
WHERE random() >= :perdida;

COMMIT;

ANALYZE usuarios, establecimientos, empleado, rodeo_lote, perimetro, perimetro_lote, caravanas, animales, estados_reproductivos,
        perfiles_referencia, telemetria;

SELECT (SELECT count(*) FROM usuarios WHERE email LIKE 'carga-%') AS usuarios,
       (SELECT count(*) FROM establecimientos WHERE nombre LIKE 'Carga %') AS establecimientos,
       (SELECT count(*) FROM caravanas WHERE codigo LIKE 'LT-%') AS caravanas,
       (SELECT count(*) FROM animales a JOIN caravanas k ON k.id = a.caravana_id WHERE k.codigo LIKE 'LT-%') AS animales,
       (SELECT count(*) FROM perfiles_referencia p JOIN animales a ON a.id = p.animal_id
                                                   JOIN caravanas k ON k.id = a.caravana_id
         WHERE k.codigo LIKE 'LT-%') AS perfiles,
       t.telemetria, t.pendientes
FROM (SELECT count(*) AS telemetria, count(*) FILTER (WHERE t.estado_inferencia_celo = 'PENDIENTE') AS pendientes
      FROM telemetria t JOIN caravanas k ON k.id = t.caravana_id WHERE k.codigo LIKE 'LT-%') t;
