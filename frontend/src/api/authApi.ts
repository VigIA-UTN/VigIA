import axiosClient from './axiosClient';

export interface AuthRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  email: string;
  rol: string;
}

export async function loginRequest(email: string, password: string): Promise<AuthResponse> {
  const response = await axiosClient.post<AuthResponse>('/auth/login', { email, password });
  return response.data;
}

export async function registerRequest(nombre: string, apellido: string, email: string, password: string, token: string): Promise<AuthResponse> {
  const response = await axiosClient.post<AuthResponse>('/auth/register', { nombre, apellido, email, password, token });
  return response.data;
}
