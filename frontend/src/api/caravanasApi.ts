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
  animalId: number | null;
  animalAlias: string | null;
}

export async function crearCaravana(data: CaravanaRequest): Promise<CaravanaResponse> {
  const response = await axiosClient.post<CaravanaResponse>('/caravana', data);
  return response.data;
}

export async function getCaravanas(establecimientoId: number): Promise<CaravanaResponse[]> {
  const response = await axiosClient.get<CaravanaResponse[]>(`/caravana?establecimientoId=${establecimientoId}`);
  return response.data;
}

export async function getCaravanasDisponibles(establecimientoId: number): Promise<CaravanaResponse[]> {
  const response = await axiosClient.get<CaravanaResponse[]>(`/caravana/disponibles?establecimientoId=${establecimientoId}`);
  return response.data;
}
