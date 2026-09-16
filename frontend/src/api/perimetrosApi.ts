import axiosClient from './axiosClient';

export interface CoordenadaDTO {
  latitud: number;
  longitud: number;
}

export interface PerimetroResponse {
  id: number;
  establecimientoId: number;
  establecimientoNombre: string;
  nombre: string;
  descripcion?: string;
  activo: boolean;
  loteIds: number[];
  coordenadas: CoordenadaDTO[];
  fechaCreacion?: string;
  fechaModificacion?: string;
  version?: number;
}

export interface PerimetroRequest {
  nombre: string;
  descripcion?: string;
  establecimientoId: number;
  loteIds: number[];
  coordenadas: CoordenadaDTO[];
}

export async function getPerimetros(establecimientoId: number): Promise<PerimetroResponse[]> {
  const response = await axiosClient.get<PerimetroResponse[]>(`/perimetros?establecimientoId=${establecimientoId}`);
  return response.data;
}

export async function getPerimetroById(id: number): Promise<PerimetroResponse> {
  const response = await axiosClient.get<PerimetroResponse>(`/perimetros/${id}`);
  return response.data;
}

export async function crearPerimetro(request: PerimetroRequest): Promise<PerimetroResponse> {
  const response = await axiosClient.post<PerimetroResponse>('/perimetros', request);
  return response.data;
}

export async function actualizarPerimetro(id: number, request: PerimetroRequest): Promise<PerimetroResponse> {
  const response = await axiosClient.put<PerimetroResponse>(`/perimetros/${id}`, request);
  return response.data;
}

export async function activarPerimetro(id: number): Promise<void> {
  await axiosClient.patch(`/perimetros/${id}/activar`);
}

export async function desactivarPerimetro(id: number): Promise<void> {
  await axiosClient.patch(`/perimetros/${id}/desactivar`);
}

export async function eliminarPerimetro(id: number): Promise<void> {
  await axiosClient.delete(`/perimetros/${id}`);
}
