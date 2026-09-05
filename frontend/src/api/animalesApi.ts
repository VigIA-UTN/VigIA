import type { AlertaResponse } from './alertaApi';
import axiosClient from './axiosClient';

export type EstadoAnimal = 'ACTIVO' | 'CON_ALERTA' | 'INACTIVO';

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
  enCalibracion?: boolean;
  diasRestantesCalibracion?: number;
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

export async function cargarAnimalesExcel(file: File, establecimientoId: number): Promise<CargaMasivaResponse> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('establecimientoId', establecimientoId.toString());
  const response = await axiosClient.post<CargaMasivaResponse>('/animales/csv', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
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
  });
  return response.data;
}

export type TipoReproductivo = 'VACIA' | 'GESTANTE' | 'CELO' | 'ANESTRO';

export interface EstadoReproductivoRequest {
  estado: TipoReproductivo;
  fechaUltimoParto?: string | null;
  fechaUltimaInseminacion?: string | null;
}

export interface EstadoReproductivoResponse {
  id: number;
  animalId: number;
  estado: string;
  fechaUltimoParto: string | null;
  fechaUltimaInseminacion: string | null;
  origen: string | null;
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
