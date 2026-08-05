import axiosClient from './axiosClient';

export interface AlertaResponse {
  id: number;
  tipo: string;
  animalId: number;
  tsGeneracion: string;
  estado: string;
  gravedad?: 'GRAVE' | 'MEDIA' | 'LEVE';
}

export async function getAlertasCelo(establecimientoId: number, estado?: string): Promise<AlertaResponse[]> {
  const url = estado
    ? `/alertas/celo?establecimientoId=${establecimientoId}&estado=${estado}`
    : `/alertas/celo?establecimientoId=${establecimientoId}`;
  const response = await axiosClient.get<AlertaResponse[]>(url);
  return response.data;
}

/**
 * Returns the most recent PENDIENTE alert for a specific animal,
 * by fetching the full establishment alert list and filtering client-side.
 * Falls back to the most recent alert of any status if none is PENDIENTE.
 */
export async function getAlertaParaAnimal(
  establecimientoId: number,
  animalId: number
): Promise<AlertaResponse | null> {
  const todas = await getAlertasCelo(establecimientoId);
  const delAnimal = todas.filter(a => a.animalId === animalId);
  if (delAnimal.length === 0) return null;
  const pendiente = delAnimal.find(a => a.estado === 'PENDIENTE');
  return pendiente ?? delAnimal[0];
}

export async function updateAlertaEstado(
  alertaId: number,
  nuevoEstado: 'CONFIRMADA' | 'DESCARTADA',
  notas?: string
): Promise<AlertaResponse> {
  const url = notas
    ? `/alertas/estado/${alertaId}?estado=${nuevoEstado}&notas=${encodeURIComponent(notas)}`
    : `/alertas/estado/${alertaId}?estado=${nuevoEstado}`;
  const response = await axiosClient.put<AlertaResponse>(url);
  return response.data;
}

export interface AlertaDetalleResponse {
  id: number;
  animalId: number;
  animalCaravanaOficial: string;
  animalAlias: string;
  animalRaza: string;
  tsGeneracion: string;
  ventanaOptimaHoras: string;
  ventanaInicioIns: string;
  ventanaFinIns: string;
  datoDisparador: string;
  estadoActual: string;
  usuarioAccion?: string;
  ultimosEstadosReproductivos?: {
    id: number;
    animalId: number;
    estado: string;
    fechaUltimoParto: string;
    fechaUltimaInseminacion: string;
    origen: string;
    fechaRegistro: string;
    registradoPor: string;
  }[];
}

export async function getAlertaDetalle(alertaId: number, establecimientoId: number): Promise<AlertaDetalleResponse> {
  const response = await axiosClient.get<AlertaDetalleResponse>(
    `/alertas/${alertaId}?establecimientoId=${establecimientoId}`
  );
  return response.data;
}

export interface AlertaResumenResponse {
  reproductivo: number;
  sanitario: number;
  geoespacial: number;
}

export async function getResumenAlertas(establecimientoId: number): Promise<AlertaResumenResponse> {
  const response = await axiosClient.get<AlertaResumenResponse>(`/alertas/resumen?establecimientoId=${establecimientoId}`);
  return response.data;
}
