import axiosClient from './axiosClient';
import { getHistorialSalud, getLineaTiempoAlertas, type EstadoSaludResponse, type IntervencionResponse, type LineaTiempoEventoResponse } from './animalesApi';
import { getDiagnosticos, type DiagnosticoResponse } from './diagnosticoApi';

/** `diagnostico` es solo el diagnóstico formal, ligado a su alerta; `estado_salud` es el estado cargado en la ficha. */
export type HistorialTipo = 'alerta' | 'diagnostico' | 'estado_salud' | 'intervencion';
export type HistorialOrigen = 'AUTOMATICO' | 'MANUAL' | 'CARGA_MASIVA';

export interface HistorialEvento {
    id: string;
    tipo: HistorialTipo;
    fecha: string; // ISO local: YYYY-MM-DDTHH:mm
    titulo: string;
    resumen: string;
    sev?: 'ALTA' | 'MEDIA' | 'BAJA';
    estado?: 'SANO' | 'EN_OBSERVACION' | 'ENFERMO';
    sub?: 'TRATAMIENTO' | 'VACUNACION' | 'REVISION_VETERINARIA' | 'INSEMINACION' | 'OTRO';
    usuario: string;
    rol?: string;
    origen: HistorialOrigen;
    fuente: string;
    fechaRegistro: string;
    detalles?: [string, string][];
    /** Alerta de origen, para abrir su detalle; los estados de salud no tienen. */
    alertaId?: number;
}

const SIS = 'Sistema VigIA';
const INTERVENCION_LABEL: Record<string, string> = {
    TRATAMIENTO: 'Tratamiento', VACUNACION: 'Vacunación', REVISION_VETERINARIA: 'Revisión veterinaria',
    INSEMINACION: 'Inseminación', OTRO: 'Otro',
};
// Mismo vocabulario que AlertNotificationCard ("#ID en estado CRITICO").
const ESTADO_ANIMAL: Record<'ALTA' | 'MEDIA' | 'BAJA', string> = { BAJA: 'NORMAL', MEDIA: 'BORDE', ALTA: 'CRITICO' };
const RESOLUCION_LABEL: Record<string, string> = {
    SIN_ATENDER: 'Pendiente', CONFIRMADA: 'Confirmada', DESCARTADA: 'Descartada', ARCHIVADA: 'Archivada',
};
const ESTADO_SALUD_LABEL: Record<NonNullable<HistorialEvento['estado']>, string> = {
    SANO: 'Sano', EN_OBSERVACION: 'En observación', ENFERMO: 'Enfermo',
};
const FUENTE_ESTADO_SALUD: Record<HistorialOrigen, string> = {
    MANUAL: 'Cargado en Ficha › Estado de salud',
    CARGA_MASIVA: 'Carga masiva de estados de salud',
    AUTOMATICO: 'Modelo sanitario VigIA',
};

/** El backend manda LocalDateTime (con segundos y fracción); el historial usa YYYY-MM-DDTHH:mm. */
const minuto = (iso: string) => iso.slice(0, 16);
const fmtDiaIso = (d: string) => d.split('-').reverse().join('/');
const fmtFechaHora = (iso: string) => `${fmtDiaIso(iso.slice(0, 10))} ${iso.slice(11, 16)} h`;
const esOrigen = (o: string | null): o is HistorialOrigen => o === 'AUTOMATICO' || o === 'MANUAL' || o === 'CARGA_MASIVA';
const esEstado = (e: string | null): e is NonNullable<HistorialEvento['estado']> =>
    e === 'SANO' || e === 'EN_OBSERVACION' || e === 'ENFERMO';

function alertaOIntervencion(ev: LineaTiempoEventoResponse): HistorialEvento {
    if (ev.tipoEvento === 'INTERVENCION') {
        const tipo = ev.tipoIntervencion ?? 'OTRO';
        const label = INTERVENCION_LABEL[tipo] ?? tipo;
        return {
            id: `i${ev.id}`, tipo: 'intervencion', sub: tipo as HistorialEvento['sub'], fecha: minuto(ev.fecha),
            titulo: label, resumen: ev.descripcion ?? '', usuario: ev.interventor ?? '—', origen: 'MANUAL',
            fuente: ev.alertaAsociadaId ? `Registrada desde la alerta #${ev.alertaAsociadaId}` : 'Registrada manualmente',
            fechaRegistro: minuto(ev.fecha), alertaId: ev.alertaAsociadaId ?? undefined,
            detalles: [['Tipo', label], ['Intervención', `#${ev.id}`]],
        };
    }
    return {
        id: `a${ev.id}`, tipo: 'alerta', sev: ev.severidad ?? undefined, fecha: minuto(ev.fecha),
        titulo: ev.tipo !== 'SALUD' ? (ev.subtipo ?? ev.tipo ?? 'Alerta')
            : ev.severidad ? `Animal en estado ${ESTADO_ANIMAL[ev.severidad]}` : 'Alerta sanitaria',
        resumen: ev.descripcion ?? 'El sistema detectó un comportamiento que se aparta de lo habitual para este animal.',
        usuario: SIS, origen: 'AUTOMATICO', fuente: 'Detección automática del modelo sanitario',
        fechaRegistro: minuto(ev.fecha), alertaId: ev.id,
        detalles: [
            ['Estado de la alerta', RESOLUCION_LABEL[ev.estadoResolucion ?? ''] ?? '—'],
            ...(ev.usuarioResolucion ? [['Resuelta por', ev.usuarioResolucion] as [string, string]] : []),
            ...(ev.tsResolucion ? [['Fecha de resolución', fmtFechaHora(ev.tsResolucion)] as [string, string]] : []),
            ['Alerta', `#${ev.id}`],
        ],
    };
}

function estadoSaludAEvento(es: EstadoSaludResponse): HistorialEvento {
    const origen = esOrigen(es.origen) ? es.origen : 'MANUAL';
    const registro = minuto(es.fechaRegistro);
    // La revisión solo tiene día: si se cargó ese mismo día se usa la hora de carga, si no 00:00.
    const fecha = !es.fechaRevision || registro.startsWith(es.fechaRevision) ? registro : `${es.fechaRevision}T00:00`;
    const estado = esEstado(es.estado) ? es.estado : undefined;
    return {
        id: `s${es.id}`, tipo: 'estado_salud', estado, fecha,
        titulo: estado ? ESTADO_SALUD_LABEL[estado] : 'Estado de salud', resumen: es.diagnosticoNotas || 'Sin notas.',
        usuario: es.registradoPor ?? SIS, origen, fuente: FUENTE_ESTADO_SALUD[origen], fechaRegistro: registro,
        detalles: [
            ['Diagnóstico / notas', es.diagnosticoNotas || '—'],
            ['Tratamiento indicado', es.tratamientoIndicado || '—'],
            ['Fecha de revisión', fmtDiaIso(fecha.slice(0, 10))],
        ],
    };
}

function diagnosticoAEvento(d: DiagnosticoResponse): HistorialEvento {
    return {
        id: `d${d.id}`, tipo: 'diagnostico', fecha: minuto(d.fechaRegistro), titulo: d.diagnostico,
        resumen: d.tratamiento || 'Sin indicación o tratamiento.', usuario: d.veterinarioNombre, rol: 'VETERINARIO',
        origen: 'MANUAL', fuente: `Registrado desde la alerta #${d.alertaId}`, fechaRegistro: minuto(d.fechaRegistro), alertaId: d.alertaId,
        detalles: [['Dato de origen de la alerta', d.datoOrigen], ['Alerta', `#${d.alertaId}`]],
    };
}

/** Une las tres fuentes reales en un único historial, más reciente primero. */
export function construirHistorial(
    lineaTiempo: LineaTiempoEventoResponse[],
    estadosSalud: EstadoSaludResponse[],
    diagnosticos: DiagnosticoResponse[],
): HistorialEvento[] {
    return [
        ...lineaTiempo.map(alertaOIntervencion),
        ...estadosSalud.map(estadoSaludAEvento),
        ...diagnosticos.map(diagnosticoAEvento),
    ].sort((a, b) => b.fecha.localeCompare(a.fecha));
}

/**
 * Historial clínico unificado: alertas sanitarias + intervenciones, estados de salud y
 * diagnósticos veterinarios del animal, más reciente primero.
 */
export async function getHistorialClinico(animalId: number): Promise<HistorialEvento[]> {
    const [lineaTiempo, estadosSalud, diagnosticos] = await Promise.all([
        getLineaTiempoAlertas(animalId, { tipo: 'sanitario' }),
        getHistorialSalud(animalId),
        getDiagnosticos({ animalId }),
    ]);
    return construirHistorial(lineaTiempo, estadosSalud, diagnosticos);
}

export interface IntervencionManualRequest {
    tipo: 'TRATAMIENTO' | 'VACUNACION' | 'REVISION_VETERINARIA' | 'OTRO';
    fechaHora: string; // YYYY-MM-DDTHH:mm
    descripcion: string;
}

/** Registra una intervención sobre el animal sin alerta asociada (solo VETERINARIO). */
export async function registrarIntervencion(animalId: number, request: IntervencionManualRequest): Promise<IntervencionResponse> {
    const response = await axiosClient.post<IntervencionResponse>(`/animales/${animalId}/intervenciones`, request);
    return response.data;
}
