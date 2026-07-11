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
}

export async function getTodosUsuarios(): Promise<UsuarioResponse[]> {
  const response = await axiosClient.get<UsuarioResponse[]>('/usuarios');
  return response.data;
}

export async function toggleEstadoUsuario(id: number): Promise<void> {
  await axiosClient.patch(`/usuarios/${id}/estado`);
}

export async function getEmpleados(): Promise<UsuarioResponse[]> {
  const response = await axiosClient.get<UsuarioResponse[]>('/usuarios/empleados');
  return response.data;
}

export async function removerEmpleado(id: number, establecimientoId: number): Promise<void> {
  await axiosClient.delete(`/usuarios/empleados/${establecimientoId}/${id}`);
}