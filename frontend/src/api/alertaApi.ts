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

export async function updateAlertaEstado(
  alertaId: number,
  nuevoEstado: 'CONFIRMADA' | 'DESCARTADA'
): Promise<AlertaResponse> {
  const response = await axiosClient.put<AlertaResponse>(
    `/alertas/estado/${alertaId}?estado=${nuevoEstado}`
  );
  return response.data;
}
