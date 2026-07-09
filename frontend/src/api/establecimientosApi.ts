import axiosClient from './axiosClient';

export interface EstablecimientoRequest {
  nombre: string;
  latitud: number;
  longitud: number;
}

export interface EstablecimientoResponse {
  id: number;
  nombre: string;
  latitud: number;
  longitud: number;
  activo: boolean;
  fechaAlta: string;
}

export async function getEstablecimientos(): Promise<EstablecimientoResponse[]> {
  const response = await axiosClient.get<EstablecimientoResponse[]>('/establecimientos');
  return response.data;
}

export async function createEstablecimiento(data: EstablecimientoRequest): Promise<EstablecimientoResponse> {
  const response = await axiosClient.post<EstablecimientoResponse>('/establecimientos', data);
  return response.data;
}

export async function desactivarEstablecimiento(id: number): Promise<EstablecimientoResponse> {
  const response = await axiosClient.delete<EstablecimientoResponse>(`/establecimientos/${id}`);
  return response.data;
}

export async function updateEstablecimiento(id: number, data: EstablecimientoRequest): Promise<EstablecimientoResponse> {
  const response = await axiosClient.put<EstablecimientoResponse>(`/establecimientos/${id}`, data);
  return response.data;
}
