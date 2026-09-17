import axiosClient from './axiosClient';

export type EstadoSensor = 'DISPONIBLE' | 'VINCULADO' | 'INACTIVO';

export interface CaravanaRequest {
  dev_eui: string;
  idCaravanaOficial?: string | null;
  alias?: string | null;
  establecimientoId: number;
}

export interface CaravanaResponse {
  id: number;
  codigo: string | null;
  alias: string | null;
  fechaAsignacion: string | null;
  fechaAlta: string;
  devEui: string;
  activo: boolean;
  estadoSensor: EstadoSensor;
  establecimientoId: number;
  establecimientoNombre: string | null;
  animalId: number | null;
  animalAlias: string | null;
  bateria: number | null;
  ultimaSenial: string | null;
}

export async function crearCaravana(data: CaravanaRequest): Promise<CaravanaResponse> {
  const response = await axiosClient.post<CaravanaResponse>('/caravana', data);
  return response.data;
}

/**
 * `establecimientoId` en `null`/`undefined` lista caravanas de todos los establecimientos —
 * el backend sólo lo permite para ADMINISTRADOR.
 */
export async function getCaravanas(
  establecimientoId: number | null | undefined,
  estado?: EstadoSensor,
  codigo?: string,
): Promise<CaravanaResponse[]> {
  const params = new URLSearchParams();
  if (establecimientoId) params.set('establecimientoId', String(establecimientoId));
  if (estado) params.set('estado', estado);
  if (codigo) params.set('codigo', codigo);
  const response = await axiosClient.get<CaravanaResponse[]>(`/caravana?${params.toString()}`);
  return response.data;
}

export async function getCaravanasDisponibles(establecimientoId: number): Promise<CaravanaResponse[]> {
  const response = await axiosClient.get<CaravanaResponse[]>(`/caravana/disponibles?establecimientoId=${establecimientoId}`);
  return response.data;
}

export async function darDeBajaCaravana(id: number): Promise<CaravanaResponse> {
  const response = await axiosClient.patch<CaravanaResponse>(`/caravana/${id}/baja`);
  return response.data;
}

export async function reactivarCaravana(id: number): Promise<CaravanaResponse> {
  const response = await axiosClient.patch<CaravanaResponse>(`/caravana/${id}/reactivar`);
  return response.data;
}
