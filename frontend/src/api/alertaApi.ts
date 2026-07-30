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
