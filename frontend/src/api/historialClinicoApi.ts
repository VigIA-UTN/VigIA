export type HistorialTipo = 'alerta' | 'diagnostico' | 'intervencion';
export type HistorialOrigen = 'AUTOMATICO' | 'MANUAL' | 'CARGA_MASIVA';

export interface HistorialEvento {
    id: string;
    tipo: HistorialTipo;
    fecha: string; // ISO local: YYYY-MM-DDTHH:mm
    titulo: string;
    resumen: string;
    sev?: 'CRITICO' | 'BORDE';
    estado?: 'SANO' | 'EN_OBSERVACION' | 'ENFERMO';
    sub?: 'TRATAMIENTO' | 'VACUNACION' | 'REVISION_VETERINARIA' | 'INSEMINACION' | 'OTRO';
    usuario: string;
    rol?: string;
    origen: HistorialOrigen;
    fuente: string;
    fechaRegistro: string;
    detalles?: [string, string][];
}

const SIS = 'Sistema VigIA';

const MOCK: HistorialEvento[] = [
    { id: 'a1042', tipo: 'alerta', sev: 'CRITICO', fecha: '2026-09-27T06:12', titulo: 'Animal en estado CRITICO', usuario: SIS, origen: 'AUTOMATICO',
        fuente: 'Caravana A3F2-19C0 · acelerómetro', fechaRegistro: '2026-09-27T06:12',
        resumen: 'El animal podría estar presentando una enfermedad. Andá a revisarlo.',
        detalles: [['Actividad', '−38 % respecto de su línea base'], ['Estado de la alerta', 'ATENDIDA'], ['Alerta', '#1042']] },
    { id: 'i312', tipo: 'intervencion', sub: 'REVISION_VETERINARIA', fecha: '2026-09-27T09:40', titulo: 'Revisión veterinaria', usuario: 'Dra. Paula Ferreyra', rol: 'VETERINARIO', origen: 'MANUAL',
        fuente: 'Registrada desde la alerta #1042', fechaRegistro: '2026-09-27T09:52',
        resumen: 'Revisión clínica en manga. Mucosas pálidas, tos seca y secreción nasal serosa.',
        detalles: [['Tipo', 'Revisión veterinaria'], ['Intervención', '#312']] },
    { id: 'd88', tipo: 'diagnostico', estado: 'ENFERMO', fecha: '2026-09-27T10:05', titulo: 'Neumonía leve', usuario: 'Dra. Paula Ferreyra', rol: 'VETERINARIO', origen: 'MANUAL',
        fuente: 'Cargado en Ficha › Estado de salud', fechaRegistro: '2026-09-27T10:05',
        resumen: 'Cuadro respiratorio compatible con neumonía leve. Se indica tratamiento y control a las 72 h.',
        detalles: [['Diagnóstico / notas', 'Neumonía leve. Auscultación con crepitantes en lóbulo craneal derecho.'], ['Tratamiento indicado', 'Oxitetraciclina LA 20 mg/kg IM, dosis única. Control a las 72 h.'], ['Fecha de revisión', '27/09/2026']] },
    { id: 'i313', tipo: 'intervencion', sub: 'TRATAMIENTO', fecha: '2026-09-27T10:20', titulo: 'Tratamiento', usuario: 'Juan Sosa', rol: 'CAMPERO', origen: 'MANUAL',
        fuente: 'Registrada desde el diagnóstico #88', fechaRegistro: '2026-09-27T10:31',
        resumen: 'Aplicación de oxitetraciclina LA 20 mg/kg intramuscular en tabla del cuello.',
        detalles: [['Tipo', 'Tratamiento'], ['Producto', 'Oxitetraciclina LA 20 %'], ['Dosis', '25 ml IM'], ['Intervención', '#313']] },
    { id: 'a1031', tipo: 'alerta', sev: 'BORDE', fecha: '2026-09-24T22:48', titulo: 'Animal en estado BORDE', usuario: SIS, origen: 'AUTOMATICO',
        fuente: 'Caravana A3F2-19C0 · modelo sanitario v2.1', fechaRegistro: '2026-09-24T22:48',
        resumen: 'Se estima que en 36 horas el animal podría presentar una enfermedad.',
        detalles: [['Rumia', '−22 % en 48 h'], ['Estado de la alerta', 'ESCALADA a #1042'], ['Alerta', '#1031']] },
    { id: 'i298', tipo: 'intervencion', sub: 'VACUNACION', fecha: '2026-08-18T08:00', titulo: 'Vacunación', usuario: 'Martín Olivera', rol: 'PRODUCTOR', origen: 'CARGA_MASIVA',
        fuente: 'Planilla vacunacion_rodeo2_agosto.csv · 214 animales', fechaRegistro: '2026-08-19T17:04',
        resumen: 'Vacuna antiaftosa, campaña 2026. 2 ml subcutáneo.',
        detalles: [['Tipo', 'Vacunación'], ['Producto', 'Antiaftosa oleosa · lote 2604-A'], ['Dosis', '2 ml SC'], ['Intervención', '#298']] },
    { id: 'd71', tipo: 'diagnostico', estado: 'SANO', fecha: '2026-07-30T11:15', titulo: 'Control de rutina', usuario: 'Dra. Paula Ferreyra', rol: 'VETERINARIO', origen: 'MANUAL',
        fuente: 'Cargado en Ficha › Estado de salud', fechaRegistro: '2026-07-30T11:15',
        resumen: 'Sin hallazgos. Condición corporal 3,5 / 5.',
        detalles: [['Diagnóstico / notas', 'Animal en buen estado general. Cojera de abril resuelta.'], ['Tratamiento indicado', '—'], ['Fecha de revisión', '30/07/2026']] },
    { id: 'a977', tipo: 'alerta', sev: 'BORDE', fecha: '2026-07-10T14:30', titulo: 'Animal en estado BORDE', usuario: SIS, origen: 'AUTOMATICO',
        fuente: 'Caravana A3F2-19C0 · acelerómetro', fechaRegistro: '2026-07-10T14:30',
        resumen: 'El animal podría estar presentando actividad baja. Andá a revisarlo.',
        detalles: [['Actividad', '−30 % en 6 h'], ['Estado de la alerta', 'DESCARTADA por Juan Sosa · día de lluvia, rodeo encerrado'], ['Alerta', '#977']] },
    { id: 'i241', tipo: 'intervencion', sub: 'TRATAMIENTO', fecha: '2026-05-15T09:00', titulo: 'Desparasitación', usuario: 'Martín Olivera', rol: 'PRODUCTOR', origen: 'CARGA_MASIVA',
        fuente: 'Planilla sanidad_otoño_2026.xlsx · 198 animales', fechaRegistro: '2026-05-16T12:40',
        resumen: 'Ivermectina 1 % inyectable, 1 ml cada 50 kg.',
        detalles: [['Tipo', 'Tratamiento'], ['Producto', 'Ivermectina 1 %'], ['Dosis', '9 ml SC'], ['Intervención', '#241']] },
    { id: 'd54', tipo: 'diagnostico', estado: 'EN_OBSERVACION', fecha: '2026-04-22T10:00', titulo: 'Cojera leve, miembro posterior izquierdo', usuario: 'Dra. Paula Ferreyra', rol: 'VETERINARIO', origen: 'MANUAL',
        fuente: 'Cargado en Ficha › Estado de salud', fechaRegistro: '2026-04-22T10:18',
        resumen: 'Lesión interdigital superficial. Se indica pediluvio y seguimiento.',
        detalles: [['Diagnóstico / notas', 'Dermatitis interdigital leve, sin compromiso articular.'], ['Tratamiento indicado', 'Pediluvio con sulfato de cobre 5 %, 3 días.'], ['Fecha de revisión', '22/04/2026']] },
    { id: 'i205', tipo: 'intervencion', sub: 'TRATAMIENTO', fecha: '2026-04-22T16:00', titulo: 'Pediluvio', usuario: 'Juan Sosa', rol: 'CAMPERO', origen: 'MANUAL',
        fuente: 'Registrada desde el diagnóstico #54', fechaRegistro: '2026-04-22T18:02',
        resumen: 'Primer pasaje por pediluvio con sulfato de cobre 5 %.',
        detalles: [['Tipo', 'Tratamiento'], ['Intervención', '#205']] },
    { id: 'd53', tipo: 'diagnostico', estado: 'EN_OBSERVACION', fecha: '2026-04-19T07:40', titulo: 'Estado de salud actualizado', usuario: SIS, origen: 'AUTOMATICO',
        fuente: 'Modelo sanitario v2.1 · 3 alertas de actividad en 72 h', fechaRegistro: '2026-04-19T07:40',
        resumen: 'Se estima que el animal podría tener una afección locomotora. Pasa a EN OBSERVACIÓN hasta una revisión.',
        detalles: [['Pasos diarios', '−41 % respecto de su línea base'], ['Tiempo echada', '+3,2 h por día']] },
];

/**
 * Historial clínico unificado (alertas + diagnósticos + intervenciones), más reciente primero.
 * TODO backend: reemplazar el cuerpo por un axiosClient.get(`/animales/${animalId}/historial-clinico`).
 */
export async function getHistorialClinico(animalId: number): Promise<HistorialEvento[]> {
    void animalId; // mock: same events for every animal
    await new Promise(r => setTimeout(r, 300));
    return [...MOCK].sort((a, b) => b.fecha.localeCompare(a.fecha));
}

export interface IntervencionManualRequest {
    tipo: 'TRATAMIENTO' | 'VACUNACION' | 'REVISION_VETERINARIA' | 'OTRO';
    fechaHora: string; // YYYY-MM-DDTHH:mm
    descripcion: string;
}

/**
 * Registro de una intervención sin alerta asociada.
 * TODO backend: no existe endpoint (hoy solo `/alertas/intervenir/{alertaId}`); reemplazar por un POST real.
 */
export async function registrarIntervencion(animalId: number, request: IntervencionManualRequest): Promise<void> {
    void animalId; void request; // stub
    await new Promise(r => setTimeout(r, 300));
}
