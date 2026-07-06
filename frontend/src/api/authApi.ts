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
