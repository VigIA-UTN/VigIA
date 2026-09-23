import type { CellBounds, ZonaSubutilizada } from './types';

/** Zones smaller than this many cells are noise (a couple of stray empty cells), not "areas". */
export const MIN_ZONE_CELLS = 3;

const NEIGHBOURS = [[1, 0], [-1, 0], [0, 1], [0, -1]];

/**
 * Groups under-used grid cells into zones of edge-adjacent cells (flood fill) and returns each cell as bounds.
 * `celdas` are cell centres on a regular grid of `latStep` x `lngStep` degrees.
 */
export function groupZonas(
    celdas: { lat: number; lng: number }[],
    latStep: number,
    lngStep: number,
    minCells = MIN_ZONE_CELLS,
): ZonaSubutilizada[] {
    if (celdas.length === 0 || !(latStep > 0) || !(lngStep > 0)) return [];

    // grid position relative to the first cell; rounding absorbs float error in the centres
    const { lat: lat0, lng: lng0 } = celdas[0];
    const key = (i: number, j: number) => `${i}:${j}`;
    const all = new Map(celdas.map(c => [
        key(Math.round((c.lat - lat0) / latStep), Math.round((c.lng - lng0) / lngStep)), c,
    ]));
    const pending = new Set(all.keys());

    const groups: CellBounds[][] = [];
    while (pending.size > 0) {
        const start = pending.values().next().value!;
        pending.delete(start);
        const stack = [start];
        const cells: CellBounds[] = [];
        while (stack.length > 0) {
            const k = stack.pop()!;
            const { lat, lng } = all.get(k)!;
            cells.push([[lat - latStep / 2, lng - lngStep / 2], [lat + latStep / 2, lng + lngStep / 2]]);
            const [i, j] = k.split(':').map(Number);
            for (const [di, dj] of NEIGHBOURS) {
                const next = key(i + di, j + dj);
                if (pending.delete(next)) stack.push(next);
            }
        }
        groups.push(cells);
    }
    return groups.filter(g => g.length >= minCells).map((cells, id) => ({ id, cells }));
}
