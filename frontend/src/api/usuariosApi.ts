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

export async function getEmpleados(establecimientoId?: number): Promise<UsuarioResponse[]> {
  const url = establecimientoId ? `/usuarios/empleados?establecimientoId=${establecimientoId}` : '/usuarios/empleados';
  const response = await axiosClient.get<UsuarioResponse[]>(url);
  return response.data;
}

export async function removerEmpleado(id: number, establecimientoId: number): Promise<void> {
  await axiosClient.delete(`/usuarios/empleados/${establecimientoId}/${id}`);
}