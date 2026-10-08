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
  diasDeteccionAnestro: number;
  severidadAnestro: string;
}

/** Forma del response del endpoint dedicado de anestro. */
export interface ConfigAnestroResponse {
  establecimientoId: number;
  nombreEstablecimiento: string;
  diasDeteccionAnestro: number;
  severidadAnestro: string;
}

/** Forma del request del endpoint dedicado de anestro. */
export interface ConfigAnestroRequest {
  diasDeteccionAnestro: number;
  severidadAnestro: string;
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

export async function activarEstablecimiento(id: number): Promise<EstablecimientoResponse> {
  const response = await axiosClient.put<EstablecimientoResponse>(`/establecimientos/activar/${id}`);
  return response.data;
}

export async function updateEstablecimiento(id: number, data: EstablecimientoRequest): Promise<EstablecimientoResponse> {
  const response = await axiosClient.put<EstablecimientoResponse>(`/establecimientos/${id}`, data);
  return response.data;
}

/**
 * Consulta los parámetros de anestro de un establecimiento.
 * Solo accesible para PRODUCTOR y VETERINARIO del establecimiento.
 */
export async function getConfigAnestro(id: number): Promise<ConfigAnestroResponse> {
  const response = await axiosClient.get<ConfigAnestroResponse>(`/establecimientos/${id}/anestro`);
  return response.data;
}

/**
 * Actualiza los parámetros de anestro de un establecimiento.
 * Solo accesible para PRODUCTOR y VETERINARIO del establecimiento.
 */
export async function updateConfigAnestro(id: number, data: ConfigAnestroRequest): Promise<ConfigAnestroResponse> {
  const response = await axiosClient.put<ConfigAnestroResponse>(`/establecimientos/${id}/anestro`, data);
  return response.data;
}
