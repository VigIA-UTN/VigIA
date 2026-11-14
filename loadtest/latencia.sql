-- Latencia de las alertas (RNF01): ts_generacion menos la hora del dato que las disparó. Ver README, "Alertas".
-- Las variables son las mismas de seed.sql y carga.js; inicio es la hora local (America/Argentina/Buenos_Aires) de
-- la semilla: los datos posteriores son los de k6.
--   docker exec -i vigia-db psql -U vigia_user -d vigia -v inicio='2026-11-09 21:40:00' \
--     -v sensores=200 -v celo=10 -v salud=10 -v aislamiento=1 < loadtest/latencia.sql
--
-- El dato disparador (t0) de cada animal con anomalía es su primer dato de k6: la semilla deja sus últimas 24 h ya
-- anómalas y procesadas, y el animal queda callado hasta su turno, así que ese dato es el primero con el que los jobs
-- pueden evaluar la ventana. Las alertas de celo y salud no guardan qué fila las disparó.
-- En aislamiento, t0 es ese dato más los 15 min fuera del grupo que exige el worker (VIGIA_GEO_MINUTOS_FUERA_MINIMO):
-- antes no puede alertar. El primer paso lo aleja ~90 m y el segundo ~180 m, así que si recién lo ve afuera en el
-- segundo, la latencia incluye esos 5 min de más.
\set ON_ERROR_STOP on
\pset footer off

CREATE TEMP TABLE lat_animal AS
SELECT a.id AS animal_id, k.codigo,
       CASE WHEN s.d < :celo THEN 'celo'
            WHEN s.d < :celo + :salud THEN 'salud'
            WHEN :aislamiento > 0 AND s.d = :sensores - 1 THEN 'aislamiento'
            ELSE 'normal' END AS tipo,
       (SELECT min(t.fecha_hora) FROM telemetria t WHERE t.caravana_id = k.id AND t.fecha_hora > :'inicio') AS primer_dato
FROM caravanas k
JOIN animales a ON a.caravana_id = k.id
CROSS JOIN LATERAL (SELECT (substr(k.codigo, 4)::int - 1) % :sensores AS d) s
WHERE k.codigo LIKE 'LT-%';

-- Las de aislamiento no tienen animal: sus animales están en alerta_animales_aislados.
CREATE TEMP TABLE lat_alerta AS
SELECT al.disparador, al.ts_generacion, la.codigo, la.tipo,
       la.primer_dato + CASE WHEN al.disparador = 'AISLAMIENTO_SOCIAL' THEN interval '15 minutes' ELSE interval '0' END
       AS t0
FROM alertas al
LEFT JOIN alerta_animales_aislados x ON x.alerta_id = al.id AND al.animal_id IS NULL
JOIN lat_animal la ON la.animal_id = coalesce(al.animal_id, x.animal_id)
WHERE al.ts_generacion > :'inicio';

\echo 'Alertas desde el inicio (latencia en minutos)'
SELECT disparador, codigo, tipo AS anomalia, t0::timestamp(0), ts_generacion::timestamp(0),
       round(extract(epoch FROM ts_generacion - t0) / 60, 1) AS latencia_min
FROM lat_alerta ORDER BY disparador, ts_generacion;

\echo 'Resumen por tipo de alerta, sobre los animales con anomalía (RNF01: p95 <= 60 min)'
SELECT coalesce(disparador, 'TOTAL') AS disparador, count(*) AS alertas,
       round(percentile_cont(0.5) WITHIN GROUP (ORDER BY lat)::numeric, 1) AS p50_min,
       round(percentile_cont(0.95) WITHIN GROUP (ORDER BY lat)::numeric, 1) AS p95_min,
       round(max(lat)::numeric, 1) AS max_min
FROM (SELECT disparador, extract(epoch FROM ts_generacion - t0) / 60 AS lat FROM lat_alerta WHERE tipo <> 'normal') x
GROUP BY ROLLUP (disparador)
ORDER BY disparador NULLS LAST;

\echo 'Problemas: animales con anomalía sin su alerta, y alertas sobre animales sin anomalía'
SELECT la.codigo, la.tipo AS anomalia, la.primer_dato::timestamp(0), 'sin alerta ' || e.disparador AS problema
FROM lat_animal la
CROSS JOIN LATERAL (SELECT CASE la.tipo WHEN 'celo' THEN 'CELO' WHEN 'salud' THEN 'SALUD'
                                        ELSE 'AISLAMIENTO_SOCIAL' END AS disparador) e
WHERE la.tipo <> 'normal'
  AND NOT EXISTS (SELECT 1 FROM lat_alerta x WHERE x.codigo = la.codigo AND x.disparador = e.disparador)
UNION ALL
SELECT codigo, tipo, NULL, 'alerta ' || disparador || ' inesperada' FROM lat_alerta WHERE tipo = 'normal'
ORDER BY 1;
