import { useEffect, useState } from 'react';
import { fetchHeatPoints, HeatUnavailableError, type HeatContext } from './heatmapData';
import type { HeatFilter, HeatLatLngTuple } from './types';

const EMPTY: HeatLatLngTuple[] = [];

/** Loads heat points for a filter. `points` keeps a stable identity until data actually changes. */
export function useHeatPoints(filter: HeatFilter, ctx: HeatContext, enabled = true) {
    const [points, setPoints] = useState<HeatLatLngTuple[]>(EMPTY);
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
            .then(res => { if (!cancelled) setPoints(res); })
            .catch(err => {
                console.error('Error al cargar mapa de calor:', err);
                if (!cancelled) {
                    setPoints(EMPTY);
                    setError(err instanceof HeatUnavailableError ? `${err.message}.` : 'No se pudo cargar el mapa de calor.');
                }
            })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [enabled, scope, rodeoId, animalId, from, to, establecimientoId]);

    return { points, loading, error };
}
