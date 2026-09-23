import { describe, expect, it } from 'vitest';
import { heatQueryParams } from './heatmapData';
import { dayStart } from './heatmapFilters';
import type { HeatQuery } from './types';

const range = { from: dayStart(new Date(2026, 8, 1)), to: dayStart(new Date(2026, 8, 18)) };
const q = (o: Partial<HeatQuery>): HeatQuery => ({ scope: 'todo', rodeoId: null, animalId: null, ...range, ...o });
const ctx = { establecimientoId: 10 };

describe('heatQueryParams', () => {
    it('todo: establecimiento and the full local days, no rodeo/animal', () =>
        expect(heatQueryParams(q({}), ctx)).toEqual({
            establecimientoId: 10, desde: '2026-09-01T00:00:00', hasta: '2026-09-18T23:59:59',
        }));

    it('rodeo sends rodeoId, animal sends animalId, never both', () => {
        expect(heatQueryParams(q({ scope: 'rodeo', rodeoId: 5, animalId: 7 }), ctx)).toMatchObject({ rodeoId: 5 });
        expect(heatQueryParams(q({ scope: 'rodeo', rodeoId: 5, animalId: 7 }), ctx)).not.toHaveProperty('animalId');
        expect(heatQueryParams(q({ scope: 'animal', rodeoId: 5, animalId: 7 }), ctx)).toMatchObject({ animalId: 7 });
        expect(heatQueryParams(q({ scope: 'animal', rodeoId: 5, animalId: 7 }), ctx)).not.toHaveProperty('rodeoId');
    });

    it('returns null when nothing is selected yet or there is no establecimiento', () => {
        expect(heatQueryParams(q({ scope: 'rodeo' }), ctx)).toBeNull();
        expect(heatQueryParams(q({ scope: 'animal' }), ctx)).toBeNull();
        expect(heatQueryParams(q({}), { establecimientoId: null })).toBeNull();
    });
});
