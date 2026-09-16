import axiosClient from './axiosClient';

export interface RodeoLoteResponse {
  id: number;
  nombre: string;
  establecimientoId: number;
  activo: boolean;
  fechaAlta: string;
}

export async function getRodeoLotes(establecimientoId?: number): Promise<RodeoLoteResponse[]> {
  const url = establecimientoId ? `/rodeo-lotes?establecimientoId=${establecimientoId}` : '/rodeo-lotes';
  const response = await axiosClient.get<RodeoLoteResponse[]>(url);
  return response.data;
}

export async function crearRodeoLote(nombre: string, establecimientoId: number): Promise<RodeoLoteResponse> {
  const response = await axiosClient.post<RodeoLoteResponse>('/rodeo-lotes', { nombre, establecimientoId });
  return response.data;
}
