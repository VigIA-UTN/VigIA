import axiosClient from './axiosClient';
import type { AlertaResponse, VentanaInseminacionResponse } from './alertaApi';

/** Lo que el Dashboard muestra de cada animal (la cola y las ventanas llevan a su ficha). */
export interface AnimalResumen {
  id: number;
  idCaravanaOficial: string | null;
  alias: string | null;
}

export type TipoTendencia = 'CELO' | 'SALUD' | 'FUERA_DE_PERIMETRO';

export interface TendenciaResponse {
  /** Semanas ISO (`2026-W45`), la actual al final. */
  semanas: string[];
  tipos: TipoTendencia[];
  datos: Record<TipoTendencia, number[]>;
}

/** Pestaña "Centro de mando" (H0043), calculada en el backend. */
export interface CentroMandoResponse {
  alertasPendientes: { total: number; celo: number; salud: number; perimetro: number };
  ventanasAbiertas: { total: number; porVencer: number; perdidasUltimos30Dias: number };
  bajoObservacion: { total: number; enfermos: number };
  cobertura: {
    total: number;
    ok: number;
    calibrando: number;
    sinSenial: number;
    bateriaBaja: number;
    sinCaravana: number;
    porcentaje: number;
  };
  /** Ya ordenada por urgencia y recortada. Las caravanas sin señal vienen con tipo `CARAVANA` e id negativo. */
  cola: { alerta: AlertaResponse; animal: AnimalResumen | null; ventana: VentanaInseminacionResponse | null }[];
  /** Abiertas primero, cada grupo por tiempo restante. */
  ventanas: { alerta: AlertaResponse; animal: AnimalResumen | null; ventana: VentanaInseminacionResponse }[];
  embudo: {
    totalReproductivos: number;
    /** Por estado reproductivo, en el orden del embudo (`SIN_ESTADO` si no tiene). */
    distribucion: Record<string, number>;
    diasAbiertosPromedio: number;
    vacasAbiertas: number;
    inseminadasSinDiagnostico: number;
  };
  tendencia: TendenciaResponse;
  umbrales: {
    horasSinSenial: number;
    bateriaBajaPorcentaje: number;
    horasVentanaPorVencer: number;
    diasDiagnosticoInseminada: number;
    colaMax: number;
  };
}

export async function getCentroMando(establecimientoId: number, rodeoId?: number): Promise<CentroMandoResponse> {
  const params = rodeoId ? { rodeoId } : undefined;
  const response = await axiosClient.get<CentroMandoResponse>(
    `/establecimientos/${establecimientoId}/dashboard/centro-mando`,
    { params },
  );
  return response.data;
}
