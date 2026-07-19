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
}

export interface CargaMasivaResponse {
  procesados: number;
  exitosos: number;
  fallidos: number;
  animales: AnimalResponse[];
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

export async function getAnimales(): Promise<AnimalResponse[]> {
  const response = await axiosClient.get<AnimalResponse[]>(`/animales`);
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

export async function cargarEstadosExcel(file: File): Promise<CargaMasivaEstadoResponse> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await axiosClient.post<CargaMasivaEstadoResponse>('/animales/estado-reproductivo/csv', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return response.data;
}
