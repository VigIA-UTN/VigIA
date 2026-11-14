-- Escenario conocido para las pruebas de aislamiento, RBAC y E2E: dos establecimientos (Fixture A y Fixture B) con la
-- misma forma, un usuario por rol y un productor en los dos (H0035). Qué hay y para qué, en README.md; los usuarios y
-- contraseñas, en credenciales.json.
-- Uso (ver README): cat fixtures/escenario/limpiar.sql fixtures/escenario/escenario.sql | docker exec -i vigia-db psql ...
-- Siempre después de limpiar.sql: los emails y códigos de caravana son únicos y una segunda carga fallaría.
-- SQL plano, sin metacomandos de psql ni bloques $$: EscenarioFixture lo corre igual desde los IT.
--
-- Determinista salvo por la hora: todo se ubica relativo al momento de la carga (las alertas pendientes, la ventana
-- de 7 días de calibración y el dato desactualizado dependen de "ahora"). Posiciones y moveState salen de fórmulas,
-- sin random.

BEGIN;
-- fecha_hora y demás timestamps son hora local, como LocalDateTime.now() en la API y el worker.
SET LOCAL TIME ZONE 'America/Argentina/Buenos_Aires';

CREATE TEMP TABLE fx_ref ON COMMIT DROP AS
SELECT date_trunc('minute', localtimestamp) AS ahora;

-- Usuarios. Contraseña de todos: la de credenciales.json (bcrypt, cost 10).
INSERT INTO usuarios (nombre, apellido, email, password_hash, rol, activo, fecha_alta)
SELECT u.nombre, u.apellido, u.email, '$2a$10$oofRzCx3mDx/NMokFdFnJuzlTnA1ebuZ2hXgjxld8JFV1DEONbNLm', u.rol, 1,
       current_date - 30
FROM (VALUES ('Admin', 'Fixture', 'admin@fixture.vigia.test', 'ADMINISTRADOR'),
             ('Productor', 'A', 'productor.a@fixture.vigia.test', 'PRODUCTOR'),
             ('Veterinario', 'A', 'veterinario.a@fixture.vigia.test', 'VETERINARIO'),
             ('Campero', 'A', 'campero.a@fixture.vigia.test', 'CAMPERO'),
             ('Productor', 'B', 'productor.b@fixture.vigia.test', 'PRODUCTOR'),
             ('Veterinario', 'B', 'veterinario.b@fixture.vigia.test', 'VETERINARIO'),
             ('Campero', 'B', 'campero.b@fixture.vigia.test', 'CAMPERO'),
             ('Productor', 'Ambos', 'productor.ambos@fixture.vigia.test', 'PRODUCTOR')
     ) u (nombre, apellido, email, rol);

-- Lejos de la demo (-34.9, -59.2) y de la grilla de loadtest (-35 a -36, -60 a -61,2).
INSERT INTO establecimientos (nombre, latitud, longitud, activo, fecha_alta)
VALUES ('Fixture A', -36.5, -61.5, 1, current_date - 30),
       ('Fixture B', -36.6, -61.6, 1, current_date - 30);

CREATE TEMP TABLE fx_est ON COMMIT DROP AS
SELECT right(nombre, 1) AS letra, lower(right(nombre, 1)) AS sufijo, id, latitud, longitud
FROM establecimientos WHERE nombre IN ('Fixture A', 'Fixture B');

-- Cada establecimiento: su productor, veterinario y campero, más el productor de los dos (H0035). El administrador es
-- un rol global y no tiene empleado.
INSERT INTO empleado (establecimiento_id, usuario_id, activo, fecha_vinculacion)
SELECT e.id, u.id, 1, r.ahora - interval '30 days'
FROM fx_est e
CROSS JOIN fx_ref r
JOIN usuarios u ON u.email IN (format('productor.%s@fixture.vigia.test', e.sufijo),
                               format('veterinario.%s@fixture.vigia.test', e.sufijo),
                               format('campero.%s@fixture.vigia.test', e.sufijo),
                               'productor.ambos@fixture.vigia.test');

INSERT INTO rodeo_lote (establecimiento_id, nombre, activo, fecha_alta)
SELECT e.id, format('Rodeo %s%s', e.letra, n), 1, r.ahora - interval '30 days'
FROM fx_est e CROSS JOIN fx_ref r CROSS JOIN generate_series(1, 2) n;

-- Cuadrado de ~1 km alrededor del establecimiento, asignado al rodeo 1.
INSERT INTO perimetro (establecimiento_id, nombre, descripcion, activo, area)
SELECT id, format('Perímetro %s', letra), 'Perímetro del rodeo 1 (~1 km)', true,
       ST_MakeEnvelope(longitud - 0.0045, latitud - 0.0045, longitud + 0.0045, latitud + 0.0045, 4326)
FROM fx_est;

INSERT INTO perimetro_lote (perimetro_id, rodeo_lote_id)
SELECT p.id, l.id
FROM fx_est e
JOIN perimetro p ON p.establecimiento_id = e.id
JOIN rodeo_lote l ON l.establecimiento_id = e.id AND l.nombre = format('Rodeo %s1', e.letra);

-- Los animales de cada establecimiento, iguales en A y en B. n es el número de la caravana (FX-A-01) y del animal
-- (A-101). dias: días de telemetría; desfase_min: minutos entre el último dato y la carga.
CREATE TEMP TABLE fx_def ON COMMIT DROP AS
SELECT * FROM (VALUES
    (1, 'Calibrada', 1, 8, 0),    -- 8 días a 5 min: 7 de calibración de salud y la ventana que evalúa
    (2, 'Celo',      1, 1, 0),    -- alerta de celo pendiente
    (3, 'Sanitaria', 2, 1, 0),    -- alerta sanitaria pendiente, en el rodeo 2 (sin perímetro)
    (4, 'Aislada',   1, 1, 0),    -- alerta de aislamiento con su episodio abierto: está a ~280 m del rodeo
    (5, 'Fuera',     1, 1, 0),    -- alerta de perímetro: la última hora está afuera
    (6, 'Sin datos', 1, 1, 180)   -- último dato hace 3 h (> 2 h): alerta de celo con dato desactualizado
) d (n, alias, rodeo, dias, desfase_min);

INSERT INTO caravanas (codigo, dev_eui, fecha_asignacion, fecha_alta, activo, estado_sensor, establecimiento_id, bateria)
SELECT x.codigo, x.codigo, current_date - 30, current_date - 30, 1, 'VINCULADO', e.id, 80
FROM fx_est e
CROSS JOIN fx_def d
CROSS JOIN LATERAL (SELECT format('FX-%s-%s', e.letra, lpad(d.n::text, 2, '0')) AS codigo) x;

-- Dos caravanas libres por establecimiento (H0040/H0041).
INSERT INTO caravanas (codigo, dev_eui, fecha_alta, activo, estado_sensor, establecimiento_id)
SELECT format('FX-%s-L%s', e.letra, n), format('FX-%s-L%s', e.letra, n), current_date - 30, 1, 'DISPONIBLE', e.id
FROM fx_est e CROSS JOIN generate_series(1, 2) n;

INSERT INTO animales (establecimiento_id, rodeo_lote_id, id_caravana_oficial, alias, fecha_nacimiento, raza,
                      estado_actual, fecha_alta, caravana_id)
SELECT e.id, l.id, format('%s-%s', e.letra, 100 + d.n), format('%s %s', d.alias, e.letra), date '2021-01-01',
       'Holando', 'ACTIVO', current_date - 30, k.id
FROM fx_est e
CROSS JOIN fx_def d
JOIN rodeo_lote l ON l.establecimiento_id = e.id AND l.nombre = format('Rodeo %s%s', e.letra, d.rodeo)
JOIN caravanas k ON k.codigo = format('FX-%s-%s', e.letra, lpad(d.n::text, 2, '0'));

CREATE TEMP TABLE fx_animal ON COMMIT DROP AS
SELECT a.id AS animal_id, a.caravana_id, e.letra, e.sufijo, e.id AS establecimiento_id, d.*,
       l.nombre AS rodeo_nombre,
       -- Lugar de la vaca en el rodeo: grilla de ~25 m al sur del casco, adentro del perímetro.
       e.latitud - 0.0004 + ((d.n - 1) % 3 - 1) * 0.0002 AS lat_base,
       e.longitud + ((d.n - 1) / 3 - 0.5) * 0.0002 AS lon_base
FROM animales a
JOIN fx_est e ON e.id = a.establecimiento_id
JOIN caravanas k ON k.id = a.caravana_id
JOIN fx_def d ON k.codigo = format('FX-%s-%s', e.letra, lpad(d.n::text, 2, '0'))
JOIN rodeo_lote l ON l.id = a.rodeo_lote_id;

-- Estado reproductivo para todas: sin ninguno, el job de anestro las alerta (cuenta desde nacimiento + 1 año).
-- Las de celo, paridas hace 60 días y con el POSIBLE_CELO de su alerta; el resto, preñadas.
INSERT INTO estados_reproductivos (animal_id, usuario_registro_id, estado, fecha_ultimo_parto, fecha_ultima_inseminacion,
                                   origen, fecha_registro)
SELECT a.animal_id, v.id, x.estado, x.parto, x.inseminacion, 'MANUAL', r.ahora - interval '30 days'
FROM fx_animal a
CROSS JOIN fx_ref r
JOIN usuarios v ON v.email = format('veterinario.%s@fixture.vigia.test', a.sufijo)
CROSS JOIN LATERAL (SELECT CASE WHEN a.n IN (2, 6) THEN 'PARIDA' ELSE 'GESTANTE' END AS estado,
                           CASE WHEN a.n IN (2, 6) THEN current_date - 60 END AS parto,
                           CASE WHEN a.n IN (2, 6) THEN NULL ELSE current_date - 60 END AS inseminacion) x;

INSERT INTO estados_reproductivos (animal_id, estado, origen, fecha_registro)
SELECT a.animal_id, 'POSIBLE_CELO', 'AUTOMATICO', r.ahora - interval '4 hours'
FROM fx_animal a CROSS JOIN fx_ref r
WHERE a.n IN (2, 6);

-- Telemetría cada 5 min; i = 0 es el dato más nuevo. Todo con las inferencias COMPLETADO: los jobs no reprocesan el
-- historial ni generan alertas nuevas sobre el escenario. moveState como el pastoreo del simulador (35 % reposo,
-- 60 % normal, 5 % alta actividad), por fórmula.
INSERT INTO telemetria (fecha_hora, caravana_id, latitud, longitud, move_state, bateria,
                        estado_inferencia_celo, estado_inferencia_salud, estado_inferencia_geo)
SELECT r.ahora - make_interval(mins => a.desfase_min + i * 5), a.caravana_id,
       a.lat_base + 0.00008 * sin(2 * pi() * i / 87.6 + a.n) + p.dlat,
       a.lon_base + 0.00008 * cos(2 * pi() * i / 87.6 + a.n),
       CASE WHEN (i * 7 + a.n * 3) % 20 < 7 THEN (i + a.n) % 2
            WHEN (i * 7 + a.n * 3) % 20 < 19 THEN 2 + (i * 3 + a.n) % 5
            ELSE 7 + (i + a.n) % 3 END,
       80, 'COMPLETADO', 'COMPLETADO', 'COMPLETADO'
FROM fx_animal a
CROSS JOIN fx_ref r
CROSS JOIN LATERAL generate_series(0, a.dias * 288 - 1) i
-- La aislada pasa las últimas 3 h ~280 m al norte del rodeo; la de perímetro, la última hora ~1 km al norte (afuera).
CROSS JOIN LATERAL (SELECT CASE WHEN a.n = 4 AND i < 36 THEN 0.0025
                                WHEN a.n = 5 AND i < 12 THEN 0.0090
                                ELSE 0 END AS dlat) p;

UPDATE caravanas k
SET ultima_senial = t.ultima
FROM (SELECT caravana_id, max(fecha_hora) AS ultima FROM telemetria
      WHERE caravana_id IN (SELECT caravana_id FROM fx_animal) GROUP BY caravana_id) t
WHERE k.id = t.caravana_id;

-- Alertas pendientes, una de cada tipo por establecimiento (y la de celo con dato desactualizado). Mismos campos que
-- arman los detectores: CeloDetector, SaludDetector, GeofenceVerificationService y GeoDetectorStrategyImpl.
INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, valor_umbral, valor_detectado, estado,
                     ventana_inicio_ins, ventana_fin_ins, confianza_modelo)
SELECT a.animal_id, 'Celo Detectado', 'ALTA', x.ts, 'CELO', 0.947, 0.95, 'PENDIENTE',
       x.ts + interval '6 hours', x.ts + interval '24 hours', 0.95
FROM fx_animal a
CROSS JOIN fx_ref r
CROSS JOIN LATERAL (SELECT r.ahora - make_interval(mins => a.desfase_min + 60) AS ts) x
WHERE a.n IN (2, 6);

INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, valor_umbral, valor_detectado, estado,
                     confianza_modelo, desvio_detectado, posible_causa)
SELECT a.animal_id, 'SALUD', 'MEDIA', r.ahora - interval '2 hours', 'SALUD', 0.5, 0.72, 'PENDIENTE', 0.72, 1,
       'Caída de actividad respecto de su perfil'
FROM fx_animal a CROSS JOIN fx_ref r
WHERE a.n = 3;

CREATE TEMP TABLE fx_ultima ON COMMIT DROP AS
SELECT DISTINCT ON (t.caravana_id) t.caravana_id, t.latitud, t.longitud
FROM telemetria t
WHERE t.caravana_id IN (SELECT caravana_id FROM fx_animal)
ORDER BY t.caravana_id, t.fecha_hora DESC;

INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, valor_umbral, valor_detectado, estado,
                     latitud, longitud, accion_recomendada, posible_causa)
SELECT a.animal_id, 'Fuera de Perímetro', 'ALTA', r.ahora - interval '30 minutes', 'FUERA_DE_PERIMETRO', 1.0, 0.0,
       'PENDIENTE', u.latitud, u.longitud,
       'Verificar ubicación del animal en el mapa y retornarlo al perímetro autorizado.',
       'Animal fuera del perímetro asignado para el lote ' || a.rodeo_nombre
FROM fx_animal a CROSS JOIN fx_ref r JOIN fx_ultima u ON u.caravana_id = a.caravana_id
WHERE a.n = 5;

-- Aislamiento: 280 m con umbral de 100 m (umbral_aislamientom por defecto) es ALTA, más del doble.
INSERT INTO alertas (animal_id, tipo, severidad, ts_generacion, disparador, valor_umbral, valor_detectado, estado,
                     latitud, longitud, accion_recomendada)
SELECT a.animal_id, 'AISLAMIENTO_SOCIAL', 'ALTA', r.ahora - interval '2 hours', 'AISLAMIENTO_SOCIAL', 100.0, 280.0,
       'PENDIENTE', u.latitud, u.longitud,
       'Recorrer la zona y verificar el estado de los animales alejados del rodeo.'
FROM fx_animal a CROSS JOIN fx_ref r JOIN fx_ultima u ON u.caravana_id = a.caravana_id
WHERE a.n = 4;

INSERT INTO alerta_animales_aislados (alerta_id, animal_id, segundos_fuera, distancia_grupo_m, ts_inicio)
SELECT al.id, a.animal_id, 3 * 3600, 280.0, r.ahora - interval '3 hours'
FROM fx_animal a
CROSS JOIN fx_ref r
JOIN alertas al ON al.animal_id = a.animal_id AND al.disparador = 'AISLAMIENTO_SOCIAL'
WHERE a.n = 4;
COMMIT;
