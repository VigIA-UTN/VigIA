import axiosClient from './axiosClient';

export interface RodeoRequest {
    nombre: string;
    establecimientoId: number;
}

export interface RodeoLoteResponse {
    id: number;
    nombre: string;
    activo: boolean;
    fechaAlta: string;
    establecimientoId: number;
}

export async function crearRodeo(data: RodeoRequest): Promise<RodeoLoteResponse> {
    const response = await axiosClient.post<RodeoLoteResponse>('/rodeo-lotes', data);
    return response.data;
}