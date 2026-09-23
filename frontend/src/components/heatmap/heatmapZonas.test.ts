import { describe, expect, it } from 'vitest';
import { groupZonas } from './heatmapZonas';

const STEP = 0.05;
// cell (i, j) of a grid whose first cell is centred at (-35, -60)
const cell = (i: number, j: number) => ({ lat: -35 + i * STEP, lng: -60 + j * STEP });

describe('groupZonas', () => {
    it('groups edge-adjacent cells into one zone and separates distant ones', () => {
        const celdas = [cell(0, 0), cell(0, 1), cell(1, 1), cell(10, 10), cell(10, 11), cell(11, 11)];
        const zonas = groupZonas(celdas, STEP, STEP);

        expect(zonas).toHaveLength(2);
        expect(zonas.map(z => z.cells.length)).toEqual([3, 3]);
    });

    it('diagonal neighbours are not the same zone', () =>
        expect(groupZonas([cell(0, 0), cell(1, 1)], STEP, STEP, 1)).toHaveLength(2));

    it('drops zones under the minimum size', () => {
        const zonas = groupZonas([cell(0, 0), cell(0, 1), cell(5, 5)], STEP, STEP, 2);

        expect(zonas).toHaveLength(1);
        expect(zonas[0].cells).toHaveLength(2);
    });

    it('returns each cell as [[south, west], [north, east]] around its centre', () => {
        const [zona] = groupZonas([cell(0, 0)], STEP, STEP, 1);

        expect(zona.cells[0][0][0]).toBeCloseTo(-35 - STEP / 2);
        expect(zona.cells[0][0][1]).toBeCloseTo(-60 - STEP / 2);
        expect(zona.cells[0][1][0]).toBeCloseTo(-35 + STEP / 2);
        expect(zona.cells[0][1][1]).toBeCloseTo(-60 + STEP / 2);
    });

    it('empty input or invalid step gives no zones', () => {
        expect(groupZonas([], STEP, STEP)).toEqual([]);
        expect(groupZonas([cell(0, 0)], 0, STEP, 1)).toEqual([]);
    });
});
