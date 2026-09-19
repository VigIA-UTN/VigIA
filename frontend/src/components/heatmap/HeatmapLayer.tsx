import 'leaflet'; // sets window.L, which leaflet.heat patches
import 'leaflet.heat';
import { useEffect, useRef } from 'react';
import { useMap } from 'react-leaflet';
import { HEAT_GRADIENT, type HeatLatLngTuple } from './types';

// leaflet.heat patches L globally — no named exports
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const createHeatLayer = (points: HeatLatLngTuple[], opts: object) => (window as any).L.heatLayer(points, opts);

/** Map child: renders `points` as a heat layer. Must live inside a react-leaflet MapContainer. */
export default function HeatmapLayer({ points }: { points: HeatLatLngTuple[] }) {
    const map = useMap();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const layerRef = useRef<any>(null);

    useEffect(() => {
        if (!map) return;
        if (layerRef.current) {
            map.removeLayer(layerRef.current);
        }
        layerRef.current = createHeatLayer(points, {
            radius: 28,
            blur: 20,
            maxZoom: 17,
            gradient: HEAT_GRADIENT,
        }).addTo(map);

        return () => {
            if (layerRef.current) map.removeLayer(layerRef.current);
        };
    }, [map, points]);

    return null;
}
