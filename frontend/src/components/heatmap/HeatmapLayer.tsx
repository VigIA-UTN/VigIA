import L from 'leaflet'; // sets window.L, which leaflet.heat patches
import 'leaflet.heat';
import { useEffect, useRef } from 'react';
import { useMap } from 'react-leaflet';
import { HEAT_GRADIENT, type HeatLatLngTuple } from './types';

// leaflet.heat patches L globally — no named exports
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const createHeatLayer = (points: HeatLatLngTuple[], opts: object) => (window as any).L.heatLayer(points, opts);

// leaflet.heat's onRemove removes its canvas from overlayPane, so hand the canvas back before removing the layer
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const removeHeatLayer = (map: L.Map, layer: any) => {
    if (layer._canvas) map.getPane('overlayPane')!.appendChild(layer._canvas);
    map.removeLayer(layer);
};

/** Map child: renders `points` as a heat layer. Must live inside a react-leaflet MapContainer. */
export default function HeatmapLayer({ points }: { points: HeatLatLngTuple[] }) {
    const map = useMap();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const layerRef = useRef<any>(null);

    useEffect(() => {
        if (!map) return;
        if (layerRef.current) {
            removeHeatLayer(map, layerRef.current);
        }
        layerRef.current = createHeatLayer(points, {
            radius: 28,
            blur: 20,
            maxZoom: 17,
            gradient: HEAT_GRADIENT,
        }).addTo(map);
        // leaflet.heat always draws into overlayPane, under the perimeter polygons: move its canvas to a pane above them
        const pane = map.getPane('heat') ?? map.createPane('heat');
        pane.style.zIndex = '450'; // overlayPane is 400, markers 600
        pane.style.pointerEvents = 'none';
        pane.appendChild(layerRef.current._canvas);

        return () => {
            if (layerRef.current) removeHeatLayer(map, layerRef.current);
            layerRef.current = null;
        };
    }, [map, points]);

    return null;
}
