import axiosClient from './axiosClient';

export interface RodeoLoteResponse {
  id: number;
  nombre: string;
  establecimientoId: number;
  activo: boolean;
  fechaAlta: string;
}

export async function getRodeoLotes(): Promise<RodeoLoteResponse[]> {
  const response = await axiosClient.get<RodeoLoteResponse[]>('/rodeo-lotes');
  return response.data;
}

export async function crearRodeoLote(nombre: string, establecimientoId: number): Promise<RodeoLoteResponse> {
  const response = await axiosClient.post<RodeoLoteResponse>('/rodeo-lotes/crear', { nombre, establecimientoId });
  return response.data;
}
