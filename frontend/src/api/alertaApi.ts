import axiosClient from './axiosClient';
import { MOCK_ALERTA_AISLAMIENTO, MOCK_DETALLE_AISLAMIENTO } from './aislamientoMock';

// El backend ya emite AISLAMIENTO_SOCIAL de forma nativa.
const mockAislamiento = false;

export interface AlertaResponse {
  id: number;
  tipo: string;
  /** Tipo crudo de la alerta ('Anestro Detectado', 'Celo Detectado', ...); `tipo` lleva el disparador. */
  subtipo?: string | null;
  animalId: number;
  tsGeneracion: string;
  /** Momento en que se confirmó, descartó o archivó la alerta. */
  tsConfirmacion?: string | null;
  estado: string;
  severidad?: "ALTA" | "MEDIA" | "BAJA";
  gravedad?: 'GRAVE' | 'MEDIA' | 'LEVE';
  accionRecomendada: string;
  /** H0008: última transmisión de la caravana (solo se envía en alertas de celo pendientes). */
  caravanaUltimaSenial?: string | null;
  datoPosiblementeDesactualizado?: boolean;
  horasUmbralDatoDesactualizado?: number;
  /** Solo AISLAMIENTO_SOCIAL: animales que se alejaron del grupo. */
  animalesAislados?: AnimalAislado[];
}

/** Un animal que se alejó del grupo (alerta AISLAMIENTO_SOCIAL). */
export interface AnimalAislado {
  animalId: number;
  identificador: string;
  alias: string | null;
  minutosFuera: number;
  distanciaGrupoM: number;
  severidad: SeveridadNivel;
}

/** Bloque de aislamiento del detalle de alerta (solo AISLAMIENTO_SOCIAL). */
export interface DetalleAislamiento {
  animalesAislados: AnimalAislado[];
  tsInicio: string;
  nombreLote: string | null;
}

/** Alerta geoespacial de aislamiento social: uno o más animales lejos del rodeo. */
export const esAislamiento = (a: { tipo?: string | null }) => /AISLAMIENTO/i.test(a.tipo ?? '');

/** Abigeato, fuera de perímetro y aislamiento: el mismo grupo que el contador "Geoespacial" y tipo=GEOESPACIAL. */
export const esGeoespacial = (a: { tipo?: string | null }) => {
  const t = (a.tipo ?? '').toUpperCase();
  return t.includes('FUERA') || t.includes('PERIMETRO') || t === 'ABIGEATO' || t === 'GEOESPACIAL' || t.includes('AISLAMIENTO');
};

/** Si la alerta pasa el filtro de tipo ('' = todos). Mismos valores que el `tipo` de GET /alertas. */
export const coincideFiltroTipo = (a: { tipo?: string | null }, filtro: string) =>
  !filtro || (filtro === 'GEOESPACIAL' ? esGeoespacial(a) : a.tipo === filtro);

/**
 * H0008: el backend marca la alerta al momento de enviarla. Como las listas no se refrescan solas,
 * además se re-evalúa con la última señal conocida para que una tarjeta abierta pase a "sin señal"
 * si la caravana deja de transmitir. Ante la duda advierte (puede adelantarse si la caravana transmitió
 * después; el detalle de la alerta siempre consulta el dato fresco).
 */
export function tieneDatoDesactualizado(alerta: AlertaResponse): boolean {
  if (alerta.datoPosiblementeDesactualizado) return true;
  if (!alerta.caravanaUltimaSenial || alerta.estado !== 'PENDIENTE') return false;
  const umbralMs = (alerta.horasUmbralDatoDesactualizado ?? 2) * 60 * 60 * 1000;
  return Date.now() - new Date(alerta.caravanaUltimaSenial).getTime() > umbralMs;
}

/** Alerta de anestro: disparador CELO, pero sin ventana de inseminación. Mira tipo por el mapper que manda el tipo crudo. */
export const esAnestro = (a: { subtipo?: string | null; tipo?: string | null }) =>
  /anestro/i.test(a.subtipo ?? a.tipo ?? '');

/** Acción recomendada de la alerta, con un texto genérico por tipo si el backend no la mandó. */
export const textoAccionRecomendada = (alerta: AlertaResponse | null) => {
  if (alerta?.accionRecomendada) {
    return alerta.accionRecomendada;
  } else if (alerta && esAnestro(alerta)) {
    return 'Revisión veterinaria: el animal no registra celo en el período configurado.';
  } else if (alerta && alerta.tipo === 'SALUD') {
    return 'Revisar al animal y realizar seguimiento clínico.';
  } else if (alerta && alerta.tipo === 'CELO') {
    return 'Realizar inseminación artificial dentro de la ventana óptima.';
  } else {
    return 'Revisar al animal y realizar seguimiento.';
  }
}

export async function getAlertas(
  establecimientoId: number,
  tipo?: string,
  severidad?: string,
  estado?: string
): Promise<AlertaResponse[]> {
  const params = new URLSearchParams();
  params.append('establecimientoId', establecimientoId.toString());
  if (tipo) params.append('tipo', tipo);
  if (severidad) params.append('severidad', severidad);
  if (estado) params.append('estado', estado);
  const response = await axiosClient.get<AlertaResponse[]>(`/alertas?${params.toString()}`);
  if (mockAislamiento && (!tipo || esAislamiento({ tipo })) && (!estado || estado === MOCK_ALERTA_AISLAMIENTO.estado)) {
    return [...response.data, MOCK_ALERTA_AISLAMIENTO];
  }
  return response.data;
}

export async function getAlertasCelo(establecimientoId: number, estado?: string): Promise<AlertaResponse[]> {
  const url = estado
    ? `/alertas/celo?establecimientoId=${establecimientoId}&estado=${estado}`
    : `/alertas/celo?establecimientoId=${establecimientoId}`;
  const response = await axiosClient.get<AlertaResponse[]>(url);
  return response.data;
}

export async function getAlertasSalud(establecimientoId: number, estado?: string): Promise<AlertaResponse[]> {
  const url = estado
    ? `/alertas/salud?establecimientoId=${establecimientoId}&estado=${estado}`
    : `/alertas/salud?establecimientoId=${establecimientoId}`;
  const response = await axiosClient.get<AlertaResponse[]>(url);
  return response.data;
}

/**
 * Returns the most recent PENDIENTE alert for a specific animal,
 * by fetching the full establishment alert list and filtering client-side.
 * Falls back to the most recent alert of any status if none is PENDIENTE.
 */
export async function getAlertaParaAnimal(
  establecimientoId: number,
  animalId: number
): Promise<AlertaResponse | null> {
  const todas = await getAlertasCelo(establecimientoId);
  const delAnimal = todas.filter(a => a.animalId === animalId);
  if (delAnimal.length === 0) return null;
  const pendiente = delAnimal.find(a => a.estado === 'PENDIENTE');
  return pendiente ?? delAnimal[0];
}

export async function updateAlertaEstado(
  alertaId: number,
  nuevoEstado: 'CONFIRMADA' | 'DESCARTADA',
  notas?: string
): Promise<AlertaResponse> {
  if (mockAislamiento && alertaId === MOCK_ALERTA_AISLAMIENTO.id) {
    MOCK_ALERTA_AISLAMIENTO.estado = nuevoEstado;
    return MOCK_ALERTA_AISLAMIENTO;
  }
  const url = notas
    ? `/alertas/estado/${alertaId}?estado=${nuevoEstado}&notas=${encodeURIComponent(notas)}`
    : `/alertas/estado/${alertaId}?estado=${nuevoEstado}`;
  const response = await axiosClient.put<AlertaResponse>(url);
  return response.data;
}

export async function archivarAlertaPorVencimiento(
  alertaId: number,
  establecimientoId: number
): Promise<AlertaResponse> {
  const response = await axiosClient.put<AlertaResponse>(
    `/alertas/${alertaId}/archivar?establecimientoId=${establecimientoId}`
  );
  return response.data;
}

export type SeveridadNivel = 'ALTA' | 'MEDIA' | 'BAJA';

/** Un punto de la serie comparativa "actual vs. perfil de referencia" (bloque de 6 h). */
export interface BloqueComparativo {
  bloque: string;
  actual: number | null;
  referenciaMedia: number | null;
  referenciaDesvio: number | null;
}

/** Bloque sanitario del detalle de alerta (solo cuando datoDisparador === 'SALUD'). */
export interface DetalleSanitario {
  desvioDetectado: boolean | null;
  severidad: SeveridadNivel | null;
  posibleCausa: string | null;
  descripcionDesvio: string | null;
  confianzaModelo: number | null;
  perfilCalculadoEn: string | null;
  ventanaDesde: string | null;
  ventanaHasta: string | null;
  serieActividad: BloqueComparativo[] | null;
  serieDesplazamiento: BloqueComparativo[] | null;
}

/** Bloque geoespacial del detalle de alerta (solo cuando datoDisparador === 'FUERA_DE_PERIMETRO'). */
export interface DetalleGeoespacial {
  /** Latitud de la última posición conocida (se actualiza mientras la alerta está pendiente). Puede ser null en alertas antiguas. */
  latitud: number | null;
  /** Longitud de la última posición conocida (se actualiza mientras la alerta está pendiente). Puede ser null en alertas antiguas. */
  longitud: number | null;
  /** Nombre del lote/rodeo al que pertenece el animal. */
  nombreLote: string | null;
  /** Segundos transcurridos desde que el animal salió del perímetro (calculado en el backend). */
  segundosFuera: number | null;
}

export interface AlertaDetalleResponse {
  id: number;
  animalId: number;
  animalCaravanaOficial: string;
  animalAlias: string;
  animalRaza: string;
  tsGeneracion: string;
  ventanaOptimaHoras: string;
  ventanaInicioIns: string;
  ventanaFinIns: string;
  datoDisparador: string;
  estadoActual: string;
  usuarioAccion?: string;
  accionRecomendada: string;
  ultimosEstadosReproductivos?: {
    id: number;
    animalId: number;
    estado: string;
    fechaUltimoParto: string;
    fechaUltimaInseminacion: string;
    origen: string;
    fechaRegistro: string;
    registradoPor: string;
  }[];
  /** Explicación en lenguaje natural del dato que originó la alerta (todos los tipos). */
  descripcionDatoOrigen?: string | null;
  /** Dato puntual y umbral superado. Se exponen por trazabilidad; la UI muestra solo la frase. */
  valorDetectado?: number | null;
  valorUmbral?: number | null;
  /** Presente solo para alertas sanitarias (datoDisparador === 'SALUD'). */
  detalleSanitario?: DetalleSanitario | null;
  /** Presente solo para alertas geoespaciales (datoDisparador === 'FUERA_DE_PERIMETRO'). */
  detalleGeoespacial?: DetalleGeoespacial | null;
  /** Presente solo para alertas AISLAMIENTO_SOCIAL. */
  detalleAislamiento?: DetalleAislamiento | null;
  /** Última transmisión de la caravana (null si no tiene caravana o nunca transmitió). */
  caravanaUltimaSenial?: string | null;
  /** True en alertas de celo pendientes cuya caravana no transmite hace más de `horasUmbralDatoDesactualizado`. */
  datoPosiblementeDesactualizado?: boolean;
  horasUmbralDatoDesactualizado?: number;
}

export async function getAlertaDetalle(alertaId: number, establecimientoId: number): Promise<AlertaDetalleResponse> {
  if (mockAislamiento && alertaId === MOCK_ALERTA_AISLAMIENTO.id) {
    return { ...MOCK_DETALLE_AISLAMIENTO, estadoActual: MOCK_ALERTA_AISLAMIENTO.estado };
  }
  const response = await axiosClient.get<AlertaDetalleResponse>(
    `/alertas/${alertaId}?establecimientoId=${establecimientoId}`
  );
  return response.data;
}

/**
 * Alertas sanitarias (tipo SALUD) de un animal concreto, más recientes primero.
 * El backend no expone un listado por animal, así que se trae el del
 * establecimiento vía el endpoint dedicado y se filtra por animalId en el
 * cliente (mismo criterio que {@link getAlertaParaAnimal} para celo).
 */
export async function getAlertasSanitariasAnimal(
  establecimientoId: number,
  animalId: number
): Promise<AlertaResponse[]> {
  const todas = await getAlertasSalud(establecimientoId);
  return todas
    .filter((a) => a.animalId === animalId)
    .sort((a, b) => new Date(b.tsGeneracion).getTime() - new Date(a.tsGeneracion).getTime());
}

export interface AlertaResumenResponse {
  reproductivo: number;
  sanitario: number;
  geoespacial: number;
}

export async function getResumenAlertas(establecimientoId: number): Promise<AlertaResumenResponse> {
  const response = await axiosClient.get<AlertaResumenResponse>(`/alertas/resumen?establecimientoId=${establecimientoId}`);
  return response.data;
}

/** Respuesta del endpoint GET /api/v1/alertas/{id}/ventana */
export interface VentanaInseminacionResponse {
  tiempoRestanteSegundos: number;
  porcentajeTranscurrido: number;
  proximaAVencer: boolean;
  estado: 'ACTIVA' | 'FUTURA' | 'VENCIDA';
}

export async function getVentanaInseminacion(
  alertaId: number,
  establecimientoId: number,
): Promise<VentanaInseminacionResponse> {
  const response = await axiosClient.get<VentanaInseminacionResponse>(
    `/alertas/${alertaId}/ventana`,
    { params: { establecimientoId } },
  );
  return response.data;
}

export type TipoIntervencion =
  | 'TRATAMIENTO'
  | 'VACUNACION'
  | 'REVISION_VETERINARIA'
  | 'INSEMINACION'
  | 'OTRO';

export type TipoEstadoSaludResultante = 'SANO' | 'EN_OBSERVACION' | 'ENFERMO';

export interface IntervencionRequest {
  descripcion?: string;
  fechaHora?: string; // ISO 8601 LocalDateTime
  tipo?: TipoIntervencion;
  /** Sólo para REVISION_VETERINARIA: estado de salud con el que concluye la revisión. */
  estadoSalud?: TipoEstadoSaludResultante;
}

export async function intervenirAlerta(
  alertaId: number,
  request: IntervencionRequest,
): Promise<AlertaResponse> {
  const response = await axiosClient.post<AlertaResponse>(
    `/alertas/intervenir/${alertaId}`,
    request,
  );
  return response.data;
}
