import axiosClient from './axiosClient';

export type EstadoAnimal = 'ACTIVO' | 'CON_ALERTA' | 'INACTIVO';

export interface AnimalResponse {
  id: number;
  idCaravanaOficial: string | null;
  alias: string | null;
  fechaNacimiento: string; // ISO date string
  sexo: string | null;
  raza: string | null;
  estadoActual: EstadoAnimal;
  rodeoLoteId: number | null;
  establecimientoId: number;
  fechaAlta: string;
}

export interface AnimalRequest {
  idCaravanaOficial: string | null;
  alias: string | null;
  fechaNacimiento: string; // 'YYYY-MM-DD'
  sexo: string | null;
  raza: string | null;
  rodeoLoteId: number | null;
  estadoActual: EstadoAnimal;
  establecimientoId: number;
}

export async function getAnimales(): Promise<AnimalResponse[]> {
  const response = await axiosClient.get<AnimalResponse[]>(`/animales`);
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
