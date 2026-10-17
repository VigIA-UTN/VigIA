-- telemetria pasa a ser una hypertable de TimescaleDB, partida en chunks de 1 día, y una política de retención
-- borra los chunks de más de 14 días. Con 25.000 sensores entran ~7M filas por día: borrar un chunk entero es
-- tirar sus archivos, sin un DELETE de millones de filas, sin bloat y sin vacuum.
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- TimescaleDB exige que toda restricción única incluya la columna de tiempo. JPA sigue usando solo id, que
-- sale de la misma secuencia y sigue siendo único. Ninguna tabla tiene una FK hacia telemetria.
ALTER TABLE telemetria DROP CONSTRAINT telemetria_pkey;
ALTER TABLE telemetria ADD PRIMARY KEY (id, fecha_hora);

-- migrate_data mueve a chunks las filas que ya existen (bloquea la tabla mientras tanto).
SELECT create_hypertable('telemetria', by_range('fecha_hora', INTERVAL '1 day'), migrate_data => true);

-- 14 días: el doble de la ventana más larga que se lee (7 días: mapa de calor y calibración de salud).
SELECT add_retention_policy('telemetria', drop_after => INTERVAL '14 days');
