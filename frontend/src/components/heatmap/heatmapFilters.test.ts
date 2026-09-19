import { describe, expect, it } from 'vitest';
import { dayStart, filterHeatPoints, lastDaysRange } from './heatmapFilters';
import type { HeatAnimal, HeatFilter, HeatPoint } from './types';

const animals: HeatAnimal[] = [
    { id: 1, caravana: 'A1', alias: null, rodeoId: 10 },
    { id: 2, caravana: 'A2', alias: null, rodeoId: 20 },
];
const pt = (animalId: number, ts: string): HeatPoint => ({ lat: 0, lng: 0, weight: 1, ts, animalId, rodeoId: null });
const points = [pt(1, '2026-09-10T12:00:00'), pt(2, '2026-09-10T12:00:00'), pt(1, '2026-08-01T12:00:00')];
const range = { from: dayStart(new Date(2026, 8, 1)), to: dayStart(new Date(2026, 8, 18)) };
const f = (o: Partial<HeatFilter>): HeatFilter => ({ scope: 'todo', rodeoId: null, animalId: null, ...range, ...o });

describe('filterHeatPoints', () => {
    it('todo: everything inside the range', () => expect(filterHeatPoints(points, f({}), animals)).toHaveLength(2));
    it('rodeo: only its animals', () =>
        expect(filterHeatPoints(points, f({ scope: 'rodeo', rodeoId: 20 }), animals).map(p => p.animalId)).toEqual([2]));
    it('animal: only that animal; none selected => empty', () => {
        expect(filterHeatPoints(points, f({ scope: 'animal', animalId: 1 }), animals)).toHaveLength(1);
        expect(filterHeatPoints(points, f({ scope: 'animal' }), animals)).toHaveLength(0);
    });
    it('range end is inclusive', () =>
        expect(filterHeatPoints([pt(1, '2026-09-18T23:00:00')], f({}), animals)).toHaveLength(1));
});

describe('lastDaysRange', () => {
    it('7 días spans 7 calendar days including today', () => {
        const { from, to } = lastDaysRange(7, new Date(2026, 8, 18));
        expect(new Date(from).getDate()).toBe(12);
        expect(new Date(to).getDate()).toBe(18);
    });
});
