-- Una alerta confirmada o descartada es parte del historial clínico: no puede volver a cambiar
-- su resolución, el usuario que la resolvió ni el dato sensorial de origen que respaldan los
-- diagnósticos. El resto de las columnas y las alertas PENDIENTE no se tocan.
-- Respeta el mismo bypass que trg_bloquear_modificacion_inmutable() (mantenimiento / DataInitializer).

CREATE OR REPLACE FUNCTION trg_bloquear_cambio_resolucion_alerta()
RETURNS TRIGGER AS $$
BEGIN
    IF current_setting('vigia.bypass_immutable_triggers', true) = 'true' THEN
        RETURN NEW;
    END IF;

    IF OLD.estado IN ('CONFIRMADA', 'DESCARTADA') AND (
           NEW.estado                  IS DISTINCT FROM OLD.estado
        OR NEW.usuario_confirmacion_id IS DISTINCT FROM OLD.usuario_confirmacion_id
        OR NEW.ts_confirmacion         IS DISTINCT FROM OLD.ts_confirmacion
        OR NEW.motivo_descarte         IS DISTINCT FROM OLD.motivo_descarte
        OR NEW.confianza_modelo        IS DISTINCT FROM OLD.confianza_modelo
        OR NEW.serie_comparativa_json  IS DISTINCT FROM OLD.serie_comparativa_json
    ) THEN
        RAISE EXCEPTION 'Acción prohibida: no se permite modificar la resolución de la alerta % (estado %)',
            OLD.id, OLD.estado;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_bloquear_cambio_resolucion_alertas ON alertas;
CREATE TRIGGER trg_bloquear_cambio_resolucion_alertas
    BEFORE UPDATE ON alertas
    FOR EACH ROW
    EXECUTE FUNCTION trg_bloquear_cambio_resolucion_alerta();
