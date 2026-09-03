import axiosClient from './axiosClient';

export interface InvitacionResponse {
  id?: number;
  nombreCompleto?: string;
  emailDestino: string;
  rol: string;
  establecimiento?: string;
  token?: string;
  fechaCreacion?: string;
  fechaExpiracion?: string;
  estado: string;
}

export interface InvitacionProductorRequest {
  email: string;
  establecimientoId: number;
}

export interface InvitacionEmpleadoRequest {
  email: string;
  establecimientoId: number;
  rol: 'VETERINARIO' | 'CAMPERO';
}

export async function getInvitacionesProductor(): Promise<InvitacionResponse[]> {
  const response = await axiosClient.get<InvitacionResponse[]>('/invitaciones/productor');
  return response.data;
}

export async function crearInvitacionProductor(
  data: InvitacionProductorRequest
): Promise<InvitacionResponse> {
  const response = await axiosClient.post<InvitacionResponse>('/invitaciones/productor', data);
  return response.data;
}

export async function getInvitacionesEmpleado(establecimientoId?: number): Promise<InvitacionResponse[]> {
  const url = establecimientoId ? `/invitaciones/empleado?establecimientoId=${establecimientoId}` : '/invitaciones/empleado';
  const response = await axiosClient.get<InvitacionResponse[]>(url);
  return response.data;
}

export async function crearInvitacionEmpleado(
  data: InvitacionEmpleadoRequest
): Promise<InvitacionResponse> {
  const response = await axiosClient.post<InvitacionResponse>('/invitaciones/empleado', data);
  return response.data;
}

export async function aceptarInvitacionPorTokenRequest(token: string): Promise<void> {
  await axiosClient.post(`/invitaciones/token/${token}/aceptar`);
}

export async function listarMisInvitacionesRequest(): Promise<InvitacionResponse[]> {
  const response = await axiosClient.get<InvitacionResponse[]>('/invitaciones/mis-invitaciones');
  return response.data;
}

export async function aceptarInvitacionRequest(id: number): Promise<void> {
  await axiosClient.post(`/invitaciones/${id}/aceptar`);
}

export async function rechazarInvitacionRequest(id: number): Promise<void> {
  await axiosClient.post(`/invitaciones/${id}/rechazar`);
}

export async function eliminarInvitacionRequest(id: number): Promise<void> {
  await axiosClient.delete(`/invitaciones/${id}`);
}

