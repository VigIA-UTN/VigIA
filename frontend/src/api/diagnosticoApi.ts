import axiosClient from './axiosClient';

export interface DiagnosticoResponse {
  id: number;
  alertaId: number;
  animalId: number;
  animalAlias: string;
  animalCaravanaOficial: string;
  establecimientoNombre: string;
  diagnostico: string;
  tratamiento: string;
  fechaRegistro: string; // ISO 8601
  veterinarioNombre: string;
  disparador: string;
  datoOrigen: string;
}

export interface DiagnosticoRequest {
  diagnostico: string;
  tratamiento: string;
}

/** Diagnósticos de los establecimientos del usuario, más recientes primero. */
export async function getDiagnosticos(params?: { animalId?: number }): Promise<DiagnosticoResponse[]> {
  const response = await axiosClient.get<DiagnosticoResponse[]>('/alertas/diagnosticos', { params });
  return response.data;
}

/** Registra un diagnóstico sobre una alerta sanitaria confirmada (solo VETERINARIO). */
export async function crearDiagnostico(alertaId: number, body: DiagnosticoRequest): Promise<DiagnosticoResponse> {
  const response = await axiosClient.post<DiagnosticoResponse>(`/alertas/${alertaId}/diagnosticos`, body);
  return response.data;
}

/** Texto a mostrar ante un error: el backend responde texto plano en 400/403 propios. */
export function mensajeErrorDiagnostico(err: unknown): string {
  const data = (err as { response?: { data?: unknown } })?.response?.data;
  if (typeof data === 'string' && data.trim()) return data;
  const message = (data as { message?: unknown } | undefined)?.message;
  if (typeof message === 'string' && message.trim()) return message;
  return 'No se pudo registrar el diagnóstico.';
}

/** Filtro de DiagnosticosPage: búsqueda por alias o caravana y establecimiento por nombre. */
export function filtrarDiagnosticos(
  rows: DiagnosticoResponse[],
  filters: { search: string; establecimiento: string },
): DiagnosticoResponse[] {
  const q = filters.search.trim().toLowerCase();
  const est = filters.establecimiento;
  return rows
    .filter((d) => est === 'Todos los establecimientos' || d.establecimientoNombre === est)
    .filter((d) => !q
      || (d.animalAlias ?? '').toLowerCase().includes(q)
      || (d.animalCaravanaOficial ?? '').toLowerCase().includes(q));
}
