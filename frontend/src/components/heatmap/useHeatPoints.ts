import { useEffect, useState } from 'react';
import { fetchHeatPoints, type HeatContext } from './heatmapData';
import type { HeatFilter, HeatLatLngTuple } from './types';

/** Loads heat points for a filter. `points` keeps a stable identity until data actually changes. */
export function useHeatPoints(filter: HeatFilter, ctx: HeatContext, enabled = true) {
    const [points, setPoints] = useState<HeatLatLngTuple[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const { scope, rodeoId, animalId, from, to } = filter;
    const { establecimientoId, animals } = ctx;
    const [lat, lng] = ctx.center;

    useEffect(() => {
        if (!enabled) return;
        let cancelled = false;
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setLoading(true);
        setError(null);
        fetchHeatPoints({ scope, rodeoId, animalId, from, to }, { establecimientoId, center: [lat, lng], animals })
            .then(data => { if (!cancelled) setPoints(data.map(p => [p.lat, p.lng, p.weight])); })
            .catch(err => {
                console.error('Error al cargar mapa de calor:', err);
                if (!cancelled) { setPoints([]); setError('No se pudo cargar el mapa de calor.'); }
            })
            .finally(() => { if (!cancelled) setLoading(false); });
        return () => { cancelled = true; };
    }, [enabled, scope, rodeoId, animalId, from, to, establecimientoId, lat, lng, animals]);

    return { points, loading, error };
}
