import { SEVERIDAD } from '../utils/severidadColores';
import axiosClient from './axiosClient';

/** Un punto de la serie de cohesión (una hora o un día). `valor` null = sin datos en ese intervalo. */
export interface CohesionPunto {
  /** Inicio del intervalo, ISO con offset. */
  ts: string;
  valor: number | null;
  promedioRodeo: number | null;
}

export interface CohesionResponse {
  /** Índice actual, 0–100; null si no hubo análisis en el período. */
  actual: number | null;
  promedioRodeo: number | null;
  /** Promedio del período anterior de igual duración (para el delta); null sin datos. */
  promedioPeriodoAnterior: number | null;
  serie: CohesionPunto[];
}

// ponytail: umbrales fijos a confirmar con ML; pasarlos al response si pasan a ser configurables por establecimiento.
export const UMBRAL_NORMAL = 60;
export const UMBRAL_CRITICO = 40;

export type ClaveCohesion = 'NORMAL' | 'BORDE' | 'CRITICO';

const ESTADOS: Record<ClaveCohesion, { label: string; color: string; explicacion: string }> = {
  NORMAL: { label: 'Cohesionado', color: SEVERIDAD.BAJA.bar, explicacion: 'Pasa el tiempo esperado cerca del rodeo.' },
  BORDE: { label: 'Al límite', color: SEVERIDAD.MEDIA.bar, explicacion: 'Se estima que está pasando menos tiempo cerca del rodeo que lo habitual.' },
  CRITICO: { label: 'Alejado', color: SEVERIDAD.ALTA.bar, explicacion: 'Se estima que se está separando del rodeo. Podría indicar un problema sanitario o un parto próximo.' },
};

/** Interpretación del índice: NORMAL ≥ 60 · BORDE 40–59 · CRITICO < 40. */
export function estadoCohesion(valor: number) {
  const clave: ClaveCohesion = valor >= UMBRAL_NORMAL ? 'NORMAL' : valor >= UMBRAL_CRITICO ? 'BORDE' : 'CRITICO';
  return { clave, ...ESTADOS[clave] };
}

/** Índice de cohesión de un animal entre `desde` y `hasta` (epoch ms). */
export async function getCohesionAnimal(animalId: number, desde: number, hasta: number): Promise<CohesionResponse> {
  const response = await axiosClient.get<CohesionResponse>(`/animales/${animalId}/cohesion`, {
    params: {
      desde: new Date(desde).toISOString(),
      hasta: new Date(hasta).toISOString(),
    },
  });
  return response.data;
}
