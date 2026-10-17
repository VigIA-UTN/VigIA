-- Movimiento sintético de los sensores de carga, como funciones de la sesión (pg_temp): se van al cerrar psql.
-- Lo usan seed.sql (historial) y calibracion.sql (perfil.json); carga.js repite la misma fórmula.
--
-- Cada vaca da vueltas alrededor de su lugar en el rodeo sobre dos círculos superpuestos, de 87,6 y 34,8 ticks,
-- que no dividen el día. La distancia por tick va de ~0,6 a ~2,2 en la escala de los modelos (grados × 100000),
-- cerca del 1,8 del pastoreo normal del simulador, y cada día sale un poco distinto, como en la calibración.
-- La posición depende solo del tiempo, así que historial, perfil y tráfico de k6 empalman sin compartir estado.
--   d: índice del sensor en su establecimiento (0..49), en una grilla de 5 × 10 cada ~33 m. Con las vueltas,
--      dos vecinas quedan a menos de 90 m: el rodeo sigue conectado bajo los 100 m de aislamiento de ml-geo.
--   t: tick de 5 minutos desde epoch (segundos / 300).
CREATE FUNCTION pg_temp.carga_dlat(d int, t float8) RETURNS float8 LANGUAGE sql IMMUTABLE AS $$
    SELECT (d / 10 - 2) * 0.0003
         + 0.000195 * sin(2 * pi() * t / 87.6 + d * 0.37)
         + 0.0000443 * sin(2 * pi() * t / 34.8 + d * 1.13)
$$;

CREATE FUNCTION pg_temp.carga_dlon(d int, t float8) RETURNS float8 LANGUAGE sql IMMUTABLE AS $$
    SELECT (d % 10 - 4.5) * 0.0004
         + 0.000195 * cos(2 * pi() * t / 87.6 + d * 0.37)
         + 0.0000443 * cos(2 * pi() * t / 34.8 + d * 1.13)
$$;

-- Como el pastoreo normal del simulador: 35 % reposo (0-1), 60 % normal (2-6), 5 % alta actividad (7-9).
CREATE FUNCTION pg_temp.carga_move_state() RETURNS int LANGUAGE sql VOLATILE AS $$
    SELECT CASE WHEN r < 0.35 THEN floor(random() * 2)
                WHEN r < 0.95 THEN 2 + floor(random() * 5)
                ELSE 7 + floor(random() * 3) END::int
    FROM (SELECT random() AS r) x
$$;
