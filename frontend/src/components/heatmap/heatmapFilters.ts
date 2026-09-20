import type { HeatAnimal, HeatFilter, HeatPoint } from './types';

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
    return { scope: 'rodeo', rodeoId: firstRodeoId, animalId: null, ...thisMonthRange() };
}

/** Client-side filter by scope + inclusive day range. Backend will do this server-side later. */
export function filterHeatPoints(points: HeatPoint[], f: HeatFilter, animals: HeatAnimal[]): HeatPoint[] {
    const rodeoAnimalIds = f.scope === 'rodeo' && f.rodeoId != null
        ? new Set(animals.filter(a => a.rodeoId === f.rodeoId).map(a => a.id))
        : null;
    return points.filter(p => {
        const t = dayStart(new Date(p.ts));
        if (t < f.from || t > f.to) return false;
        if (f.scope === 'animal') return f.animalId != null && p.animalId === f.animalId;
        if (rodeoAnimalIds) return rodeoAnimalIds.has(p.animalId);
        return f.scope === 'todo';
    });
}
