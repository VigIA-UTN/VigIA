-- Los diagnósticos registran fecha y hora (el historial clínico muestra la hora).
-- ALTER COLUMN ... TYPE es DDL: no dispara el trigger de inmutabilidad por fila.
-- Las filas existentes quedan a las 00:00 del mismo día.
ALTER TABLE diagnosticos ALTER COLUMN fecha TYPE TIMESTAMP USING fecha::timestamp;
