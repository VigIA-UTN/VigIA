import { SEVERIDAD } from '../utils/severidadColores';

/** Un punto de la serie de cohesión. `valor` null = sin datos en ese intervalo. */
export interface CohesionPunto {
  ts: string;
  valor: number | null;
  promedioRodeo: number | null;
}

export interface CohesionResponse {
  /** Índice actual, 0–100. */
  actual: number;
  promedioRodeo: number;
  /** Promedio del período anterior de igual duración (para el delta). */
  promedioPeriodoAnterior: number;
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

const HORA = 3600_000;
const DIA = 24 * HORA;

/**
 * Índice de cohesión de un animal entre `desde` y `hasta` (epoch ms).
 * TODO backend: reemplazar por
 *   axiosClient.get<CohesionResponse>(`/animales/${animalId}/cohesion`, { params: { desde: iso, hasta: iso } })
 */
export async function getCohesionAnimal(animalId: number, desde: number, hasta: number): Promise<CohesionResponse> {
  return mockCohesion(animalId, desde, hasta);
}

/** Serie simulada: estable cerca del rodeo y cae hacia `actual` en el último cuarto, con un tramo sin datos. */
export function mockCohesion(animalId: number, desde: number, hasta: number): CohesionResponse {
  const span = hasta - desde;
  const paso = span <= 1.5 * DIA ? HORA : span <= 8 * DIA ? 6 * HORA : span <= 31 * DIA ? DIA : 7 * DIA;
  const n = Math.max(2, Math.floor(span / paso) + 1);
  const actual = [78, 52, 34][animalId % 3];
  let seed = animalId * 7919 + n;
  const rnd = () => (seed = (seed * 9301 + 49297) % 233280) / 233280;
  const caida = Math.floor(n * 0.75);
  const gapIni = Math.floor(n * 0.4);
  const gapFin = gapIni + Math.max(1, Math.round(n * 0.08));

  const serie = Array.from({ length: n }, (_, i): CohesionPunto => {
    let v = 76 + (rnd() - 0.5) * 10;
    if (i >= caida) {
      const t = (i - caida) / Math.max(1, n - 1 - caida);
      v = v * (1 - t) + actual * t;
    }
    if (i === n - 1) v = actual;
    const rodeo = Math.round(74 + (rnd() - 0.5) * 6);
    const sinDatos = i >= gapIni && i < gapFin && i < n - 1;
    return {
      ts: new Date(hasta - (n - 1 - i) * paso).toISOString(),
      valor: sinDatos ? null : Math.max(0, Math.min(100, Math.round(v))),
      promedioRodeo: sinDatos ? null : rodeo,
    };
  });

  const rodeos = serie.flatMap(p => (p.promedioRodeo == null ? [] : [p.promedioRodeo]));
  return {
    actual,
    promedioRodeo: Math.round(rodeos.reduce((a, b) => a + b, 0) / rodeos.length),
    promedioPeriodoAnterior: 76,
    serie,
  };
}
