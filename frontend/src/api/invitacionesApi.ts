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

export async function getInvitacionesEmpleado(): Promise<InvitacionResponse[]> {
  const response = await axiosClient.get<InvitacionResponse[]>('/invitaciones/empleado');
  return response.data;
}

export async function crearInvitacionEmpleado(
  data: InvitacionEmpleadoRequest
): Promise<InvitacionResponse> {
  const response = await axiosClient.post<InvitacionResponse>('/invitaciones/empleado', data);
  return response.data;
}
