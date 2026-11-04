import type { AlertaResponse } from './alertaApi';
import axiosClient from './axiosClient';

export type EstadoAnimal = 'ACTIVO' | 'CON_ALERTA' | 'INACTIVO';

export type TipoEstadoSalud = 'SANO' | 'EN_OBSERVACION' | 'ENFERMO';

/** Etiquetas legibles para los valores de estado de salud (evita mostrar el enum crudo). */
export const ESTADO_SALUD_LABEL: Record<string, string> = {
  SANO: 'Sano',
  EN_OBSERVACION: 'En observación',
  ENFERMO: 'Enfermo',
};

export interface AnimalResponse {
  id: number;
  idCaravanaOficial: string | null;
  alias: string | null;
  fechaNacimiento: string;
  raza: string | null;
  estadoActual: EstadoAnimal;
  rodeoLoteId: number | null;
  rodeoLoteNombre: string | null;
  establecimientoId: number;
  fechaAlta: string;
  estadoReproductivo?: string | null;
  fechaUltimoParto?: string | null;
  fechaUltimaInseminacion?: string | null;
  estadoSalud?: TipoEstadoSalud | null;
  enCalibracion?: boolean;
  diasRestantesCalibracion?: number;
  caravanaId: number | null;
  caravanaDevEui: string | null;
  caravanaCodigo: string | null;
  caravanaAlias: string | null;
  caravanaEstado: 'DISPONIBLE' | 'VINCULADO' | 'INACTIVO' | null;
  caravanaUltimaSenial: string | null;
  caravanaBateria: number | null;
}

export interface CargaMasivaResponse {
  procesados: number;
  exitosos: number;
  fallidos: number;
  animales: AnimalResponse[];
  errores?: string[];
}

export interface FilaEstadoInvalida {
  fila: number;
  error: string;
}

export interface CargaMasivaEstadoResponse {
  procesados: number;
  exitosos: number;
  fallidos: number;
  errores: FilaEstadoInvalida[];
}

export interface AnimalRequest {
  idCaravanaOficial: string | null;
  alias: string | null;
  fechaNacimiento: string;
  raza: string | null;
  rodeoLoteId: number | null;
  estadoActual: EstadoAnimal;
  establecimientoId: number;
  /** Peso inicial en kg (opcional). Si se provee, se crea el primer registro de peso. */
  pesoInicial?: number | null;
  caravanaId?: number | null;
}

export async function getAnimales(establecimientoId?: number): Promise<AnimalResponse[]> {
  const url = establecimientoId ? `/animales?establecimientoId=${establecimientoId}` : `/animales`;
  const response = await axiosClient.get<AnimalResponse[]>(url);
  return response.data;
}

export async function getAnimalById(id: number): Promise<AnimalResponse> {
  const response = await axiosClient.get<AnimalResponse>(`/animales/${id}`);
  return response.data;
}

export async function getAnimalesByRodeoLote(rodeoLoteId: number): Promise<AnimalResponse[]> {
  const response = await axiosClient.get<AnimalResponse[]>(`/animales/rodeoLote/${rodeoLoteId}`);
  return response.data;
}

export interface AnimalMapa {
  id: number;
  identificador: string | null;
  latitud: number | null;
  longitud: number | null;
  tsUltimaPosicion: string | null;
  sinDatosRecientes: boolean;
  alertasAnimal: AlertaResponse[]
}

export async function getAnimalesMapa(establecimientoId?: number): Promise<AnimalMapa[]> {
  const url = establecimientoId ? `/animales/mapa?establecimientoId=${establecimientoId}` : `/animales/mapa`;
  const response = await axiosClient.get<AnimalMapa[]>(url);
  return response.data;
}

export async function crearAnimal(data: AnimalRequest): Promise<AnimalResponse> {
  const response = await axiosClient.post<AnimalResponse>('/animales', data);
  return response.data;
}

export async function desactivarAnimal(id: number): Promise<void> {
  await axiosClient.delete(`/animales/${id}`);
}

export async function updateAnimal(id: number, data: AnimalRequest): Promise<AnimalResponse> {
  const response = await axiosClient.put<AnimalResponse>(`/animales/${id}`, data);
  return response.data;
}

export async function vincularCaravana(animalId: number, caravanaId: number): Promise<AnimalResponse> {
  const response = await axiosClient.post<AnimalResponse>(`/animales/${animalId}/vincular-caravana`, { caravanaId });
  return response.data;
}

export async function desvincularCaravana(animalId: number): Promise<AnimalResponse> {
  const response = await axiosClient.delete<AnimalResponse>(`/animales/${animalId}/desvincular-caravana`);
  return response.data;
}

/**
 * Asigna al azar las caravanas disponibles a los animales sin caravana; con rodeoLoteId, solo a los de ese lote.
 * Devuelve los animales que recibieron una.
 */
export async function asignarCaravanasAleatoriamente(establecimientoId: number, rodeoLoteId?: number): Promise<AnimalResponse[]> {
  const response = await axiosClient.post<AnimalResponse[]>(`/animales/asignar-caravanas`, null, { params: { establecimientoId, rodeoLoteId } });
  return response.data;
}

/** Planilla .xlsx animal → sensor. Sin animalIds incluye todos los animales con caravana del establecimiento. */
export async function descargarAsignacionesCaravanasExcel(establecimientoId: number, animalIds?: number[]): Promise<Blob> {
  const response = await axiosClient.post<Blob>(`/animales/asignaciones-caravanas/excel`, animalIds ?? null, {
    params: { establecimientoId },
    responseType: 'blob',
  });
  return response.data;
}

// Con filas inválidas las cargas masivas responden 422 con el mismo reporte (errores por fila): no es una falla
// del pedido, es el resultado que muestra el modal.
const aceptaReporteConErrores = (status: number) => (status >= 200 && status < 300) || status === 422;

export async function cargarAnimalesExcel(file: File, establecimientoId: number): Promise<CargaMasivaResponse> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('establecimientoId', establecimientoId.toString());
  const response = await axiosClient.post<CargaMasivaResponse>('/animales/csv', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    validateStatus: aceptaReporteConErrores,
  });
  return response.data;
}

export async function cargarEstadosExcel(file: File, establecimientoId: number): Promise<CargaMasivaEstadoResponse> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('establecimientoId', establecimientoId.toString());
  const response = await axiosClient.post<CargaMasivaEstadoResponse>('/animales/estado-reproductivo/csv', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    validateStatus: aceptaReporteConErrores,
  });
  return response.data;
}

export type TipoReproductivo =
  | 'VACIA'
  | 'GESTANTE'
  | 'CELO'
  | 'ANESTRO'
  | 'POSIBLE_CELO'
  | 'INSEMINADA'
  | 'PARIDA';

/** Etiquetas legibles para los valores de estado reproductivo (evita mostrar el enum crudo). */
export const ESTADO_REPRODUCTIVO_LABEL: Record<string, string> = {
  VACIA: 'Vacía',
  GESTANTE: 'Gestante',
  CELO: 'Celo',
  ANESTRO: 'Anestro',
  POSIBLE_CELO: 'Posible celo',
  INSEMINADA: 'Inseminada',
  PARIDA: 'Parida',
};

export interface EstadoReproductivoRequest {
  estado: TipoReproductivo;
  fechaUltimoParto?: string | null;
  fechaUltimaInseminacion?: string | null;
}

export interface EstadoReproductivoResponse {
  id: number;
  animalId: number;
  estado: string | null;
  fechaUltimoParto: string | null;
  fechaUltimaInseminacion: string | null;
  origen: string | null; // MANUAL | CARGA_MASIVA | AUTOMATICO
  fechaRegistro: string;
  registradoPor: string | null;
}

export async function registrarEstadoReproductivo(
  animalId: number,
  data: EstadoReproductivoRequest
): Promise<EstadoReproductivoResponse> {
  const response = await axiosClient.post<EstadoReproductivoResponse>(
    `/animales/${animalId}/estado-reproductivo`,
    data
  );
  return response.data;
}

export async function editarEstadoReproductivo(
  animalId: number,
  estadoId: number,
  data: EstadoReproductivoRequest
): Promise<EstadoReproductivoResponse> {
  const response = await axiosClient.put<EstadoReproductivoResponse>(
    `/animales/${animalId}/estado-reproductivo/${estadoId}`,
    data
  );
  return response.data;
}

export async function getHistorialEstados(animalId: number): Promise<EstadoReproductivoResponse[]> {
  const response = await axiosClient.get<EstadoReproductivoResponse[]>(
    `/animales/${animalId}/estado-reproductivo`
  );
  return response.data;
}

export interface IntervencionResponse {
  id: number;
  alertaId: number | null;
  animalId: number;
  interventor: string;
  descripcion: string;
  fechaHora: string;
  tipo: 'TRATAMIENTO' | 'VACUNACION' | 'REVISION_VETERINARIA' | 'INSEMINACION' | 'OTRO';
}

export async function getIntervenciones(animalId: number): Promise<IntervencionResponse[]> {
  const response = await axiosClient.get<IntervencionResponse[]>(`/animales/${animalId}/intervenciones`);
  return response.data;
}

/** Todas las intervenciones del establecimiento, de la más reciente a la más antigua. */
export async function getIntervencionesEstablecimiento(establecimientoId: number): Promise<IntervencionResponse[]> {
  const response = await axiosClient.get<IntervencionResponse[]>(`/establecimientos/${establecimientoId}/intervenciones`);
  return response.data;
}

export interface AnimalMetricaResponse {
  fecha: string;
  actividadMotora: number | null;
  latitud: number | null;
  longitud: number | null;
  sinDatos: boolean;
}

export async function getMetricasAnimal(animalId: number): Promise<AnimalMetricaResponse[]> {
  const response = await axiosClient.get<AnimalMetricaResponse[]>(`/animales/${animalId}/metricas`);
  return response.data;
}

export interface EstadoSaludRequest {
  estado: TipoEstadoSalud;
  diagnosticoNotas?: string | null;
  tratamientoIndicado?: string | null;
  /** YYYY-MM-DD. Si es null/undefined, el backend usa la fecha de hoy. */
  fecha?: string | null;
}

export interface EstadoSaludResponse {
  id: number;
  animalId: number;
  estado: string | null;
  fechaRevision: string | null;
  diagnosticoNotas: string | null;
  tratamientoIndicado: string | null;
  origen: string | null;
  fechaRegistro: string;
  registradoPor: string | null;
}

export async function registrarEstadoSalud(
  animalId: number,
  data: EstadoSaludRequest
): Promise<EstadoSaludResponse> {
  const response = await axiosClient.post<EstadoSaludResponse>(
    `/animales/${animalId}/estado-salud`,
    data
  );
  return response.data;
}

export async function getHistorialSalud(animalId: number): Promise<EstadoSaludResponse[]> {
  const response = await axiosClient.get<EstadoSaludResponse[]>(
    `/animales/${animalId}/estado-salud`
  );
  return response.data;
}

// ─── Historial de Pesos ───────────────────────────────────────────────────────

export interface RegistroPesoRequest {
  valorKg: number;
  /** 'kg' | 'lbs' — la unidad en la que se registra el peso */
  unidad: string;
  /** YYYY-MM-DD. Si es null/undefined, el backend usa la fecha de hoy. */
  fecha?: string | null;
}

export interface RegistroPesoResponse {
  id: number;
  animalId: number;
  fecha: string;
  valorKg: number;
  unidad: string;
  fechaRegistro: string;
}

export async function getHistorialPesos(animalId: number): Promise<RegistroPesoResponse[]> {
  const response = await axiosClient.get<RegistroPesoResponse[]>(
    `/animales/${animalId}/historial-pesos`
  );
  return response.data;
}

/** Todas las pesadas de los animales del establecimiento, la más reciente primero. */
export async function getPesosEstablecimiento(establecimientoId: number): Promise<RegistroPesoResponse[]> {
  const response = await axiosClient.get<RegistroPesoResponse[]>(`/establecimientos/${establecimientoId}/pesos`);
  return response.data;
}

export async function registrarPeso(
  animalId: number,
  data: RegistroPesoRequest
): Promise<RegistroPesoResponse> {
  const response = await axiosClient.post<RegistroPesoResponse>(
    `/animales/${animalId}/historial-pesos`,
    data
  );
  return response.data;
}

export async function editarPeso(
  animalId: number,
  pesoId: number,
  data: RegistroPesoRequest
): Promise<RegistroPesoResponse> {
  const response = await axiosClient.put<RegistroPesoResponse>(
    `/animales/${animalId}/historial-pesos/${pesoId}`,
    data
  );
  return response.data;
}

export async function eliminarPeso(animalId: number, pesoId: number): Promise<void> {
  await axiosClient.delete(`/animales/${animalId}/historial-pesos/${pesoId}`);
}

// ─── Línea de tiempo de alertas ───────────────────────────────────────────────

export interface LineaTiempoEventoResponse {
  tipoEvento: string;
  id: number;
  fecha: string
  tipo: string | null;
  /** Tipo crudo de la alerta ('Anestro Detectado', ...); `tipo` lleva el disparador. */
  subtipo?: string | null;
  severidad: 'ALTA' | 'MEDIA' | 'BAJA' | null;
  /** Valor medido y umbral del disparador (en el anestro: días sin ciclo y días configurados). */
  valorDetectado?: number | null;
  valorUmbral?: number | null;
  estadoResolucion: string | null;
  /** Quién confirmó o descartó la alerta y cuándo; null mientras está pendiente. */
  usuarioResolucion?: string | null;
  tsResolucion?: string | null;
  tipoIntervencion: string | null;
  descripcion: string | null;
  interventor: string | null;
  alertaAsociadaId: number | null;
}

export interface LineaTiempoFiltros {
  tipo?: string;
  desde?: string; // ISO-8601 datetime
  hasta?: string; // ISO-8601 datetime
}

export async function getLineaTiempoAlertas(
  animalId: number,
  filtros?: LineaTiempoFiltros
): Promise<LineaTiempoEventoResponse[]> {
  const params = new URLSearchParams();
  if (filtros?.tipo) params.append('tipo', filtros.tipo);
  if (filtros?.desde) params.append('desde', filtros.desde);
  if (filtros?.hasta) params.append('hasta', filtros.hasta);
  const query = params.toString();
  const url = `/animales/${animalId}/linea-tiempo-alertas${query ? `?${query}` : ''}`;
  const response = await axiosClient.get<LineaTiempoEventoResponse[]>(url);
  return response.data;
}

// ─── Indicadores de salud ──────────────────────────────────────────────────

export type EstadoIndicador = 'VERDE' | 'AMARILLO' | 'ROJO' | 'SIN DATOS';

export interface IndicadorSaludResponse {
  nombre: string;
  descripcion: string;
  valor: string;
  estado: EstadoIndicador;
}

export interface AnimalIndicadoresSaludResponse {
  animalId: number;
  caravana: string | null;
  indicadores: IndicadorSaludResponse[];
}

export async function getIndicadoresSalud(animalId: number): Promise<AnimalIndicadoresSaludResponse> {
  const response = await axiosClient.get<AnimalIndicadoresSaludResponse>(
    `/animales/${animalId}/indicadores-salud`
  );
  return response.data;
}
