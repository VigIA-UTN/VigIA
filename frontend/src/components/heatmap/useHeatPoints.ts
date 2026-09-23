import { useEffect, useState } from 'react';
import { fetchHeatPoints, HeatUnavailableError, type HeatContext, type HeatData } from './heatmapData';
import type { HeatFilter } from './types';

const EMPTY: HeatData = { points: [], zonas: [] };

/** Loads heat points (and under-used zones) for a filter. Results keep a stable identity until data actually changes. */
export function useHeatPoints(filter: HeatFilter, ctx: HeatContext, enabled = true) {
    const [data, setData] = useState<HeatData>(EMPTY);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const { scope, rodeoId, animalId, from, to } = filter;
    const { establecimientoId } = ctx;

    useEffect(() => {
        if (!enabled) return;
        let cancelled = false;
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setLoading(true);
        setError(null);
        fetchHeatPoints({ scope, rodeoId, animalId, from, to }, { establecimientoId })
            .then(res => { if (!cancelled) setData(res); })
            .catch(err => {
                console.error('Error al cargar mapa de calor:', err);
                if (!cancelled) {
                    setData(EMPTY);
                    setError(err instanceof HeatUnavailableError ? `${err.message}.` : 'No se pudo cargar el mapa de calor.');
                }
            })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [enabled, scope, rodeoId, animalId, from, to, establecimientoId]);

    return { points: data.points, zonas: data.zonas, loading, error };
}
