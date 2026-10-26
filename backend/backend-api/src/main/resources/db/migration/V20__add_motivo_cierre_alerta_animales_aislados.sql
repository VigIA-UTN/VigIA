-- Por qué se cerró un episodio de aislamiento: REGRESO (volvió al grupo) o SIN_DATOS (la caravana
-- dejó de transmitir y no se sabe si volvió). NULL mientras está abierto.
ALTER TABLE alerta_animales_aislados ADD COLUMN motivo_cierre VARCHAR(20);

UPDATE alerta_animales_aislados SET motivo_cierre = 'REGRESO' WHERE ts_regreso IS NOT NULL;
