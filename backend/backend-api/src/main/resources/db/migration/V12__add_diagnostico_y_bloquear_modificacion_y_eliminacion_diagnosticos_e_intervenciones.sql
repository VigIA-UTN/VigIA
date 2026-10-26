-- Tabla de diagnósticos clínicos emitidos por veterinarios sobre animales alertados
CREATE TABLE diagnosticos (
    id            BIGSERIAL PRIMARY KEY,
    animal_id     BIGINT NOT NULL,
    alerta_id     BIGINT NOT NULL,
    veterinario_id BIGINT NOT NULL,
    diagnostico   TEXT   NOT NULL,
    indicaciones  TEXT   NOT NULL,
    fecha         DATE   NOT NULL,
    observaciones TEXT,
    CONSTRAINT fk_diagnosticos_animal      FOREIGN KEY (animal_id)      REFERENCES animales(id),
    CONSTRAINT fk_diagnosticos_alerta      FOREIGN KEY (alerta_id)      REFERENCES alertas(id),
    CONSTRAINT fk_diagnosticos_veterinario FOREIGN KEY (veterinario_id) REFERENCES usuarios(id)
);

-- Función de trigger para impedir la modificación (UPDATE) o eliminación (DELETE)
-- de registros inmutables de auditoría clínica (diagnósticos/estados de salud e intervenciones).

CREATE OR REPLACE FUNCTION trg_bloquear_modificacion_inmutable()
RETURNS TRIGGER AS $$
BEGIN
    -- Permitir bypass opcional para tareas de mantenimiento / inicialización de datos
    IF current_setting('vigia.bypass_immutable_triggers', true) = 'true' THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        ELSE
            RETURN NEW;
        END IF;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'Acción prohibida: no se permite modificar registros en la tabla %', TG_TABLE_NAME;
    ELSIF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Acción prohibida: no se permite eliminar registros en la tabla %', TG_TABLE_NAME;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Trigger para diagnosticos clínicos
        DROP TRIGGER IF EXISTS trg_bloquear_modificacion_diagnosticos ON diagnosticos;
        CREATE TRIGGER trg_bloquear_modificacion_diagnosticos
            BEFORE UPDATE OR DELETE ON diagnosticos
            FOR EACH ROW
            EXECUTE FUNCTION trg_bloquear_modificacion_inmutable();

-- Trigger para intervenciones
DROP TRIGGER IF EXISTS trg_bloquear_modificacion_intervenciones ON intervenciones;
CREATE TRIGGER trg_bloquear_modificacion_intervenciones
    BEFORE UPDATE OR DELETE ON intervenciones
    FOR EACH ROW
    EXECUTE FUNCTION trg_bloquear_modificacion_inmutable();
