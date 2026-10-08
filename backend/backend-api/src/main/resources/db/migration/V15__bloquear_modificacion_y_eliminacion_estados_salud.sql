-- Los estados de salud son parte del historial clínico: solo se agregan, nunca se modifican ni eliminan.
-- Reutiliza la función de V12 (incluye el bypass para mantenimiento / DataInitializer).
DROP TRIGGER IF EXISTS trg_bloquear_modificacion_estados_salud ON estados_salud;
CREATE TRIGGER trg_bloquear_modificacion_estados_salud
    BEFORE UPDATE OR DELETE ON estados_salud
    FOR EACH ROW
    EXECUTE FUNCTION trg_bloquear_modificacion_inmutable();
