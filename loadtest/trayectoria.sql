-- Movimiento sintético de los sensores de carga, como funciones de la sesión (pg_temp): se van al cerrar psql.
-- Lo usan seed.sql (historial) y calibracion.sql (perfil.json); carga.js repite la misma fórmula.
--
-- Cada vaca da vueltas alrededor de su lugar en el rodeo sobre dos círculos superpuestos, de 87,6 y 34,8 ticks,
-- que no dividen el día. La distancia por tick va de ~0,6 a ~2,2 en la escala de los modelos (grados × 100000),
-- cerca del 1,8 del pastoreo normal del simulador, y cada día sale un poco distinto, como en la calibración.
-- La posición depende solo del tiempo, así que historial, perfil y tráfico de k6 empalman sin compartir estado.
--   d: índice del sensor en su establecimiento (0..sensores-1), en una grilla de 10 columnas cada ~33 m, con las
--      filas centradas en el establecimiento (con 50: 5 filas, de -2 a 2; con 200: 20 filas, que siguen dentro del
--      perímetro de ~1 km). Con las vueltas, dos vecinas quedan a menos de 90 m: el rodeo sigue conectado bajo los
--      100 m de aislamiento de ml-geo.
--   t: tick de 5 minutos desde epoch (segundos / 300).
--   amplitud: escala de las vueltas (1 = pastoreo normal; las vacas con desvío sanitario se mueven menos).
CREATE FUNCTION pg_temp.carga_dlat(d int, t float8, sensores int DEFAULT 50, amplitud float8 DEFAULT 1)
RETURNS float8 LANGUAGE sql IMMUTABLE AS $$
    SELECT (d / 10 - ((sensores + 9) / 10 - 1) / 2.0) * 0.0003
         + amplitud * 0.000195 * sin(2 * pi() * t / 87.6 + d * 0.37)
         + amplitud * 0.0000443 * sin(2 * pi() * t / 34.8 + d * 1.13)
$$;

CREATE FUNCTION pg_temp.carga_dlon(d int, t float8, amplitud float8 DEFAULT 1) RETURNS float8 LANGUAGE sql IMMUTABLE AS $$
    SELECT (d % 10 - 4.5) * 0.0004
         + amplitud * 0.000195 * cos(2 * pi() * t / 87.6 + d * 0.37)
         + amplitud * 0.0000443 * cos(2 * pi() * t / 34.8 + d * 1.13)
$$;

-- Celo (RNF01, ver README): la vaca queda en su lugar del rodeo y en los ticks impares salta a un punto al azar a
-- 2,5-4 veces la velocidad base de simulador_celo.py. ml-celo solo mira moveState y la distancia entre puntos
-- seguidos, así que la ve como una caminata a esa velocidad, sin alejarse del rodeo. Devuelve {dlat, dlon}.
CREATE FUNCTION pg_temp.carga_salto(k int) RETURNS float8[] LANGUAGE sql VOLATILE AS $$
    SELECT CASE WHEN k % 2 = 1 THEN ARRAY[s * cos(a), s * sin(a)] ELSE ARRAY[0, 0]::float8[] END
    FROM (SELECT 0.000018 * (2.5 + 1.5 * random()) AS s, 2 * pi() * random() AS a) x
$$;

-- Como el pastoreo normal del simulador: 35 % reposo (0-1), 60 % normal (2-6), 5 % alta actividad (7-9).
-- En celo, alta actividad (7-9); con desvío sanitario, poca (1-3), como simulador_celo.py y simulador_salud.py.
CREATE FUNCTION pg_temp.carga_move_state(tipo text DEFAULT 'normal') RETURNS int LANGUAGE sql VOLATILE AS $$
    SELECT CASE WHEN tipo = 'celo' THEN 7 + floor(random() * 3)
                WHEN tipo = 'salud' THEN 1 + floor(random() * 3)
                WHEN r < 0.35 THEN floor(random() * 2)
                WHEN r < 0.95 THEN 2 + floor(random() * 5)
                ELSE 7 + floor(random() * 3) END::int
    FROM (SELECT random() AS r) x
$$;
