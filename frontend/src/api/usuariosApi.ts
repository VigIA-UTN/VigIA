import axiosClient from './axiosClient';

export interface UsuarioResponse {
  id: number;
  nombre: string;
  apellido: string;
  email: string;
  rol: string;
  activo: boolean;
  establecimiento?: string;
  establecimientoId?: number;
  proveedorAuth?: string;
}

export async function getTodosUsuarios(): Promise<UsuarioResponse[]> {
  const response = await axiosClient.get<UsuarioResponse[]>('/usuarios');
  return response.data;
}

export async function toggleEstadoUsuario(id: number): Promise<void> {
  await axiosClient.patch(`/usuarios/${id}/estado`);
}

export async function getEmpleados(establecimientoId?: number): Promise<UsuarioResponse[]> {
  const url = establecimientoId ? `/usuarios/empleados?establecimientoId=${establecimientoId}` : '/usuarios/empleados';
  const response = await axiosClient.get<UsuarioResponse[]>(url);
  return response.data;
}

export async function removerEmpleado(id: number, establecimientoId: number): Promise<void> {
  await axiosClient.delete(`/usuarios/empleados/${establecimientoId}/${id}`);
}

export async function getMiPerfil(): Promise<UsuarioResponse> {
  const response = await axiosClient.get<UsuarioResponse>('/usuarios/me');
  return response.data;
}

export async function updateMiPerfil(data: { nombre: string; apellido: string }): Promise<UsuarioResponse> {
  const response = await axiosClient.put<UsuarioResponse>('/usuarios/me', data);
  return response.data;
}

export async function cambiarPassword(data: { passwordActual: string; nuevaPassword: string }): Promise<void> {
  await axiosClient.put('/usuarios/me/password', data);
}