import axiosClient from './axiosClient';

export interface AnimalResponse {
    id: number;
    idCaravanaOficial: string;
    alias: string;
    fechaNacimiento: string;
    raza: string;
    estadoActual: string;
    rodeoLoteId: number;
    rodeoLoteNombre: string;
    establecimientoId: number;
    fechaAlta: string;
    estadoReproductivo: string;
    fechaUltimoParto: string;
    fechaUltimaInseminacion: string;
}

export async function getAnimales(): Promise<AnimalResponse[]> {
    const response = await axiosClient.get<AnimalResponse[]>('/animales');
    return response.data;
}

export async function removerAnimal(id: number): Promise<void> {
    await axiosClient.delete(`/animales/${id}`);
}