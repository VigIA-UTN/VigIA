import axiosClient from './axiosClient';

export interface UsuarioResponse {
  id: number;
  nombre: string;
  apellido: string;
  email: string;
  rol: string;
  activo: boolean;
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

export async function toggleEstadoEmpleado(id: number): Promise<void> {
  const response = await axiosClient.patch(`/usuarios/empleados/${id}/estado`);
  return response.data;
}