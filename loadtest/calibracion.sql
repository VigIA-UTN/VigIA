-- Cuerpo de /perfil de ml-salud: 7 días (2016 puntos) del movimiento de trayectoria.sql. Ver README, "perfil.json".
SELECT json_build_object('calibracion', json_agg(json_build_object(
           'moveState', pg_temp.carga_move_state(),
           'latitud', -35 + pg_temp.carga_dlat(0, k),
           'longitud', -60 + pg_temp.carga_dlon(0, k)) ORDER BY k))
FROM generate_series(floor(extract(epoch FROM now()) / 300)::int - 2016,
                     floor(extract(epoch FROM now()) / 300)::int - 1) k;
