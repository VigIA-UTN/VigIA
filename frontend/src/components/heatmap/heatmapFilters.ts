import type { PerimetroResponse } from '../../api/perimetrosApi';
import type { HeatFilter } from './types';

const DAY = 86400000;

export const dayStart = (d: Date): number => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();

/** Inclusive range ending today: 7 días => today and the 6 days before. */
export function lastDaysRange(days: number, today = new Date()): { from: number; to: number } {
    const to = dayStart(today);
    return { from: to - (days - 1) * DAY, to };
}

export function thisMonthRange(today = new Date()): { from: number; to: number } {
    return { from: dayStart(new Date(today.getFullYear(), today.getMonth(), 1)), to: dayStart(today) };
}

export function defaultHeatFilter(firstRodeoId: number | null): HeatFilter {
    return { scope: 'rodeo', rodeoId: firstRodeoId, animalId: null, perimetro: false, ...thisMonthRange() };
}

/** Active perimeters assigned to a rodeo (a perimeter lists the rodeos it applies to in `loteIds`). */
export function perimetrosDeRodeo(perimetros: PerimetroResponse[], rodeoId: number | null): PerimetroResponse[] {
    return rodeoId == null ? [] : perimetros.filter(p => p.activo && p.loteIds.includes(rodeoId));
}
