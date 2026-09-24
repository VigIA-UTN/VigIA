import axiosClient from '../../api/axiosClient';
import type { HeatLatLngTuple, HeatQuery } from './types';

export interface HeatContext {
    establecimientoId: number | null;
}

/** The backend answered 206: ml-geo is degraded, so an empty map would be misleading. */
export class HeatUnavailableError extends Error {
    constructor() {
        super('Servicio de análisis no disponible');
    }
}

/** Shape of GET /mapa-calor (GeoAnalyzeResponseDTO). */
interface MapaCalorResponse {
    mapa_calor: { lat: number; lng: number; intensidad: number }[];
}

const pad = (n: number) => String(n).padStart(2, '0');

/** Local wall-clock ISO date-time without offset, as the backend's LocalDateTime expects. */
function localDateTime(dayMs: number, endOfDay: boolean): string {
    const d = new Date(dayMs);
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${endOfDay ? '23:59:59' : '00:00:00'}`;
}

/** Query params for GET /mapa-calor, or null when the filter is not ready to fetch (nothing selected yet). */
export function heatQueryParams(filter: HeatQuery, ctx: HeatContext): Record<string, string | number> | null {
    if (ctx.establecimientoId == null) return null;
    if (filter.scope === 'rodeo' && filter.rodeoId == null) return null;
    if (filter.scope === 'animal' && filter.animalId == null) return null;
    return {
        establecimientoId: ctx.establecimientoId,
        ...(filter.scope === 'rodeo' && { rodeoId: filter.rodeoId! }),
        ...(filter.scope === 'animal' && { animalId: filter.animalId! }),
        desde: localDateTime(filter.from, false),
        hasta: localDateTime(filter.to, true),
    };
}

/** Real heatmap data: GET /mapa-calor. Empty cells are dropped; only the intensity is drawn. */
export async function fetchHeatPoints(filter: HeatQuery, ctx: HeatContext): Promise<HeatLatLngTuple[]> {
    const params = heatQueryParams(filter, ctx);
    if (!params) return [];

    const res = await axiosClient.get<MapaCalorResponse>('/mapa-calor', { params });
    if (res.status === 206) throw new HeatUnavailableError();

    return res.data.mapa_calor.filter(c => c.intensidad > 0).map(c => [c.lat, c.lng, c.intensidad]);
}
