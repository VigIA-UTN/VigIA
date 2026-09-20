import { filterHeatPoints } from './heatmapFilters';
import type { HeatAnimal, HeatFilter, HeatPoint } from './types';

export interface HeatContext {
    establecimientoId: number | null;
    /** map center; the mock clusters points around it */
    center: [number, number];
    animals: HeatAnimal[];
}

const DAY = 86400000;
const MOCK_DAYS = 90;
const READINGS_PER_DAY = 3;

// small deterministic PRNG so the mock is stable between renders
function mulberry32(seed: number) {
    return () => {
        seed |= 0; seed = (seed + 0x6D2B79F5) | 0;
        let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

function mockPoints({ center: [lat, lng], animals }: HeatContext): HeatPoint[] {
    const now = Date.now();
    const out: HeatPoint[] = [];
    for (const a of animals) {
        const rnd = mulberry32(a.id * 7919);
        // each rodeo grazes in its own zone (~1 km apart); animals wander around it
        const rid = a.rodeoId ?? 0;
        const zoneLat = lat + (((rid * 37) % 7) - 3) * 0.003;
        const zoneLng = lng + (((rid * 53) % 7) - 3) * 0.003;
        for (let d = 0; d < MOCK_DAYS; d++) {
            for (let r = 0; r < READINGS_PER_DAY; r++) {
                out.push({
                    lat: zoneLat + (rnd() - 0.5) * 0.004,
                    lng: zoneLng + (rnd() - 0.5) * 0.004,
                    weight: 0.4 + rnd() * 0.6,
                    ts: new Date(now - d * DAY - r * 3600000 * 6).toISOString(),
                    animalId: a.id,
                    rodeoId: a.rodeoId,
                });
            }
        }
    }
    return out;
}

/**
 * Single data seam for the heatmap. MOCK for now.
 *
 * Backend contract to swap in (not built yet):
 *   GET /animales/mapa/calor?establecimientoId&desde=YYYY-MM-DD&hasta=YYYY-MM-DD[&rodeoId=|&animalId=]
 *   -> { lat: number; lng: number; weight: number (0..1); ts: string (ISO); animalId: number; rodeoId: number | null }[]
 * Replace only this function's body; the hook and UI do not change. Scope 'todo' sends no rodeoId/animalId.
 */
export async function fetchHeatPoints(filter: HeatFilter, ctx: HeatContext): Promise<HeatPoint[]> {
    return filterHeatPoints(mockPoints(ctx), filter, ctx.animals);
}
