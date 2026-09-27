import { describe, expect, it } from 'vitest';
import type { PerimetroResponse } from '../../api/perimetrosApi';
import { lastDaysRange, perimetrosDeRodeo } from './heatmapFilters';

const peri = (id: number, activo: boolean, loteIds: number[]) => ({ id, activo, loteIds }) as PerimetroResponse;

describe('perimetrosDeRodeo', () => {
    const all = [peri(1, true, [10, 20]), peri(2, false, [10]), peri(3, true, [20]), peri(4, true, [10])];

    it('returns the active perimeters that list the rodeo, several if there are several', () =>
        expect(perimetrosDeRodeo(all, 10).map(p => p.id)).toEqual([1, 4]));

    it('ignores inactive perimeters and other rodeos', () => {
        expect(perimetrosDeRodeo(all, 20).map(p => p.id)).toEqual([1, 3]);
        expect(perimetrosDeRodeo(all, 99)).toEqual([]);
    });

    it('no rodeo selected => none', () => expect(perimetrosDeRodeo(all, null)).toEqual([]));
});

describe('lastDaysRange', () => {
    it('7 días spans 7 calendar days including today', () => {
        const { from, to } = lastDaysRange(7, new Date(2026, 8, 18));
        expect(new Date(from).getDate()).toBe(12);
        expect(new Date(to).getDate()).toBe(18);
    });
});
