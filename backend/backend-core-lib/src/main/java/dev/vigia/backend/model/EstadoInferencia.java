package dev.vigia.backend.model;

/** Estado de la evaluación de una lectura de telemetría en cada pipeline de ML (celo, salud y geo). */
public enum EstadoInferencia {
    /** Sin evaluar, o el microservicio estaba degradado: el próximo ciclo del batch la vuelve a tomar. */
    PENDIENTE,
    COMPLETADO,
    /** El microservicio rechazó los datos (4xx) o falló el procesamiento: repetirla daría lo mismo. */
    ERROR,
    /**
     * Ya no se escribe: una caída del microservicio deja la lectura en PENDIENTE. Se conserva solo para que el
     * worker pueda leer las filas viejas mientras la migración V23 (que las pasa a PENDIENTE) no haya corrido; la
     * corre backend-api, que puede deployarse después que el worker. Sacarlo cuando V23 ya esté aplicada.
     */
    @Deprecated
    INFERENCIA_PENDIENTE
}
